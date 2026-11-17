package me.trae.foundation.database.api.index;

import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@UtilityClass
public final class IndexRegistry {

    private final ConcurrentHashMap<Class<?>, List<Index<?, ?>>> INDEX_MAP = new ConcurrentHashMap<>();

    public <IndexType extends Index<?, ?>> IndexType register(final IndexType index) {
        INDEX_MAP.computeIfAbsent(index.getEntityType(), _ -> new CopyOnWriteArrayList<>()).add(index);

        return index;
    }

    public List<Index<?, ?>> getIndexes(final Class<?> entityType) {
        return INDEX_MAP.getOrDefault(entityType, Collections.emptyList());
    }
}