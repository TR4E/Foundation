package me.trae.foundation.database.core.holder;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.holder.LookupTier;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.core.holder.change.ChangeTracker;
import me.trae.foundation.database.core.holder.claim.UniqueClaimer;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.core.value.ColumnValueMapper;
import me.trae.foundation.database.storage.local.LocalStorage;
import me.trae.foundation.database.storage.redis.RedisStorage;
import org.jooq.Field;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@AllArgsConstructor
public final class HolderWriter<E extends Entity> {

    private static final Runnable EMPTY_CALLBACK = () -> {};

    private final Object[] lockArray = Stream.generate(Object::new).limit(64).toArray();

    @Getter
    private final String sourceId = UUID.randomUUID().toString();

    private final AbstractEntityRepository<E> repository;
    private final LocalStorage<UUID, E> localStorage;
    private final LocalEntityIndex<E> localEntityIndex;
    private final RedisStorage<E> redisStorage;
    private final ChangeTracker<E> changeTracker;
    private final UniqueClaimer<E> uniqueClaimer;
    private final boolean redisEnabled;

    private void saveWithCallbacks(final E entity, final Runnable commitRunnable, final Runnable failureRunnable) {
        synchronized (this.getLock(entity.getId())) {
            if (this.changeTracker.isTracked(entity.getId()) || this.localStorage != null && this.localStorage.contains(entity.getId())) {
                throw new DatabaseException("Cannot save existing or already tracked %s %s".formatted(entity.getClass().getSimpleName(), entity.getId()));
            }

            final Map<String, String> encodedMap = this.changeTracker.encode(entity);
            final UniqueClaimer.EntityIdClaim idClaim = this.uniqueClaimer.claimId(entity);

            try {
                final List<Runnable> callbackList = new ArrayList<>(this.uniqueClaimer.claim(entity, this.repository.getProperties()));
                if (this.redisStorage != null) {
                    callbackList.add(() -> this.cacheRedisEntity(entity.getId()));
                }
                callbackList.add(idClaim::release);
                callbackList.addAll(this.callbackList(commitRunnable));

                final List<Runnable> failureCallbackList = new ArrayList<>();
                failureCallbackList.add(() -> {
                    try {
                        this.handleFailedWrite(entity, true);
                    } finally {
                        idClaim.release();
                    }
                });
                failureCallbackList.addAll(this.callbackList(failureRunnable));

                this.repository.save(entity, callbackList, failureCallbackList, this.redisEnabled, this.sourceId);
            } catch (final RuntimeException exception) {
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

    public void save(final E entity, final Runnable commitRunnable, final Runnable failureRunnable) {
        try {
            this.saveWithCallbacks(entity, commitRunnable, failureRunnable);
        } catch (final RuntimeException | Error exception) {
            this.runCallback(failureRunnable, entity.getId());
            throw exception;
        }
    }

    public void save(final E entity, final Runnable commitRunnable) {
        this.save(entity, commitRunnable, EMPTY_CALLBACK);
    }

    public void save(final E entity) {
        this.save(entity, EMPTY_CALLBACK, EMPTY_CALLBACK);
    }

    private boolean updateWithCallbacks(final E entity, final Runnable commitRunnable, final Runnable failureRunnable) {
        synchronized (this.getLock(entity.getId())) {
            if (!this.changeTracker.isTracked(entity)) {
                final E baseline = this.repository.findById(entity.getId()).orElseThrow(() -> new DatabaseException("Cannot update missing %s %s".formatted(entity.getClass().getSimpleName(), entity.getId())));

                if (!this.repository.getCoreDatabase().getEntityRevisionStore().hasState(entity)) {
                    this.repository.inheritRevision(entity, baseline);
                }
                this.changeTracker.snapshot(entity, this.changeTracker.encode(baseline));
            }

            final boolean writeQueued = this.writeChangedLocked(entity, commitRunnable, failureRunnable);

            if (this.localStorage != null) {
                this.localEntityIndex.put(entity, this.localStorage);
            }

            return writeQueued;
        }
    }

    public void update(final E entity, final Runnable commitRunnable, final Runnable failureRunnable) {
        final boolean writeQueued;
        try {
            writeQueued = this.updateWithCallbacks(entity, commitRunnable, failureRunnable);
        } catch (final RuntimeException | Error exception) {
            this.runCallback(failureRunnable, entity.getId());
            throw exception;
        }

        if (!writeQueued) {
            this.runCallback(commitRunnable, entity.getId());
        }
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

    private boolean writeChangedLocked(final E entity, final Runnable commitRunnable, final Runnable failureRunnable) {
        final Map<String, String> encodedMap = this.changeTracker.encode(entity);

        final List<EntityProperty<?, ?>> changedList = this.changeTracker.diff(entity.getId(), encodedMap);
        if (changedList.isEmpty()) {
            return false;
        }

        final List<EntityProperty<?, ?>> persistentChangedList = changedList.stream().filter(EntityProperty::isPersistent).toList();
        if (persistentChangedList.isEmpty()) {
            this.changeTracker.commit(entity.getId(), encodedMap, changedList);
            return false;
        }

        final List<Runnable> callbackList = new ArrayList<>(this.uniqueClaimer.claim(entity, persistentChangedList));
        callbackList.addAll(this.callbackList(commitRunnable));

        final List<Runnable> failureCallbackList = new ArrayList<>();
        failureCallbackList.add(() -> this.handleFailedWrite(entity, false));
        failureCallbackList.addAll(this.callbackList(failureRunnable));

        this.repository.update(
                entity,
                persistentChangedList,
                callbackList,
                failureCallbackList,
                this.redisEnabled,
                this.sourceId
        );

        this.changeTracker.commit(entity, changedList);

        this.localEntityIndex.index(entity);

        return true;
    }

    private void writeChangedLocked(final E entity) {
        this.writeChangedLocked(entity, EMPTY_CALLBACK, EMPTY_CALLBACK);
    }

    private void deleteWithCallbacks(final E entity, final Runnable commitRunnable, final Runnable failureRunnable) {
        synchronized (this.getLock(entity.getId())) {
            final List<Runnable> callbackList = new ArrayList<>();
            callbackList.add(() -> this.uniqueClaimer.release(entity));
            callbackList.addAll(this.callbackList(commitRunnable));

            this.repository.delete(
                    entity,
                    callbackList,
                    this.callbackList(failureRunnable),
                    this.redisEnabled,
                    this.sourceId
            );

            if (this.localStorage != null) {
                this.localEntityIndex.remove(entity.getId(), this.localStorage);
            }

            this.changeTracker.forget(entity.getId());
        }
    }

    public void delete(final E entity, final Runnable commitRunnable, final Runnable failureRunnable) {
        try {
            this.deleteWithCallbacks(entity, commitRunnable, failureRunnable);
        } catch (final RuntimeException | Error exception) {
            this.runCallback(failureRunnable, entity.getId());
            throw exception;
        }
    }

    public void delete(final E entity, final Runnable commitRunnable) {
        this.delete(entity, commitRunnable, EMPTY_CALLBACK);
    }

    public void delete(final E entity) {
        this.delete(entity, EMPTY_CALLBACK, EMPTY_CALLBACK);
    }

    private List<Runnable> callbackList(final Runnable runnable) {
        return runnable == EMPTY_CALLBACK ? List.of() : List.of(runnable);
    }

    private void runCallback(final Runnable runnable, final UUID id) {
        try {
            runnable.run();
        } catch (final RuntimeException exception) {
            this.repository.getCoreDatabase().getBatchQueue().reportFailure(new DatabaseException("Holder callback failed for %s".formatted(id), exception));
        }
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
        this.redisStorage.refresh(id, () -> this.repository.findById(id))
                .ifPresent(this.uniqueClaimer::cacheRedisIndexes);
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

    public void refreshLocal(final UUID id, final HolderLookups<E> holderLookups) {
        synchronized (this.getLock(id)) {
            final E localEntity = this.localStorage.get(id).orElse(null);
            if (localEntity == null) {
                return;
            }

            final List<EntityProperty<?, ?>> changedPropertyList = this.changeTracker.isTracked(localEntity) ? this.changeTracker.diff(localEntity) : List.of();

            if (this.changeTracker.isTracked(localEntity)) {
                this.writeChangedLocked(localEntity);
            }

            final Map<Field<?>, Object> pendingValueMap = this.repository.getCoreDatabase().getBatchQueue().getPendingWriteStore()
                    .getPendingValues(this.repository.getTableSchema(), this.repository.getTenantId().orElse(null), id);

            final E refreshedEntity = holderLookups.getIdLookup().lookup(id, EnumSet.of(LookupTier.REDIS, LookupTier.DATABASE)).orElse(null);
            if (refreshedEntity == null) {
                this.evictLocal(id);

                this.changeTracker.forget(id);
                return;
            }

            for (final EntityProperty<?, ?> entityProperty : this.repository.getProperties()) {
                if (!entityProperty.isPersistent() || changedPropertyList.contains(entityProperty)) {
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

    private <Owner extends Entity, Value> void copyProperty(final EntityProperty<Owner, Value> entityProperty, final E sourceEntity, final E targetEntity) {
        entityProperty.setValue(entityProperty.getEntityType().cast(targetEntity), entityProperty.getValue(entityProperty.getEntityType().cast(sourceEntity)));
    }

    private <Owner extends Entity, Amount extends Number> void apply(final EntityProperty<Owner, Amount> entityProperty, final Entity entity, final long value) {
        final Object boxed = entityProperty.getValueType() == Integer.class ? Integer.valueOf(Math.toIntExact(value)) : Long.valueOf(value);

        entityProperty.setValue(entityProperty.getEntityType().cast(entity), entityProperty.getValueType().cast(boxed));
    }
}