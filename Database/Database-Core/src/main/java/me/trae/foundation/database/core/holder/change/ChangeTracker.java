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

    public void snapshot(final E entity) {
        final Map<String, Long> hashMap = new HashMap<>();

        this.entityCodec.encode(entity).forEach((name, value) -> hashMap.put(name, hash(value)));

        this.snapshotStorage.put(entity.getId(), hashMap);
    }

    public void snapshotIfAbsent(final E entity) {
        if (!this.isTracked(entity.getId())) {
            this.snapshot(entity);
        }
    }

    public List<EntityProperty<?, ?>> diff(final E entity) {
        final Map<String, String> encodedMap = this.entityCodec.encode(entity);

        final Map<String, Long> snapshotMap = this.snapshotStorage.get(entity.getId()).orElse(Collections.emptyMap());

        return this.entityCodec.getProperties().stream()
                .filter(entityProperty -> !snapshotMap.containsKey(entityProperty.getName()) || !Objects.equals(snapshotMap.get(entityProperty.getName()), hash(encodedMap.get(entityProperty.getName()))))
                .toList();
    }

    public void commit(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties) {
        final Map<String, Long> hashMap = new HashMap<>(this.snapshotStorage.get(entity.getId()).orElse(Collections.emptyMap()));

        this.entityCodec.encode(entity, entityProperties).forEach((name, value) -> hashMap.put(name, hash(value)));

        this.snapshotStorage.put(entity.getId(), hashMap);
    }

    public void forget(final UUID id) {
        this.snapshotStorage.remove(id);
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