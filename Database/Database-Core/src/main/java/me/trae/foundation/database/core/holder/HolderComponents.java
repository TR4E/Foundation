package me.trae.foundation.database.core.holder;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.holder.InstanceMode;
import me.trae.foundation.database.api.holder.RedisMode;
import me.trae.foundation.database.core.batch.BatchQueue;
import me.trae.foundation.database.core.holder.change.ChangeTracker;
import me.trae.foundation.database.core.holder.claim.UniqueClaimer;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.local.LocalStorage;
import me.trae.foundation.database.storage.redis.RedisNamespace;
import me.trae.foundation.database.storage.redis.RedisStorage;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class HolderComponents<E extends Entity> {

    private final LocalStorage<UUID, E> localStorage;
    private final LocalEntityIndex<E> localEntityIndex;
    private final ChangeTracker<E> changeTracker;
    private final HolderLookups<E> holderLookups;
    private final HolderWriter<E> holderWriter;

    public static <E extends Entity> HolderComponents<E> create(final AbstractEntityRepository<E> repository, final InstanceMode instanceMode, final RedisMode redisMode, final Duration localExpiry, final Duration redisExpiry, final Duration localMaximumAge) {
        Objects.requireNonNull(redisMode, "redisMode");

        if (instanceMode == InstanceMode.MULTI_INSTANCE && (localMaximumAge == null || localMaximumAge.isZero() || localMaximumAge.isNegative())) {
            throw new IllegalArgumentException("MULTI_INSTANCE localMaximumAge must be greater than zero");
        }

        if (instanceMode == InstanceMode.MULTI_INSTANCE && redisMode != RedisMode.ENABLED) {
            throw new IllegalArgumentException("MULTI_INSTANCE holders require RedisMode.ENABLED");
        }

        final RedisDriver redisDriver = redisMode == RedisMode.ENABLED ? repository.getCoreDatabase().getRedisDriver().orElse(null) : null;

        if (instanceMode == InstanceMode.MULTI_INSTANCE && redisDriver == null) {
            throw new IllegalArgumentException("MULTI_INSTANCE holders require a Redis driver on their CoreDatabase");
        }

        final ChangeTracker<E> changeTracker = new ChangeTracker<>(repository.getEntityType(), localExpiry);

        final Duration maximumAge = instanceMode == InstanceMode.MULTI_INSTANCE ? localMaximumAge : null;

        final LocalStorage<UUID, E> localStorage = new LocalStorage<>(localExpiry, instanceMode == InstanceMode.MULTI_INSTANCE, maximumAge);

        final LocalEntityIndex<E> localEntityIndex = new LocalEntityIndex<>(repository.getEntityIndexes());

        final RedisStorage<E> redisStorage = redisDriver == null ? null : new RedisStorage<>(redisDriver, RedisNamespace.of(repository.getTable(), repository.getTenantId().orElse(null)), repository.getEntityType(), redisExpiry, repository.getTableSchema().isOptimisticLocking(), repository.getCoreDatabase().getEntityRevisionStore());

        final HolderLookups<E> holderLookups = new HolderLookups<>(repository, localStorage, localEntityIndex, redisStorage, redisDriver, redisExpiry);

        final HolderWriter<E> holderWriter = new HolderWriter<>(repository, localStorage, localEntityIndex, redisStorage, changeTracker, new UniqueClaimer<>(repository, holderLookups, redisDriver), redisMode == RedisMode.ENABLED);

        if (instanceMode == InstanceMode.MULTI_INSTANCE) {
            final String channel = redisStorage.getRedisNamespace().getInvalidationChannel();

            redisDriver.subscribe(channel, message -> invalidateLocal(localStorage, holderLookups, holderWriter, repository.getCoreDatabase().getBatchQueue(), message));
        }

        return new HolderComponents<>(localStorage, localEntityIndex, changeTracker, holderLookups, holderWriter);
    }

    public static <E extends Entity> HolderComponents<E> create(final AbstractEntityRepository<E> repository, final InstanceMode instanceMode, final Duration localExpiry, final Duration redisExpiry, final Duration localMaximumAge) {
        return create(repository, instanceMode, RedisMode.ENABLED, localExpiry, redisExpiry, localMaximumAge);
    }

    public static <E extends Entity> HolderComponents<E> create(final AbstractEntityRepository<E> repository, final InstanceMode instanceMode, final Duration localExpiry, final Duration redisExpiry) {
        return create(repository, instanceMode, RedisMode.ENABLED, localExpiry, redisExpiry, LocalStorage.DEFAULT_MAXIMUM_AGE);
    }

    private static <EntityType extends Entity> void invalidateLocal(final LocalStorage<UUID, EntityType> localStorage, final HolderLookups<EntityType> holderLookups, final HolderWriter<EntityType> holderWriter, final BatchQueue batchQueue, final String message) {
        if (message.equals("*")) {
            for (final EntityType entity : localStorage.getValues()) {
                refreshSafely(entity.getId(), holderLookups, holderWriter, batchQueue);
            }

            return;
        }

        final String[] parts = message.split("\\|", 2);

        if (parts.length != 2 || holderWriter.getSourceId().equals(parts[0])) {
            return;
        }

        try {
            refreshSafely(UUID.fromString(parts[1]), holderLookups, holderWriter, batchQueue);
        } catch (final IllegalArgumentException ignored) {
        }
    }

    private static <EntityType extends Entity> void refreshSafely(final UUID id, final HolderLookups<EntityType> holderLookups, final HolderWriter<EntityType> holderWriter, final BatchQueue batchQueue) {
        try {
            holderWriter.refreshLocal(id, holderLookups);
        } catch (final RuntimeException exception) {
            batchQueue.reportFailure(new DatabaseException("Failed to refresh invalidated local entity %s".formatted(id), exception));
        }
    }
}