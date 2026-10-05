package me.trae.foundation.database.api.property;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.SchemaException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@UtilityClass
public class EntityPropertyRegistry {

    private final Map<Class<?>, List<EntityProperty<?, ?>>> PROPERTY_MAP = new ConcurrentHashMap<>();

    public <E extends Entity, V> EntityProperty<E, V> register(final EntityProperty<E, V> entityProperty) {
        final List<EntityProperty<?, ?>> propertyList = PROPERTY_MAP.computeIfAbsent(entityProperty.getEntityType(), key -> new CopyOnWriteArrayList<>());

        if (propertyList.stream().anyMatch(registered -> registered.getName().equals(entityProperty.getName()))) {
            throw new SchemaException("Property %s is already registered for %s".formatted(entityProperty.getName(), entityProperty.getEntityType().getName()));
        }

        propertyList.add(entityProperty);

        return entityProperty;
    }

    public List<EntityProperty<?, ?>> getProperties(final Class<? extends Entity> entityType) {
        final List<EntityProperty<?, ?>> propertyList = new ArrayList<>();

        for (Class<?> type = entityType; type != null && type != Object.class; type = type.getSuperclass()) {
            propertyList.addAll(PROPERTY_MAP.getOrDefault(type, Collections.emptyList()));
        }

        return List.copyOf(propertyList);
    }
}