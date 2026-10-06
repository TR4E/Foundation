package me.trae.foundation.database.core.holder;

import lombok.AllArgsConstructor;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.core.holder.change.ChangeTracker;
import me.trae.foundation.database.core.holder.claim.UniqueClaimer;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.storage.local.LocalStorage;
import me.trae.foundation.database.storage.redis.RedisStorage;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
public final class HolderWriter<E extends Entity> {

    private final AbstractEntityRepository<E> repository;
    private final LocalStorage<UUID, E> localStorage;
    private final RedisStorage<E> redisStorage;
    private final ChangeTracker<E> changeTracker;
    private final UniqueClaimer<E> uniqueClaimer;

    public void save(final E entity) {
        if (this.changeTracker.isTracked(entity.getId())) {
            this.writeChanged(entity);
        } else {
            this.writeFull(entity);
        }

        if (this.localStorage != null) {
            this.localStorage.put(entity.getId(), entity);
        }
    }

    public void writeFull(final E entity) {
        final List<Runnable> callbackList = this.uniqueClaimer.claim(entity, this.repository.getProperties());

        if (this.redisStorage != null) {
            this.redisStorage.put(entity.getId(), entity);
        }

        this.repository.save(entity, callbackList);

        this.changeTracker.snapshot(entity);
    }

    public void writeChanged(final E entity) {
        final List<EntityProperty<?, ?>> changedList = this.changeTracker.diff(entity);
        if (changedList.isEmpty()) {
            return;
        }

        final List<Runnable> callbackList = this.uniqueClaimer.claim(entity, changedList);

        if (this.redisStorage != null) {
            this.redisStorage.putProperties(entity, changedList);
        }

        this.repository.update(entity, changedList, callbackList);

        this.changeTracker.commit(entity, changedList);
    }

    public void delete(final E entity) {
        if (this.localStorage != null) {
            this.localStorage.remove(entity.getId());
        }

        if (this.redisStorage != null) {
            this.redisStorage.remove(entity.getId());
        }

        this.uniqueClaimer.release(entity);

        this.repository.delete(entity);

        this.changeTracker.forget(entity.getId());
    }

    public long increment(final E entity, final EntityProperty<? super E, ? extends Number> entityProperty, final long delta) {
        final Number current = entityProperty.getValue(entity);

        final long value = this.redisStorage != null ? this.redisStorage.increment(entity.getId(), entityProperty, delta) : (current == null ? 0L : current.longValue()) + delta;

        this.apply(entityProperty, entity, value);

        this.repository.update(entity, List.of(entityProperty), Collections.emptyList());

        this.changeTracker.commit(entity, List.of(entityProperty));

        return value;
    }

    private <Owner extends Entity, Amount extends Number> void apply(final EntityProperty<Owner, Amount> entityProperty, final Entity entity, final long value) {
        final Object boxed = entityProperty.getValueType() == Integer.class ? Integer.valueOf(Math.toIntExact(value)) : Long.valueOf(value);

        entityProperty.setValue(entityProperty.getEntityType().cast(entity), entityProperty.getValueType().cast(boxed));
    }
}