package me.trae.foundation.database.core.holder;

import io.lettuce.core.RedisException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.ConnectionException;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.exception.SynchronizationException;
import me.trae.foundation.database.api.holder.LookupTier;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.utility.DatabaseCallbacks;
import me.trae.foundation.database.core.batch.HolderCommitCallback;
import me.trae.foundation.database.core.batch.PendingWrite;
import me.trae.foundation.database.core.holder.change.ChangeTracker;
import me.trae.foundation.database.core.holder.claim.UniqueClaimer;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.core.value.ColumnValueMapper;
import me.trae.foundation.database.storage.local.LocalStorage;
import me.trae.foundation.database.storage.redis.RedisStorage;
import org.jooq.Field;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

@AllArgsConstructor
public final class HolderWriter<E extends Entity> {

    private static final Runnable EMPTY_CALLBACK = () -> {};

    private final Object[] lockArray = Stream.generate(Object::new).limit(64).toArray();

    @Getter
    private final String sourceId = UUID.randomUUID().toString();

    private final ConcurrentHashMap<UUID, Completion> completionMap = new ConcurrentHashMap<>();

    private final AbstractEntityRepository<E> repository;
    private final LocalStorage<UUID, E> localStorage;
    private final LocalEntityIndex<E> localEntityIndex;
    private final RedisStorage<E> redisStorage;
    private final ChangeTracker<E> changeTracker;
    private final UniqueClaimer<E> uniqueClaimer;
    private final boolean redisEnabled;

    private void saveWithCallbacks(final E entity, final Completion completion) {
        synchronized (this.getLock(entity.getId())) {
            if (this.changeTracker.isTracked(entity.getId()) || this.localStorage != null && this.localStorage.contains(entity.getId())) {
                throw new DatabaseException("Cannot save existing or already tracked %s %s".formatted(entity.getClass().getSimpleName(), entity.getId()));
            }

            final Map<String, String> encodedMap = this.changeTracker.encode(entity);

            final UniqueClaimer.EntityIdClaim idClaim = this.uniqueClaimer.claimId(entity);
            final CompletableFuture<Void> initializationFuture = completion.getInitializationFuture();

            try {
                final List<Runnable> callbackList = new ArrayList<>(this.uniqueClaimer.claim(entity, this.repository.getProperties()));
                if (this.redisStorage != null) {
                    callbackList.add(() -> {
                        try {
                            this.cacheRedisEntity(entity.getId());
                            this.redisStorage.synchronizeProperties(entity.getId(), encodedMap, this.sourceId, false, true)
                                    .whenComplete((_, exception) -> {
                                        if (exception == null) {
                                            initializationFuture.complete(null);
                                        } else {
                                            initializationFuture.completeExceptionally(exception);
                                        }
                                    });
                        } catch (final RuntimeException | Error exception) {
                            initializationFuture.completeExceptionally(exception);
                            throw exception;
                        }
                    });
                } else {
                    initializationFuture.complete(null);
                }

                callbackList.add(idClaim::release);

                callbackList.addAll(this.commitCallbacks(completion, false, initializationFuture));

                final List<Runnable> failureCallbackList = new ArrayList<>();

                failureCallbackList.add(() -> {
                    try {
                        this.handleFailedWrite(entity, true);
                    } finally {
                        idClaim.release();
                    }
                });

                failureCallbackList.addAll(this.failureCallbacks(completion));

                this.repository.save(entity, callbackList, failureCallbackList, this.redisEnabled, this.sourceId);
                completion.getEnqueued().set(true);
            } catch (final RuntimeException | Error exception) {
                this.uniqueClaimer.release(entity);

                idClaim.release();

                throw exception;
            }

            this.changeTracker.snapshot(entity, encodedMap);

            if (this.localStorage != null) {
                this.localEntityIndex.put(entity, this.localStorage);
            }
        }
    }

    private void save(final E entity, final Completion completion) {
        try {
            this.saveWithCallbacks(entity, completion);
        } catch (final RuntimeException | Error exception) {
            if (!completion.getEnqueued().get()) {
                completion.writeFailed();
                throw exception;
            }
            this.repository.getCoreDatabase().getBatchQueue().reportFailure(new DatabaseException("Post-enqueue cache maintenance failed; write callbacks will report the write outcome", exception));
        }
    }

