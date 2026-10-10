package me.trae.foundation.database.storage.redis;

import io.lettuce.core.RedisException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.entity.RevisionedEntity;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.storage.codec.EntityCodec;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.redis.script.RedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedisStorage<E extends Entity> implements Storage<UUID, E> {

    private static final String REVISION_FIELD = "__foundation_revision";

    private static final String WRITE_MISSING_SCRIPT_SOURCE = "if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3]) return 1";
    private static final String RELEASE_LOCK_SCRIPT = "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end";

    private static final long FILL_LOCK_MILLIS = 30_000L;
    private static final long FILL_WAIT_MILLIS = 35_000L;
    private static final long NEGATIVE_CACHE_MILLIS = 1_000L;
    private static final long FAILURE_CACHE_MILLIS = 500L;

    private static final RedisScript WRITE_MISSING_SCRIPT = new RedisScript(WRITE_MISSING_SCRIPT_SOURCE);

    private final RedisDriver redisDriver;

    @Getter
    private final RedisNamespace redisNamespace;

    private final EntityCodec<E> entityCodec;
    private final RedisHashWriter redisHashWriter;
    private final boolean revisioned;

    public RedisStorage(final RedisDriver redisDriver, final RedisNamespace redisNamespace, final Class<E> entityType, final Duration expiry) {
        this(redisDriver, redisNamespace, entityType, expiry, false);
    }

    public RedisStorage(final RedisDriver redisDriver, final RedisNamespace redisNamespace, final Class<E> entityType, final Duration expiry, final boolean revisioned) {
        this(redisDriver, redisNamespace, new EntityCodec<>(entityType), new RedisHashWriter(redisDriver, expiry), revisioned);
    }

    public RedisStorage(final RedisDriver redisDriver, final RedisNamespace redisNamespace, final Class<E> entityType) {
        this(redisDriver, redisNamespace, entityType, null);
    }

    @Override
    public void put(final UUID id, final E entity) {
        this.redisHashWriter.write(this.redisNamespace.getKey(id), this.encode(entity));
    }

    @Override
    public void remove(final UUID id) {
        this.redisDriver.getCommands().del(this.redisNamespace.getKey(id));
    }

    public void invalidate(final UUID id) {
        this.createInvalidation(id).dispatch(this.redisDriver);
    }

    public RedisInvalidation createInvalidation(final UUID id) {
        return this.redisNamespace.getInvalidation(id, this.redisDriver.getInstanceId());
    }

    public Optional<E> getOrLoad(final UUID id, final Supplier<Optional<E>> loader) {
        final Optional<E> cached = this.get(id);

        if (cached.isPresent() || this.isMissing(id)) {
            return cached;
        }

        final String lockKey = this.getLockKey(id);
        final String owner = UUID.randomUUID().toString();

        final long deadline = System.nanoTime() + Duration.ofMillis(FILL_WAIT_MILLIS).toNanos();

        while (System.nanoTime() < deadline) {
            if (this.acquireLockIfAvailable(lockKey, owner)) {
                try {
                    final Optional<E> latest = this.get(id);

                    if (latest.isPresent() || this.isMissing(id)) {
                        return latest;
                    }

                    final Optional<E> loaded;
                    try {
                        loaded = loader.get();
                    } catch (final RuntimeException exception) {
                        this.writeFailureMarker(id, lockKey, owner, exception);
                        throw exception;
                    }

                    if (loaded.isPresent()) {
                        if (!this.redisHashWriter.writeIfLockOwner(this.redisNamespace.getKey(id), lockKey, owner, this.encode(loaded.get()))) {
                            return Optional.of(this.get(id).orElseThrow(() -> new IllegalStateException("Redis cache fill lock expired while loading %s".formatted(id))));
                        }
                    } else if (!this.writeMissingIfLockOwner(id, lockKey, owner)) {
                        throw new IllegalStateException("Redis cache fill lock expired while loading %s".formatted(id));
                    }

                    return loaded;
                } finally {
                    this.releaseLock(lockKey, owner);
                }
            }

            final Optional<E> latest = this.get(id);

            if (latest.isPresent() || this.isMissing(id)) {
                return latest;
            }

            this.pauseBeforeRetry(deadline);
        }

        throw new IllegalStateException("Timed out waiting for the Redis cache fill lock for %s".formatted(id));
    }

    public Map<UUID, E> getOrLoadAll(final Collection<UUID> ids, final Function<Collection<UUID>, ? extends Collection<E>> loader) {
        final List<UUID> idList = ids.stream().distinct().sorted(Comparator.naturalOrder()).toList();
        final Map<UUID, E> entityMap = new LinkedHashMap<>();
        final Map<UUID, String> lockOwnerMap = new LinkedHashMap<>();

        final long deadline = System.nanoTime() + Duration.ofMillis(FILL_WAIT_MILLIS).toNanos();

        try {
            for (final UUID id : idList) {
                final Optional<E> cached = this.get(id);

                if (cached.isPresent()) {
                    entityMap.put(id, cached.get());
                    continue;
                }

                if (this.isMissing(id)) {
                    continue;
                }

                final String owner = UUID.randomUUID().toString();
                final String lockKey = this.getLockKey(id);

                boolean acquired = false;
                while (!acquired) {
                    acquired = this.acquireLockIfAvailable(lockKey, owner);
                    if (acquired) {
                        break;
                    }

                    final Optional<E> current = this.get(id);
                    if (current.isPresent()) {
                        entityMap.put(id, current.get());
                        break;
                    }

                    if (this.isMissing(id)) {
                        break;
                    }

                    if (System.nanoTime() >= deadline) {
                        throw new IllegalStateException("Timed out waiting for the Redis cache fill lock for %s".formatted(id));
                    }

                    this.pauseBeforeRetry(deadline);
                }

                if (acquired) {
                    lockOwnerMap.put(id, owner);
                }
            }

            final List<UUID> loadIdList = new ArrayList<>();
            for (final UUID id : lockOwnerMap.keySet()) {
                final Optional<E> current = this.get(id);

                if (current.isPresent()) {
                    entityMap.put(id, current.get());
                } else if (!this.isMissing(id)) {
                    loadIdList.add(id);
                }
            }

            if (!loadIdList.isEmpty()) {
                final Collection<E> loadedEntities;
                try {
                    loadedEntities = loader.apply(loadIdList);
                } catch (final RuntimeException exception) {
                    for (final UUID id : loadIdList) {
                        this.writeFailureMarker(id, this.getLockKey(id), lockOwnerMap.get(id), exception);
                    }

                    throw exception;
                }

                final Set<UUID> loadedIdSet = new LinkedHashSet<>();

                for (final E entity : loadedEntities) {
                    final String owner = lockOwnerMap.get(entity.getId());

                    if (owner == null || !this.redisHashWriter.writeIfLockOwner(this.redisNamespace.getKey(entity.getId()), this.getLockKey(entity.getId()), owner, this.encode(entity))) {
                        final E cached = this.get(entity.getId()).orElseThrow(() -> new IllegalStateException("Redis cache fill lock expired while loading %s".formatted(entity.getId())));
                        entityMap.put(entity.getId(), cached);
                    } else {
                        entityMap.put(entity.getId(), entity);
                    }
                    loadedIdSet.add(entity.getId());
                }

                for (final UUID id : loadIdList) {
                    if (!loadedIdSet.contains(id)) {
                        final String owner = lockOwnerMap.get(id);
                        if (owner != null && !this.writeMissingIfLockOwner(id, this.getLockKey(id), owner)) {
                            throw new IllegalStateException("Redis cache fill lock expired while loading %s".formatted(id));
                        }
                    }
                }
            }

            return entityMap;
        } finally {
            lockOwnerMap.forEach(this::releaseLock);
        }
    }

    @Override
    public Optional<E> get(final UUID id) {
        return this.decode(id, this.redisDriver.getCommands().hgetall(this.redisNamespace.getKey(id)));
    }

    @Override
    public Map<UUID, E> getAll(final Collection<UUID> ids) {
        final Map<UUID, RedisFuture<Map<String, String>>> futureMap = new LinkedHashMap<>();

        for (final UUID id : ids) {
            futureMap.put(id, this.redisDriver.getAsyncCommands().hgetall(this.redisNamespace.getKey(id)));
        }

        final Map<UUID, E> entityMap = new LinkedHashMap<>();

        for (final Map.Entry<UUID, RedisFuture<Map<String, String>>> entry : futureMap.entrySet()) {
            final Optional<E> entity = this.decode(entry.getKey(), this.await(entry.getValue()));

            if (entity.isPresent()) {
                entityMap.put(entry.getKey(), entity.get());
            }
        }

        return entityMap;
    }

    @Override
    public boolean contains(final UUID id) {
        return this.redisDriver.getCommands().exists(this.redisNamespace.getKey(id)) > 0;
    }

    public void putProperties(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties) {
        if (entityProperties.isEmpty()) {
            return;
        }

        this.redisHashWriter.write(this.redisNamespace.getKey(entity.getId()), this.encode(entity, entityProperties));
    }

    public long increment(final UUID id, final EntityProperty<?, ? extends Number> entityProperty, final long delta) {
        if (entityProperty.getValueConverter().isPresent()) {
            throw new SchemaException("Property %s cannot be incremented because it uses a converter".formatted(entityProperty.getName()));
        }

        if (entityProperty.getValueType() != Long.class && entityProperty.getValueType() != Integer.class) {
            throw new SchemaException("Property %s must be a Long or Integer to be incremented".formatted(entityProperty.getName()));
        }

        return this.redisHashWriter.increment(this.redisNamespace.getKey(id), entityProperty.getName(), delta);
    }

    public RedisStorage<E> forTenant(final String tenantId) {
        return new RedisStorage<>(this.redisDriver, this.redisNamespace.withTenant(tenantId), this.entityCodec, this.redisHashWriter, this.revisioned);
    }

    private Optional<E> decode(final UUID id, final Map<String, String> fieldMap) {
        if (fieldMap == null || fieldMap.isEmpty()) {
            return Optional.empty();
        }

        try {
            final E entity = this.entityCodec.decode(id, fieldMap);

            if (this.revisioned && entity instanceof RevisionedEntity revisionedEntity && fieldMap.containsKey(REVISION_FIELD)) {
                revisionedEntity.setRevision(Long.parseLong(fieldMap.get(REVISION_FIELD)));
            }

            return Optional.of(entity);
        } catch (final RuntimeException exception) {
            this.remove(id);
            return Optional.empty();
        }
    }

    private Map<String, String> encode(final E entity) {
        return this.encode(entity, this.entityCodec.getProperties());
    }

    private Map<String, String> encode(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties) {
        final Map<String, String> fieldMap = this.entityCodec.encode(entity, entityProperties);

        if (this.revisioned && entity instanceof RevisionedEntity revisionedEntity) {
            fieldMap.put(REVISION_FIELD, Long.toString(revisionedEntity.getRevision()));
        }

        return fieldMap;
    }

    private boolean acquireLockIfAvailable(final String lockKey, final String owner) {
        return "OK".equals(this.redisDriver.getCommands().set(lockKey, owner, SetArgs.Builder.nx().px(FILL_LOCK_MILLIS)));
    }

    private void releaseLock(final String lockKey, final String owner) {
        this.redisDriver.getCommands().eval(RELEASE_LOCK_SCRIPT, ScriptOutputType.INTEGER, new String[]{lockKey}, owner);
    }

    private void releaseLock(final UUID id, final String owner) {
        this.releaseLock(this.getLockKey(id), owner);
    }

    private boolean isMissing(final UUID id) {
        final String marker = this.redisDriver.getCommands().get(this.getMissingKey(id));

        if ("failed".equals(marker)) {
            throw new IllegalStateException("A concurrent database cache fill failed for %s".formatted(id));
        }

        return marker != null;
    }

    private boolean writeMissingIfLockOwner(final UUID id, final String lockKey, final String owner) {
        return this.writeMarkerIfLockOwner(id, lockKey, owner, "missing", NEGATIVE_CACHE_MILLIS);
    }

    private boolean writeMarkerIfLockOwner(final UUID id, final String lockKey, final String owner, final String marker, final long expiryMillis) {
        return WRITE_MISSING_SCRIPT.<Long>execute(
                this.redisDriver,
                ScriptOutputType.INTEGER,
                new String[]{lockKey, this.getMissingKey(id)},
                owner,
                marker,
                String.valueOf(expiryMillis)
        ) > 0L;
    }

    private void writeFailureMarker(final UUID id, final String lockKey, final String owner, final RuntimeException exception) {
        try {
            this.writeMarkerIfLockOwner(id, lockKey, owner, "failed", FAILURE_CACHE_MILLIS);
        } catch (final RuntimeException markerException) {
            exception.addSuppressed(markerException);
        }
    }

    private String getLockKey(final UUID id) {
        return this.redisNamespace.getKey("cache:fill:%s".formatted(id));
    }

    private String getMissingKey(final UUID id) {
        return this.redisNamespace.getKey("cache:missing:%s".formatted(id));
    }

    private void pauseBeforeRetry(final long deadline) {
        final long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0L) {
            return;
        }

        try {
            Thread.sleep(Math.min(25L, Math.max(1L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remainingNanos))));
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a Redis cache fill", exception);
        }
    }

    private Map<String, String> await(final RedisFuture<Map<String, String>> future) {
        try {
            return future.get();
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a Redis bulk lookup", exception);
        } catch (final ExecutionException exception) {
            if (exception.getCause() instanceof final RedisException redisException) {
                throw redisException;
            }

            throw new IllegalStateException("Redis bulk lookup failed", exception.getCause());
        }
    }
}