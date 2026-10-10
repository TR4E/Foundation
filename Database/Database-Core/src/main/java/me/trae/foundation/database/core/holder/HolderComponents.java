package me.trae.foundation.database.core.holder;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.holder.InstanceMode;
import me.trae.foundation.database.core.batch.BatchQueue;
import me.trae.foundation.database.core.holder.change.ChangeTracker;
import me.trae.foundation.database.core.holder.claim.UniqueClaimer;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.local.LocalStorage;
import me.trae.foundation.database.storage.redis.RedisNamespace;
import me.trae.foundation.database.storage.redis.RedisStorage;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class HolderComponents<E extends Entity> {

    private final LocalStorage<UUID, E> localStorage;
    private final ChangeTracker<E> changeTracker;
    private final HolderLookups<E> holderLookups;
    private final HolderWriter<E> holderWriter;

    public static <E extends Entity> HolderComponents<E> create(final AbstractEntityRepository<E> repository, final InstanceMode instanceMode, final Duration localExpiry, final Duration redisExpiry) {
        return create(repository, instanceMode, localExpiry, redisExpiry, LocalStorage.DEFAULT_MAXIMUM_AGE);
    }

    public static <E extends Entity> HolderComponents<E> create(final AbstractEntityRepository<E> repository, final InstanceMode instanceMode, final Duration localExpiry, final Duration redisExpiry, final Duration localMaximumAge) {
        if (instanceMode == InstanceMode.MULTI_INSTANCE && (localMaximumAge == null || localMaximumAge.isZero() || localMaximumAge.isNegative())) {
            throw new IllegalArgumentException("MULTI_INSTANCE localMaximumAge must be greater than zero");
        }

        final ChangeTracker<E> changeTracker = new ChangeTracker<>(repository.getEntityType(), localExpiry);

        final RedisDriver redisDriver = repository.getCoreDatabase().getRedisDriver().orElse(null);
        final boolean localEnabled = instanceMode == InstanceMode.SINGLETON || redisDriver != null;
        final Duration maximumAge = instanceMode == InstanceMode.MULTI_INSTANCE ? localMaximumAge : null;
        final LocalStorage<UUID, E> localStorage = localEnabled ? new LocalStorage<>(localExpiry, instanceMode == InstanceMode.MULTI_INSTANCE, maximumAge) : null;

        final RedisStorage<E> redisStorage = redisDriver == null ? null : new RedisStorage<>(redisDriver, RedisNamespace.of(repository.getTable(), repository.getTenantId().orElse(null)), repository.getEntityType(), redisExpiry, repository.getTableSchema().isOptimisticLocking());

        final HolderLookups<E> holderLookups = new HolderLookups<>(repository, localStorage, redisStorage, redisDriver);
        final HolderWriter<E> holderWriter = new HolderWriter<>(repository, localStorage, redisStorage, changeTracker, new UniqueClaimer<>(repository, holderLookups, redisDriver));

        if (instanceMode == InstanceMode.MULTI_INSTANCE && localStorage != null) {
            final String channel = redisStorage.getRedisNamespace().getInvalidationChannel();
            redisDriver.subscribe(channel, message -> invalidateLocal(localStorage, changeTracker, holderWriter, repository.getCoreDatabase().getBatchQueue(), message));
        }

        return new HolderComponents<>(localStorage, changeTracker, holderLookups, holderWriter);
    }

    private static <E extends Entity> void invalidateLocal(final LocalStorage<UUID, E> localStorage, final ChangeTracker<E> changeTracker, final HolderWriter<E> holderWriter, final BatchQueue batchQueue, final String message) {
        if (message.equals("*")) {
            for (final E entity : localStorage.getValues()) {
                if (flushSafely(entity, changeTracker, holderWriter, batchQueue)) {
                    localStorage.remove(entity.getId());
                }
            }

            return;
        }

        final String[] parts = message.split("\\|", 2);

        if (parts.length != 2) {
            return;
        }

        try {
            final UUID id = UUID.fromString(parts[1]);
            final Optional<E> entity = localStorage.get(id);
            final boolean flushed = entity.isEmpty() || flushSafely(entity.get(), changeTracker, holderWriter, batchQueue);

            if (flushed) {
                localStorage.remove(id);
                changeTracker.forget(id);
            }
        } catch (final IllegalArgumentException ignored) {
        }
    }

    private static <E extends Entity> boolean flushSafely(final E entity, final ChangeTracker<E> changeTracker, final HolderWriter<E> holderWriter, final BatchQueue batchQueue) {
        try {
            if (changeTracker.isTracked(entity.getId())) {
                holderWriter.writeChanged(entity);
            }
            changeTracker.forget(entity.getId());
            return true;
        } catch (final RuntimeException ignored) {
            batchQueue.reportFailure(new DatabaseException("Failed to flush local changes before invalidating %s".formatted(entity.getId()), ignored));
            return false;
        }
    }
}