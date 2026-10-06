package me.trae.foundation.database.core.holder.change;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.storage.codec.EntityCodec;
import me.trae.foundation.database.storage.local.LocalStorage;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ChangeTracker<E extends Entity> {

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final EntityCodec<E> entityCodec;
    private final LocalStorage<UUID, Map<String, Long>> snapshotStorage;

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
        final Map<String, Long> hashMap = new HashMap<>();

        encodedMap.forEach((name, value) -> hashMap.put(name, hash(value)));

        this.snapshotStorage.put(id, hashMap);
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
        final Map<String, Long> snapshotMap = this.snapshotStorage.get(id).orElse(Collections.emptyMap());

        return this.entityCodec.getProperties().stream()
                .filter(entityProperty -> !snapshotMap.containsKey(entityProperty.getName()) || !Objects.equals(snapshotMap.get(entityProperty.getName()), hash(encodedMap.get(entityProperty.getName()))))
                .toList();
    }

    public List<EntityProperty<?, ?>> diff(final E entity) {
        return this.diff(entity.getId(), this.encode(entity));
    }

    public void commit(final UUID id, final Map<String, String> encodedMap, final Collection<? extends EntityProperty<?, ?>> entityProperties) {
        final Map<String, Long> hashMap = new HashMap<>(this.snapshotStorage.get(id).orElse(Collections.emptyMap()));

        for (final EntityProperty<?, ?> entityProperty : entityProperties) {
            hashMap.put(entityProperty.getName(), hash(encodedMap.get(entityProperty.getName())));
        }

        this.snapshotStorage.put(id, hashMap);
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

    private static long hash(final String value) {
        if (value == null) {
            return 0L;
        }

        long hash = FNV_OFFSET;

        for (final byte character : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= character;
            hash *= FNV_PRIME;
        }

        return hash;
    }
}