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
import me.trae.foundation.database.storage.redis.RedisEntityCoordinator;
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
            final RedisEntityCoordinator redisEntityCoordinator = redisDriver.getEntityCoordinator();
            if (redisEntityCoordinator != null) {
                final String namespace = redisStorage.getRedisNamespace().getKey("");
                final Runnable bindingRunnable = () -> redisEntityCoordinator.bind(
                        namespace,
                        holderWriter.getSourceId(),
                        id -> holderWriter.refreshLocalStrict(id, holderLookups),
                        () -> localStorage.getValues().forEach(entity -> holderWriter.refreshLocalStrict(entity.getId(), holderLookups)),
                        exception -> repository.getCoreDatabase().getBatchQueue().reportFailure(new DatabaseException("Entity owner synchronization failed", exception))
                );
                bindingRunnable.run();
                localStorage.setReadGuard(redisEntityCoordinator::checkLease);
                localStorage.setRemovalListener(id -> redisEntityCoordinator.remove(namespace, id, holderWriter.getSourceId()));
                localEntityIndex.setAdmissionFunction(entity -> {
                    final RedisEntityCoordinator.Admission admission = redisEntityCoordinator.admit(namespace, entity.getId(), holderWriter.getSourceId());
                    if (admission.getReadyFuture().isDone()) {
                        admission.getReadyFuture().join();
                        return () -> {};
                    }

                    if (admission.getAdmissionThreadId() != Thread.currentThread().threadId()) {
                        admission.getReadyFuture().join();
                        return () -> {};
                    }

                    try {
                        while (true) {
                            final long generation = redisEntityCoordinator.generation(namespace, entity.getId());
                            holderWriter.prepareAdmission(entity, holderLookups);
                            final long currentGeneration = redisEntityCoordinator.generation(namespace, entity.getId());
                            if (currentGeneration == generation) {
                                break;
                            }
                        }

                        return () -> admission.getReadyFuture().complete(null);
                    } catch (final RuntimeException | Error exception) {
                        admission.getReadyFuture().completeExceptionally(exception);
                        try {
                            redisEntityCoordinator.remove(namespace, entity.getId(), holderWriter.getSourceId());
                        } catch (final RuntimeException | Error cleanupException) {
                            exception.addSuppressed(cleanupException);
                        }
                        throw exception;
                    }
                });
                repository.getCoreDatabase().getBatchQueue().addStopTask(() -> redisEntityCoordinator.unbind(holderWriter.getSourceId()));
                repository.getCoreDatabase().getBatchQueue().addStartTask(() -> {
                    bindingRunnable.run();
                    localStorage.getValues().forEach(entity -> holderWriter.refreshLocalStrict(entity.getId(), holderLookups));
                });
            } else {
                final String channel = redisStorage.getRedisNamespace().getInvalidationChannel();
                redisDriver.subscribe(channel, message -> invalidateLocal(localStorage, holderLookups, holderWriter, repository.getCoreDatabase().getBatchQueue(), message));
            }
        }

        repository.getCoreDatabase().getBatchQueue().addStopTask(holderWriter::closeCompletions);

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