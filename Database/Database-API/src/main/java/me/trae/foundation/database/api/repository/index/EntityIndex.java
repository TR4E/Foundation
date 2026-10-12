package me.trae.foundation.database.api.repository.index;

import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.property.EntityProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public final class EntityIndex<E extends Entity> {

    private final String name;
    private final List<EntityProperty<? super E, ?>> properties;

    private EntityIndex(final String name, final List<EntityProperty<? super E, ?>> properties) {
        if (name == null || name.isBlank()) {
            throw new SchemaException("An entity index must have a name");
        }

        if (properties.isEmpty()) {
            throw new SchemaException("Entity index %s must contain at least one property".formatted(name));
        }

        if (properties.stream().map(EntityProperty::getName).distinct().count() != properties.size()) {
            throw new SchemaException("Entity index %s cannot contain the same property more than once".formatted(name));
        }

        this.name = name;
        this.properties = List.copyOf(properties);
    }

    @SafeVarargs
    public static <E extends Entity> EntityIndex<E> of(final String name, final EntityProperty<? super E, ?>... properties) {
        final List<EntityProperty<? super E, ?>> propertyList = new ArrayList<>();
        Collections.addAll(propertyList, properties);
        return new EntityIndex<>(name, propertyList);
    }

    public List<Object> getValues(final Object... values) {
        if (values.length != this.properties.size()) {
            throw new IllegalArgumentException("Index %s requires %s values".formatted(this.name, this.properties.size()));
        }

        final java.util.ArrayList<Object> normalizedList = new java.util.ArrayList<>(values.length);

        for (int index = 0; index < values.length; index++) {
            final EntityProperty<? super E, ?> property = this.properties.get(index);
            final Object value = values[index];

            if (value == null || !property.getValueType().isInstance(value)) {
                throw new IllegalArgumentException("Invalid value for index %s property %s".formatted(this.name, property.getName()));
            }

            normalizedList.add(property.normalizeIndexValue(value));
        }

        return List.copyOf(normalizedList);
    }

    public List<Object> getValues(final E entity) {
        return this.getValues(this.properties.stream().map(property -> property.getValue(entity)).toArray());
    }

    public boolean hasValues(final E entity) {
        return this.properties.stream().allMatch(property -> property.getValue(entity) != null);
    }
}