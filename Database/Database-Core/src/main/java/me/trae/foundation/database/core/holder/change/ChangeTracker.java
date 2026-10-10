package me.trae.foundation.database.core.holder.change;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.storage.codec.EntityCodec;
import me.trae.foundation.database.storage.local.LocalStorage;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ChangeTracker<E extends Entity> {

    private final EntityCodec<E> entityCodec;
    private final LocalStorage<UUID, Map<String, String>> snapshotStorage;

    public ChangeTracker(final Class<E> entityType, final Duration expiry) {
        this.entityCodec = new EntityCodec<>(entityType);
        this.snapshotStorage = new LocalStorage<>(expiry);
    }

    public boolean isTracked(final UUID id) {
        return this.snapshotStorage.contains(id);
    }

    public Map<String, String> encode(final E entity) {
        return this.entityCodec.encode(entity);
    }

    public void snapshot(final UUID id, final Map<String, String> encodedMap) {
        this.snapshotStorage.put(id, new HashMap<>(encodedMap));
    }

    public void snapshot(final E entity) {
        this.snapshot(entity.getId(), this.encode(entity));
    }

    public void snapshotIfAbsent(final E entity) {
        if (!this.isTracked(entity.getId())) {
            this.snapshot(entity);
        }
    }

    public List<EntityProperty<?, ?>> diff(final UUID id, final Map<String, String> encodedMap) {
        final Map<String, String> snapshotMap = this.snapshotStorage.get(id).orElse(Collections.emptyMap());

        return this.entityCodec.getProperties().stream()
                .filter(entityProperty -> !snapshotMap.containsKey(entityProperty.getName()) || !Objects.equals(snapshotMap.get(entityProperty.getName()), encodedMap.get(entityProperty.getName())))
                .toList();
    }

    public List<EntityProperty<?, ?>> diff(final E entity) {
        return this.diff(entity.getId(), this.encode(entity));
    }

    public void commit(final UUID id, final Map<String, String> encodedMap, final Collection<? extends EntityProperty<?, ?>> entityProperties) {
        final Map<String, String> snapshotMap = new HashMap<>(this.snapshotStorage.get(id).orElse(Collections.emptyMap()));

        for (final EntityProperty<?, ?> entityProperty : entityProperties) {
            snapshotMap.put(entityProperty.getName(), encodedMap.get(entityProperty.getName()));
        }

        this.snapshotStorage.put(id, snapshotMap);
    }

    public void commit(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties) {
        this.commit(entity.getId(), this.encode(entity), entityProperties);
    }

    public void forget(final UUID id) {
        this.snapshotStorage.remove(id);
    }

    public void evictExpired() {
        this.snapshotStorage.evictExpired();
    }
}