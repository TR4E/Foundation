package me.trae.foundation.database.core.value;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.property.converter.ValueConverter;

import java.util.Arrays;

@UtilityClass
public class ColumnValueMapper {

    public <Owner extends Entity, Value> Object readStored(final EntityProperty<Owner, Value> entityProperty, final Entity entity) {
        return toStored(entityProperty, entityProperty.getValue(entityProperty.getEntityType().cast(entity)));
    }

    public <Owner extends Entity, Value> void writeStored(final EntityProperty<Owner, Value> entityProperty, final Entity entity, final Object stored) {
        entityProperty.setValue(entityProperty.getEntityType().cast(entity), fromStored(entityProperty, stored));
    }

    public <Value> Object toStoredObject(final EntityProperty<?, Value> entityProperty, final Object value) {
        if (value == null) {
            return null;
        }

        return toStored(entityProperty, entityProperty.getValueType().cast(value));
    }

    public <Value> Object toStored(final EntityProperty<?, Value> entityProperty, final Value value) {
        if (value == null) {
            return null;
        }

        return entityProperty.getValueConverter()
                .map(valueConverter -> serialize(valueConverter, value))
                .orElseGet(() -> value instanceof final Enum<?> enumValue ? enumValue.name() : value);
    }

    public <Value> Value fromStored(final EntityProperty<?, Value> entityProperty, final Object stored) {
        if (stored == null) {
            return null;
        }

        return entityProperty.getValueConverter()
                .map(valueConverter -> deserialize(valueConverter, stored))
                .orElseGet(() -> fromRaw(entityProperty.getValueType(), stored));
    }

    private <Value, Stored> Object serialize(final ValueConverter<Value, Stored> valueConverter, final Value value) {
        return valueConverter.serialize(value);
    }

    private <Value, Stored> Value deserialize(final ValueConverter<Value, Stored> valueConverter, final Object stored) {
        return valueConverter.deserialize(valueConverter.getStoredType().cast(stored));
    }

    private <Value> Value fromRaw(final Class<Value> valueType, final Object stored) {
        if (!valueType.isEnum()) {
            return valueType.cast(stored);
        }

        return Arrays.stream(valueType.getEnumConstants())
                .filter(constant -> Enum.class.cast(constant).name().equals(stored.toString()))
                .findFirst()
                .orElseThrow(() -> new SchemaException("%s has no constant named %s".formatted(valueType.getName(), stored)));
    }
}