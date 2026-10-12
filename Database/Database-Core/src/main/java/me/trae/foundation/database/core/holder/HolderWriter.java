package me.trae.foundation.database.core.holder;

import lombok.AllArgsConstructor;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.core.holder.change.ChangeTracker;
import me.trae.foundation.database.core.holder.claim.UniqueClaimer;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.storage.local.LocalStorage;
import me.trae.foundation.database.storage.redis.RedisStorage;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@AllArgsConstructor
public final class HolderWriter<E extends Entity> {

    private final Object[] lockArray = Stream.generate(Object::new).limit(64).toArray();

    private final AbstractEntityRepository<E> repository;
    private final LocalStorage<UUID, E> localStorage;
    private final LocalEntityIndex<E> localEntityIndex;
    private final RedisStorage<E> redisStorage;
    private final ChangeTracker<E> changeTracker;
    private final UniqueClaimer<E> uniqueClaimer;
    private final boolean redisEnabled;

    public void save(final E entity) {
        synchronized (this.getLock(entity.getId())) {
            if (this.changeTracker.isTracked(entity.getId()) || this.localStorage != null && this.localStorage.contains(entity.getId())) {
                throw new DatabaseException("Cannot save existing or already tracked %s %s".formatted(entity.getClass().getSimpleName(), entity.getId()));
            }

            final Map<String, String> encodedMap = this.changeTracker.encode(entity);

            final List<Runnable> callbackList = this.uniqueClaimer.claim(entity, this.repository.getProperties());

            try {
                this.repository.save(entity, callbackList, List.of(() -> this.handleFailedWrite(entity, true)), this.redisEnabled);
            } catch (final RuntimeException exception) {
                this.uniqueClaimer.release(entity);
                throw exception;
            }

            this.changeTracker.snapshot(entity, encodedMap);

            if (this.localStorage != null) {
                this.localEntityIndex.put(entity, this.localStorage);
            }
        }
    }

    public void update(final E entity) {
        synchronized (this.getLock(entity.getId())) {
            if (!this.changeTracker.isTracked(entity)) {
                final E baseline = this.repository.findById(entity.getId()).orElseThrow(() -> new DatabaseException("Cannot update missing %s %s".formatted(entity.getClass().getSimpleName(), entity.getId())));

                if (!this.repository.getCoreDatabase().getEntityRevisionStore().hasState(entity)) {
                    this.repository.inheritRevision(entity, baseline);
                }
                this.changeTracker.snapshot(entity, this.changeTracker.encode(baseline));
            }

            this.writeChangedLocked(entity);

            if (this.localStorage != null) {
                this.localEntityIndex.put(entity, this.localStorage);
            }
        }
    }

    public void writeChanged(final E entity) {
        synchronized (this.getLock(entity.getId())) {
            this.writeChangedLocked(entity);
        }
    }

    private void writeChangedLocked(final E entity) {
        final Map<String, String> encodedMap = this.changeTracker.encode(entity);

        final List<EntityProperty<?, ?>> changedList = this.changeTracker.diff(entity.getId(), encodedMap);
        if (changedList.isEmpty()) {
            return;
        }

        final List<EntityProperty<?, ?>> persistentChangedList = changedList.stream().filter(EntityProperty::isPersistent).toList();
        if (persistentChangedList.isEmpty()) {
            this.changeTracker.commit(entity.getId(), encodedMap, changedList);
            return;
        }

        final List<Runnable> callbackList = this.uniqueClaimer.claim(entity, persistentChangedList);

        this.repository.update(entity, persistentChangedList, callbackList, List.of(() -> this.handleFailedWrite(entity, false)), this.redisEnabled);

        this.changeTracker.commit(entity, changedList);
        this.localEntityIndex.index(entity);
    }

    public void delete(final E entity) {
        synchronized (this.getLock(entity.getId())) {
            this.repository.delete(entity, List.of(() -> this.uniqueClaimer.release(entity)), this.redisEnabled);

            if (this.localStorage != null) {
                this.localEntityIndex.remove(entity.getId(), this.localStorage);
            }

            this.changeTracker.forget(entity.getId());
        }
    }

    public long increment(final E entity, final EntityProperty<? super E, ? extends Number> entityProperty, final long delta) {
        synchronized (this.getLock(entity.getId())) {
            final Number current = entityProperty.getValue(entity);

            final long value = this.redisStorage != null ? this.redisStorage.increment(entity.getId(), entityProperty, delta) : (current == null ? 0L : current.longValue()) + delta;

            this.apply(entityProperty, entity, value);

            final Map<String, String> encodedMap = this.changeTracker.encode(entity);

            this.repository.update(entity, List.of(entityProperty), Collections.emptyList(), Collections.emptyList(), this.redisEnabled);

            this.changeTracker.commit(entity.getId(), encodedMap, List.of(entityProperty));
            this.localEntityIndex.index(entity);

            return value;
        }
    }

    private Object getLock(final UUID id) {
        return this.lockArray[Math.floorMod(id.hashCode(), this.lockArray.length)];
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
                this.uniqueClaimer.release(entity);
            }
        }
    }

    public synchronized <Value> void transferUniqueProperty(final E claimant, final E currentOwner, final EntityProperty<? super E, Value> entityProperty, final Value claimedValue, final Value replacementValue) {
        final Value claimantPreviousValue = entityProperty.getValue(claimant);

        this.repository.transferUniqueProperty(claimant, currentOwner, entityProperty, claimedValue, replacementValue, this.redisEnabled);
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

    private <Owner extends Entity, Amount extends Number> void apply(final EntityProperty<Owner, Amount> entityProperty, final Entity entity, final long value) {
        final Object boxed = entityProperty.getValueType() == Integer.class ? Integer.valueOf(Math.toIntExact(value)) : Long.valueOf(value);

        entityProperty.setValue(entityProperty.getEntityType().cast(entity), entityProperty.getValueType().cast(boxed));
    }
}