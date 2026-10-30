package me.trae.foundation.database.core.holder;

import lombok.Setter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.repository.index.EntityIndex;
import me.trae.foundation.database.lookup.index.EntityIndexKey;
import me.trae.foundation.database.storage.local.LocalStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class LocalEntityIndex<E extends Entity> {

    private final Map<String, EntityIndex<E>> indexMap = new HashMap<>();
    private final Map<String, UUID> idByKeyMap = new HashMap<>();
    private final Map<UUID, List<String>> keysByIdMap = new HashMap<>();

    @Setter
    private Function<E, Runnable> admissionFunction = _ -> () -> {};

    public LocalEntityIndex(final List<EntityIndex<E>> indexes) {
        indexes.forEach(index -> this.indexMap.put(index.getName(), index));
    }

    private void admit(final E entity, final LocalStorage<UUID, E> localStorage, final boolean pinned) {
        localStorage.withLock(entity.getId(), () -> {
            final Runnable completionRunnable = this.admissionFunction.apply(entity);
            try {
                synchronized (this) {
                    this.index(entity);
                    if (pinned) {
                        localStorage.pin(entity.getId(), entity);
                    } else {
                        localStorage.put(entity.getId(), entity);
                    }
                }
            } finally {
                completionRunnable.run();
            }
        });
    }

    public void put(final E entity, final LocalStorage<UUID, E> localStorage) {
        this.admit(entity, localStorage, false);
    }

    public void pin(final E entity, final LocalStorage<UUID, E> localStorage) {
        this.admit(entity, localStorage, true);
    }

    public synchronized void index(final E entity) {
        this.remove(entity.getId());

        final List<String> keyList = new ArrayList<>();

        for (final EntityIndex<E> entityIndex : this.indexMap.values()) {
            if (!entityIndex.hasValues(entity)) {
                continue;
            }

            final String key = EntityIndexKey.encode(entityIndex, entityIndex.getValues(entity));
            this.idByKeyMap.putIfAbsent(key, entity.getId());
            keyList.add(key);
        }

        this.keysByIdMap.put(entity.getId(), keyList);
    }

    private synchronized void remove(final UUID id) {
        final List<String> keyList = this.keysByIdMap.remove(id);

        if (keyList != null) {
            keyList.forEach(key -> this.idByKeyMap.remove(key, id));
        }
    }

    public void evict(final UUID id, final LocalStorage<UUID, E> localStorage) {
        localStorage.withLock(id, () -> {
            this.remove(id);
            localStorage.evict(id);
        });
    }

    public Optional<E> get(final EntityIndex<? super E> entityIndex, final Object[] values, final LocalStorage<UUID, E> localStorage) {
        final EntityIndex<E> registeredIndex = this.indexMap.get(entityIndex.getName());

        if (registeredIndex == null || !this.sameIndex(registeredIndex, entityIndex)) {
            throw new IllegalArgumentException("Index %s is not registered".formatted(entityIndex.getName()));
        }

        final String key = EntityIndexKey.encode(entityIndex, entityIndex.getValues(values));
        final UUID id = this.getId(key);

        if (id == null) {
            return Optional.empty();
        }

        return localStorage.withValueLock(id, () -> {
            final Optional<E> entity = localStorage.get(id);
            if (entity.isEmpty() || !registeredIndex.hasValues(entity.get()) || !key.equals(EntityIndexKey.encode(registeredIndex, registeredIndex.getValues(entity.get())))) {
                this.remove(id);
                return Optional.empty();
            }
            return entity;
        });
    }

    private synchronized UUID getId(final String key) {
        return this.idByKeyMap.get(key);
    }

    public void remove(final UUID id, final LocalStorage<UUID, E> localStorage) {
        localStorage.withLock(id, () -> {
            this.remove(id);
            localStorage.remove(id);
        });
    }

    private boolean sameIndex(final EntityIndex<E> first, final EntityIndex<?> second) {
        return first.getProperties().equals(second.getProperties());
    }
}