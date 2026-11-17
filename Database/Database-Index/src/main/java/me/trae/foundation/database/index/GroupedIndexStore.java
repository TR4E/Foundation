package me.trae.foundation.database.index;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.index.GroupedIndex;
import me.trae.foundation.database.api.index.Index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor
public final class GroupedIndexStore {

    private final ConcurrentHashMap<Object, Set<UUID>> idMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Object> keyMap = new ConcurrentHashMap<>();

    @Getter
    private final GroupedIndex<?, ?> index;

    public List<UUID> lookup(final Object key) {
        return new ArrayList<>(this.idMap.getOrDefault(key, Collections.emptySet()));
    }

    public void index(final Entity entity) {
        final Object key = extract(this.index, entity);
        final Object previous = this.keyMap.put(entity.getId(), key);

        if (previous != null && !previous.equals(key)) {
            this.discard(previous, entity.getId());
        }

        this.idMap.computeIfAbsent(key, _ -> ConcurrentHashMap.newKeySet()).add(entity.getId());
    }

    public void unindex(final UUID id) {
        final Object key = this.keyMap.remove(id);

        if (key == null) {
            return;
        }

        this.discard(key, id);
    }

    private void discard(final Object key, final UUID id) {
        this.idMap.computeIfPresent(key, (_, ids) -> {
            ids.remove(id);

            return ids.isEmpty() ? null : ids;
        });
    }

    private static <Owner> Object extract(final Index<Owner, ?> index, final Object entity) {
        return index.extract(index.getEntityType().cast(entity));
    }
}