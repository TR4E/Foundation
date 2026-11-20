package me.trae.foundation.database.index;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.index.Index;
import me.trae.foundation.database.api.index.UniqueIndex;
import me.trae.foundation.database.api.storage.Storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@AllArgsConstructor
public final class UniqueIndexStore {

    private final ConcurrentHashMap<UUID, Object> keyMap = new ConcurrentHashMap<>();

    @Getter
    private final UniqueIndex<?, ?> index;

    private final List<Storage<Object, UUID>> storages;

    public UUID lookup(final Object key) {
        for (final Storage<Object, UUID> storage : this.storages) {
            final UUID id = storage.get(key).orElse(null);

            if (id != null) {
                return id;
            }
        }

        return null;
    }

    public void index(final Entity entity) {
        final Object key = extract(this.index, entity);

        if (key == null) {
            this.unindex(entity.getId());
            return;
        }

        final Object previous = this.keyMap.put(entity.getId(), key);

        if (key.equals(previous)) {
            return;
        }

        if (previous != null) {
            this.discard(previous, entity.getId());
        }

        this.storages.forEach(storage -> storage.put(key, entity.getId()));
    }

    public void indexAll(final Collection<? extends Entity> entities) {
        final LinkedHashMap<Object, UUID> entries = new LinkedHashMap<>();

        entities.forEach(entity -> this.collect(entries, entity));

        if (entries.isEmpty()) {
            return;
        }

        this.storages.forEach(storage -> storage.putAll(entries));
    }

    public void unindex(final UUID id) {
        final Object key = this.keyMap.remove(id);

        if (key == null) {
            return;
        }

        this.discard(key, id);
    }

    public void refreshAll(final Collection<? extends Entity> entities) {
        final List<Object> keys = entities.stream()
                .map(entity -> extract(this.index, entity))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        if (keys.isEmpty()) {
            return;
        }

        this.storages.forEach(storage -> storage.refreshAll(keys));
    }

    public void refresh(final Entity entity) {
        final Object key = extract(this.index, entity);

        if (key == null) {
            return;
        }

        this.storages.forEach(storage -> storage.refresh(key));
    }

    private void discard(final Object key, final UUID id) {
        this.storages.forEach(storage -> storage.remove(key, id));
    }

    private void collect(final LinkedHashMap<Object, UUID> entries, final Entity entity) {
        final Object key = extract(this.index, entity);
        if (key == null) {
            return;
        }

        final Object previous = this.keyMap.put(entity.getId(), key);

        if (key.equals(previous)) {
            return;
        }

        if (previous != null) {
            this.discard(previous, entity.getId());
        }

        entries.put(key, entity.getId());
    }

    private static <Owner> Object extract(final Index<Owner, ?> index, final Object entity) {
        return index.extract(index.getEntityType().cast(entity));
    }
}