    public void save(final E entity, final Runnable commitRunnable, final Runnable failureRunnable) {
        this.save(entity, this.completion(entity.getId(), null, commitRunnable, failureRunnable, _ -> {}));
    }

    public void saveAndSynchronize(final E entity, final Duration timeout, final Runnable synchronizedRunnable, final Runnable failureRunnable, final Consumer<SynchronizationException> synchronizationFailureConsumer) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Synchronization timeout must be positive");
        }
        timeout.toNanos();
        this.save(entity, this.completion(entity.getId(), timeout, synchronizedRunnable, failureRunnable, synchronizationFailureConsumer));
    }

    public void saveAndSynchronize(final E entity, final Runnable synchronizedRunnable, final Runnable failureRunnable, final Consumer<SynchronizationException> synchronizationFailureConsumer) {
        this.saveAndSynchronize(entity, Duration.ofSeconds(5), synchronizedRunnable, failureRunnable, synchronizationFailureConsumer);
    }

    public void save(final E entity, final Runnable commitRunnable) {
        this.save(entity, commitRunnable, EMPTY_CALLBACK);
    }

    public void save(final E entity) {
        this.save(entity, EMPTY_CALLBACK, EMPTY_CALLBACK);
    }

    private boolean updateWithCallbacks(final E entity, final Completion completion) {
        synchronized (this.getLock(entity.getId())) {
            if (!this.changeTracker.isTracked(entity)) {
                final E baseline = this.repository.findById(entity.getId()).orElseThrow(() -> new DatabaseException("Cannot update missing %s %s".formatted(entity.getClass().getSimpleName(), entity.getId())));

                if (this.redisStorage != null) {
                    this.redisStorage.restoreSharedProperties(baseline);
                }

                if (!this.repository.getCoreDatabase().getEntityRevisionStore().hasState(entity)) {
                    this.repository.inheritRevision(entity, baseline);
                }

                this.changeTracker.snapshot(entity, this.changeTracker.encode(baseline));
            }

            final boolean writeQueued = this.writeChangedLocked(entity, completion);

            if (this.localStorage != null) {
                this.localEntityIndex.put(entity, this.localStorage);
            }

            return writeQueued;
        }
    }

    private void update(final E entity, final Completion completion) {
        try {
            if (!this.updateWithCallbacks(entity, completion)) {
                if (completion.getTimeout() != null && this.repository.getCoreDatabase().getBatchQueue().getPendingWriteStore()
                        .attachCommitCallback(this.repository.getTableSchema(), this.repository.getTenantId().orElse(null), entity.getId(), this.commitCallbacks(completion, false, CompletableFuture.completedFuture(null)), this.failureCallbacks(completion))) {
                    completion.getEnqueued().set(true);
                    return;
                }
                this.committed(completion, null, false, CompletableFuture.completedFuture(null));
            }
        } catch (final RuntimeException | Error exception) {
            if (!completion.getEnqueued().get()) {
                completion.writeFailed();
                throw exception;
            }
            this.repository.getCoreDatabase().getBatchQueue().reportFailure(new DatabaseException("Post-enqueue cache maintenance failed; write callbacks will report the write outcome", exception));
        }
    }

    public void update(final E entity, final Runnable commitRunnable, final Runnable failureRunnable) {
        this.update(entity, this.completion(entity.getId(), null, commitRunnable, failureRunnable, _ -> {}));
    }

    public void updateAndSynchronize(final E entity, final Duration timeout, final Runnable synchronizedRunnable, final Runnable failureRunnable, final Consumer<SynchronizationException> synchronizationFailureConsumer) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Synchronization timeout must be positive");
        }
        timeout.toNanos();
        this.update(entity, this.completion(entity.getId(), timeout, synchronizedRunnable, failureRunnable, synchronizationFailureConsumer));
    }

    public void updateAndSynchronize(final E entity, final Runnable synchronizedRunnable, final Runnable failureRunnable, final Consumer<SynchronizationException> synchronizationFailureConsumer) {
        this.updateAndSynchronize(entity, Duration.ofSeconds(5), synchronizedRunnable, failureRunnable, synchronizationFailureConsumer);
    }

    public void update(final E entity, final Runnable commitRunnable) {
        this.update(entity, commitRunnable, EMPTY_CALLBACK);
    }

    public void update(final E entity) {
        this.update(entity, EMPTY_CALLBACK, EMPTY_CALLBACK);
    }

    public void writeChanged(final E entity) {
        synchronized (this.getLock(entity.getId())) {
            this.writeChangedLocked(entity);
        }
    }

    private boolean writeChangedLocked(final E entity, final Completion completion) {
        final Map<String, String> encodedMap = this.changeTracker.encode(entity);

        final List<EntityProperty<?, ?>> changedList = this.changeTracker.diff(entity.getId(), encodedMap);

        if (changedList.isEmpty()) {
            return false;
        }

        final List<EntityProperty<?, ?>> persistentChangedList = changedList.stream().filter(EntityProperty::isPersistent).toList();

        final CompletableFuture<Void> sharedFuture;
        if (this.redisStorage != null) {
            final Map<String, String> changedValueMap = new LinkedHashMap<>();

            changedList.forEach(entityProperty -> changedValueMap.put(entityProperty.getName(), encodedMap.get(entityProperty.getName())));

            sharedFuture = this.redisStorage.synchronizeProperties(
                    entity.getId(),
                    changedValueMap,
                    this.sourceId,
                    persistentChangedList.isEmpty() && completion.getTimeout() != null,
                    false,
                    completion.getTimeout() == null ? Duration.ofSeconds(5) : completion.getTimeout()
            );
        } else {
            sharedFuture = CompletableFuture.completedFuture(null);
        }

        if (persistentChangedList.isEmpty()) {
            completion.setSharedOnly(true);
            this.changeTracker.commit(entity, changedList);
            this.committed(completion, null, false, sharedFuture);
            return true;
        }

        final List<Runnable> callbackList = new ArrayList<>(this.uniqueClaimer.claim(entity, persistentChangedList));

        callbackList.addAll(this.commitCallbacks(completion, false, sharedFuture));

        final List<Runnable> failureCallbackList = new ArrayList<>();

        failureCallbackList.add(() -> this.handleFailedWrite(entity, false));

        failureCallbackList.addAll(this.failureCallbacks(completion));

        this.repository.update(
                entity,
                persistentChangedList,
                callbackList,
                failureCallbackList,
                this.redisEnabled,
                this.sourceId
        );
        completion.getEnqueued().set(true);

        this.changeTracker.commit(entity, changedList);

        this.localEntityIndex.index(entity);

        return true;
    }

    private void writeChangedLocked(final E entity) {
        if (!this.changeTracker.diff(entity).isEmpty()) {
            this.writeChangedLocked(entity, this.completion(entity.getId(), null, EMPTY_CALLBACK, EMPTY_CALLBACK, _ -> {}));
        }
    }

    private void deleteWithCallbacks(final E entity, final Completion completion) {
        synchronized (this.getLock(entity.getId())) {
            final List<Runnable> callbackList = new ArrayList<>();

            callbackList.add(() -> this.uniqueClaimer.release(entity));

            callbackList.addAll(this.commitCallbacks(completion, true, CompletableFuture.completedFuture(null)));

            this.repository.delete(
                    entity,
                    callbackList,
                    this.failureCallbacks(completion),
                    this.redisEnabled,
                    this.sourceId
            );
            completion.getEnqueued().set(true);

            if (this.localStorage != null) {
                this.localEntityIndex.remove(entity.getId(), this.localStorage);
            }

            this.changeTracker.forget(entity.getId());
        }
    }

    private void delete(final E entity, final Completion completion) {
        try {
            this.deleteWithCallbacks(entity, completion);
        } catch (final RuntimeException | Error exception) {
            if (!completion.getEnqueued().get()) {
                completion.writeFailed();
                throw exception;
            }
            this.repository.getCoreDatabase().getBatchQueue().reportFailure(new DatabaseException("Post-enqueue cache maintenance failed; write callbacks will report the write outcome", exception));
        }
    }

    public void delete(final E entity, final Runnable commitRunnable, final Runnable failureRunnable) {
        this.delete(entity, this.completion(entity.getId(), null, commitRunnable, failureRunnable, _ -> {}));
    }

    public void deleteAndSynchronize(final E entity, final Duration timeout, final Runnable synchronizedRunnable, final Runnable failureRunnable, final Consumer<SynchronizationException> synchronizationFailureConsumer) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Synchronization timeout must be positive");
        }
        timeout.toNanos();
        this.delete(entity, this.completion(entity.getId(), timeout, synchronizedRunnable, failureRunnable, synchronizationFailureConsumer));
    }

    public void deleteAndSynchronize(final E entity, final Runnable synchronizedRunnable, final Runnable failureRunnable, final Consumer<SynchronizationException> synchronizationFailureConsumer) {
        this.deleteAndSynchronize(entity, Duration.ofSeconds(5), synchronizedRunnable, failureRunnable, synchronizationFailureConsumer);
    }

    public void delete(final E entity, final Runnable commitRunnable) {
        this.delete(entity, commitRunnable, EMPTY_CALLBACK);
    }

    public void delete(final E entity) {
        this.delete(entity, EMPTY_CALLBACK, EMPTY_CALLBACK);
    }

    private Completion completion(final UUID id, final Duration timeout, final Runnable successRunnable, final Runnable failureRunnable, final Consumer<SynchronizationException> synchronizationFailureConsumer) {
        final Completion completion = new Completion(id, timeout, successRunnable, failureRunnable, synchronizationFailureConsumer, this.repository.getCoreDatabase().getBatchQueue()::reportFailure);
        if (timeout != null) {
            this.completionMap.put(completion.getRequestId(), completion);
            completion.getTerminalFuture().whenComplete((_, _) -> this.completionMap.remove(completion.getRequestId(), completion));
        }
        return completion;
    }

    public void closeCompletions() {
        this.completionMap.values().stream()
                .filter(completion -> completion.getCommitted().get())
                .forEach(completion -> completion.synchronizationFailed(new SynchronizationException("Holder stopped before synchronization completed")));
    }

    private List<Runnable> failureCallbacks(final Completion completion) {
        return completion.getFailureRunnable() == EMPTY_CALLBACK && completion.getTimeout() == null ? List.of() : List.of(completion::writeFailed);
    }

    private List<Runnable> commitCallbacks(final Completion completion, final boolean delete, final CompletableFuture<Void> prerequisiteFuture) {
        if (completion.getSuccessRunnable() == EMPTY_CALLBACK && completion.getFailureRunnable() == EMPTY_CALLBACK && completion.getTimeout() == null) {
            return List.of();
        }
        return List.of(new HolderCommitCallback(completion.getTimeout(), pendingWrite -> this.committed(completion, pendingWrite, delete, prerequisiteFuture)));
    }

    private void committed(final Completion completion, final PendingWrite pendingWrite, final boolean delete, final CompletableFuture<Void> prerequisiteFuture) {
        if (completion.getTerminal().get() || !completion.getCommitStarted().compareAndSet(false, true)) {
            return;
        }
        completion.getCommitted().set(true);
        if (completion.getTimeout() == null) {
            completion.succeeded();
            prerequisiteFuture.whenComplete((_, exception) -> {
                if (exception != null) {
                    this.repository.getCoreDatabase().getBatchQueue().reportFailure(new SynchronizationException("Write committed but cache maintenance failed", exception));
                }
            });
            return;
        }

        final CompletableFuture<Void> synchronizationFuture;
        if (this.redisStorage == null || completion.isSharedOnly()) {
            synchronizationFuture = prerequisiteFuture;
        } else if (pendingWrite != null && pendingWrite.getInvalidation() != null) {
            synchronizationFuture = pendingWrite.synchronize(() -> prerequisiteFuture.thenCompose(_ -> this.redisStorage.synchronize(pendingWrite.getInvalidation(), pendingWrite.getSynchronizationTimeout())));
        } else {
            synchronizationFuture = prerequisiteFuture.thenCompose(_ -> this.redisStorage.synchronize(completion.getId(), this.sourceId, delete, completion.getTimeout()));
        }

        synchronizationFuture.thenApply(_ -> (Void) null)
                .orTimeout(completion.getTimeout().toNanos(), TimeUnit.NANOSECONDS)
                .whenComplete((_, exception) -> {
                    if (exception == null) {
                        completion.succeeded();
                    } else {
                        completion.synchronizationFailed(exception);
                    }
                });
    }

    public void prepareAdmission(final E entity, final HolderLookups<E> holderLookups) {
        if (this.repository.getCoreDatabase().getBatchQueue().getPendingWriteStore()
                .hasPendingWrite(this.repository.getTableSchema(), this.repository.getTenantId().orElse(null), entity.getId())) {
            return;
        }

        final List<EntityProperty<?, ?>> dirtyPropertyList = this.changeTracker.isTracked(entity) ? this.changeTracker.diff(entity) : List.of();
        final E currentEntity = holderLookups.loadFresh(entity.getId())
                .orElseThrow(() -> new DatabaseException("Entity %s was deleted during cache admission".formatted(entity.getId())));

        for (final EntityProperty<?, ?> entityProperty : this.repository.getProperties()) {
            if (!dirtyPropertyList.contains(entityProperty)) {
                this.copyProperty(entityProperty, currentEntity, entity);
            }
        }

        this.repository.inheritRevision(entity, currentEntity);
        this.changeTracker.snapshot(entity, this.changeTracker.encode(currentEntity));
    }

    public long increment(final E entity, final EntityProperty<? super E, ? extends Number> entityProperty, final long delta) {
        synchronized (this.getLock(entity.getId())) {
            final long value = this.repository.increment(entity, entityProperty, delta, this.redisEnabled, this.sourceId);

            this.apply(entityProperty, entity, value);

            final Map<String, String> encodedMap = this.changeTracker.encode(entity);

            this.changeTracker.commit(entity.getId(), encodedMap, List.of(entityProperty));

            this.localEntityIndex.index(entity);

            return value;
        }
    }

    private Object getLock(final UUID id) {
        return this.lockArray[Math.floorMod(id.hashCode(), this.lockArray.length)];
    }

    private void cacheRedisEntity(final UUID id) {
        this.redisStorage.refresh(id, () -> this.repository.findById(id)).ifPresent(this.uniqueClaimer::cacheRedisIndexes);
    }

    private void handleFailedWrite(final E entity, final boolean releaseUniqueClaims) {
        synchronized (this.getLock(entity.getId())) {
            this.changeTracker.forget(entity.getId());

            if (this.localStorage != null) {
                final E localEntity = this.localStorage.get(entity.getId()).orElse(null);

                if (localEntity == null || localEntity == entity) {
                    if (releaseUniqueClaims) {
                        this.localEntityIndex.remove(entity.getId(), this.localStorage);
                    } else {
                        this.localEntityIndex.evict(entity.getId(), this.localStorage);
                    }
                }
            }

            if (releaseUniqueClaims) {
                this.uniqueClaimer.releaseFailedCreate(entity, this.repository.findById(entity.getId()));
            }
        }
    }

    public synchronized <Value> void transferUniqueProperty(final E claimant, final E currentOwner, final EntityProperty<? super E, Value> entityProperty, final Value claimedValue, final Value replacementValue) {
        final Value claimantPreviousValue = entityProperty.getValue(claimant);

        this.repository.transferUniqueProperty(
                claimant,
                currentOwner,
                entityProperty,
                claimedValue,
                replacementValue,
                this.redisEnabled,
                this.sourceId
        );

        this.uniqueClaimer.transferProperty(entityProperty, claimantPreviousValue, claimedValue, claimant.getId(), currentOwner.getId());

        this.changeTracker.snapshot(claimant);
        this.changeTracker.snapshot(currentOwner);

        if (this.localStorage != null) {
            this.localEntityIndex.index(currentOwner);
            this.localEntityIndex.index(claimant);
        }
    }

    public void evictLocal(final UUID id) {
        if (this.localStorage != null) {
            this.localEntityIndex.evict(id, this.localStorage);
        }
    }

    private void refreshLocal(final UUID id, final HolderLookups<E> holderLookups, final boolean strict) {
        synchronized (this.getLock(id)) {
            final E localEntity = this.localStorage.get(id).orElse(null);
            if (localEntity == null) {
                return;
            }

            final List<EntityProperty<?, ?>> changedPropertyList = this.changeTracker.isTracked(localEntity) ? this.changeTracker.diff(localEntity) : List.of();

            if (this.changeTracker.isTracked(localEntity)) {
                this.writeChangedLocked(localEntity);
            }

            final Map<Field<?>, Object> pendingValueMap = this.repository.getCoreDatabase().getBatchQueue().getPendingWriteStore().getPendingValues(this.repository.getTableSchema(), this.repository.getTenantId().orElse(null), id);

            final E refreshedEntity = holderLookups.getIdLookup().lookup(id, EnumSet.of(LookupTier.REDIS, LookupTier.DATABASE)).orElse(null);
            if (refreshedEntity == null) {
                this.evictLocal(id);

                this.changeTracker.forget(id);
                return;
            }

            if (this.redisStorage != null) {
                try {
                    this.redisStorage.restoreSharedProperties(refreshedEntity);
                } catch (final RedisException | ConnectionException exception) {
                    if (strict) {
                        throw exception;
                    }

                    for (final EntityProperty<?, ?> entityProperty : this.repository.getProperties()) {
                        if (!entityProperty.isPersistent()) {
                            this.copyProperty(entityProperty, localEntity, refreshedEntity);
                        }
                    }
                }
            }

            for (final EntityProperty<?, ?> entityProperty : this.repository.getProperties()) {
                if (changedPropertyList.contains(entityProperty)) {
                    this.copyProperty(entityProperty, localEntity, refreshedEntity);
                }

                if (entityProperty.isPersistent() && pendingValueMap.containsKey(this.repository.getTableSchema().getField(entityProperty))) {
                    ColumnValueMapper.writeStored(entityProperty, refreshedEntity, pendingValueMap.get(this.repository.getTableSchema().getField(entityProperty)));
                }
            }

            this.changeTracker.snapshot(refreshedEntity);

            if (this.localStorage.isPinned(id)) {
                this.localEntityIndex.pin(refreshedEntity, this.localStorage);
            } else {
                this.localEntityIndex.put(refreshedEntity, this.localStorage);
            }
        }
    }

    public void refreshLocal(final UUID id, final HolderLookups<E> holderLookups) {
        this.refreshLocal(id, holderLookups, false);
    }

    public void refreshLocalStrict(final UUID id, final HolderLookups<E> holderLookups) {
        this.refreshLocal(id, holderLookups, true);
    }

    private <Owner extends Entity, Value> void copyProperty(final EntityProperty<Owner, Value> entityProperty, final E sourceEntity, final E targetEntity) {
        entityProperty.setValue(entityProperty.getEntityType().cast(targetEntity), entityProperty.getValue(entityProperty.getEntityType().cast(sourceEntity)));
    }

    private <Owner extends Entity, Amount extends Number> void apply(final EntityProperty<Owner, Amount> entityProperty, final Entity entity, final long value) {
        final Object boxed = entityProperty.getValueType() == Integer.class ? Integer.valueOf(Math.toIntExact(value)) : Long.valueOf(value);

        entityProperty.setValue(entityProperty.getEntityType().cast(entity), entityProperty.getValueType().cast(boxed));
    }

    @RequiredArgsConstructor
    @Getter
    private static final class Completion {

        private final UUID requestId = UUID.randomUUID();

        private final AtomicBoolean terminal = new AtomicBoolean();
        private final AtomicBoolean enqueued = new AtomicBoolean();
        private final AtomicBoolean commitStarted = new AtomicBoolean();
        private final AtomicBoolean committed = new AtomicBoolean();

        private final CompletableFuture<Void> initializationFuture = new CompletableFuture<>();
        private final CompletableFuture<Void> terminalFuture = new CompletableFuture<>();

        private final UUID id;

        private final Duration timeout;
        private final Runnable successRunnable, failureRunnable;
        private final Consumer<SynchronizationException> synchronizationFailureConsumer;
        private final Consumer<DatabaseException> failureConsumer;

        @Setter
        private boolean sharedOnly;

        private void succeeded() {
            if (this.terminal.compareAndSet(false, true)) {
                this.terminalFuture.complete(null);
                if (this.successRunnable != EMPTY_CALLBACK) {
                    DatabaseCallbacks.dispatch(this.successRunnable, this.failureConsumer);
                }
            }
        }

        private void writeFailed() {
            if (this.terminal.compareAndSet(false, true)) {
                this.terminalFuture.complete(null);
                if (this.failureRunnable != EMPTY_CALLBACK) {
                    DatabaseCallbacks.dispatch(this.failureRunnable, this.failureConsumer);
                }
            }
        }

        private void synchronizationFailed(final Throwable cause) {
            if (this.terminal.compareAndSet(false, true)) {
                this.terminalFuture.complete(null);
                final SynchronizationException exception = new SynchronizationException("Write for %s committed, but cache synchronization failed".formatted(this.id), cause);
                DatabaseCallbacks.dispatch(() -> this.synchronizationFailureConsumer.accept(exception), this.failureConsumer);
                this.failureConsumer.accept(exception);
            }
        }
    }
}