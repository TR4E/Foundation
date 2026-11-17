package me.trae.foundation.database.api.property;

import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@UtilityClass
public final class PropertyRegistry {

    private final ConcurrentHashMap<Class<?>, List<Property<?, ?>>> PROPERTY_MAP = new ConcurrentHashMap<>();

    public <EntityType, Value> Property<EntityType, Value> register(final Property<EntityType, Value> property) {
        PROPERTY_MAP.computeIfAbsent(property.getEntityType(), _ -> new CopyOnWriteArrayList<>()).add(property);

        return property;
    }

    public List<Property<?, ?>> getProperties(final Class<?> entityType) {
        return PROPERTY_MAP.getOrDefault(entityType, Collections.emptyList());
    }
}