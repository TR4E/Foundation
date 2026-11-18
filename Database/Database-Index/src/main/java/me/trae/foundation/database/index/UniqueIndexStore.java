package me.trae.foundation.database.index;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.index.Index;
import me.trae.foundation.database.api.index.UniqueIndex;
import me.trae.foundation.database.api.storage.Storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor
public final class UniqueIndexStore {

    private final ConcurrentHashMap<UUID, Object> keyMap = new ConcurrentHashMap<>();

    @Getter
    private final UniqueIndex<?, ?> index;

    private final List<Storage<Object, UUID>> storages;

    public Optional<UUID> lookup(final Object key) {
        return this.storages.stream()
                .map(storage -> storage.get(key))
                .flatMap(Optional::stream)
                .findFirst();
    }

    public void index(final Entity entity) {
        final Object key = extract(this.index, entity);

        if (key == null) {
            this.unindex(entity.getId());
            return;
        }

        final Object previous = this.keyMap.put(entity.getId(), key);

        if (previous != null && !previous.equals(key)) {
            this.storages.forEach(storage -> storage.remove(previous));
        }

        this.storages.forEach(storage -> storage.put(key, entity.getId()));
    }

    public void unindex(final UUID id) {
        final Object key = this.keyMap.remove(id);

        if (key == null) {
            return;
        }

        this.storages.forEach(storage -> storage.remove(key));
    }

    public void refresh(final Entity entity) {
        final Object key = extract(this.index, entity);

        if (key == null) {
            return;
        }

        this.storages.forEach(storage -> storage.refresh(key));
    }

    private static <Owner> Object extract(final Index<Owner, ?> index, final Object entity) {
        return index.extract(index.getEntityType().cast(entity));
    }
}