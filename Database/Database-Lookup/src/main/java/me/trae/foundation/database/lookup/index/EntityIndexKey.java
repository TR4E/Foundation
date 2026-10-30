package me.trae.foundation.database.lookup.index;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.repository.index.EntityIndex;
import me.trae.foundation.database.storage.codec.ValueCodec;

import java.util.List;

@UtilityClass
public final class EntityIndexKey {

    private String encode(final EntityProperty<?, ?> entityProperty, final Object value) {
        return encodeUnchecked(entityProperty, entityProperty.normalizeIndexValue(value));
    }

    public static String encodeValue(final EntityProperty<?, ?> entityProperty, final Object value) {
        return encode(entityProperty, value);
    }

    public static String encode(final EntityIndex<?> entityIndex, final List<Object> values) {
        if (values.size() != entityIndex.getProperties().size()) {
            throw new IllegalArgumentException("Index %s requires %s values".formatted(entityIndex.getName(), entityIndex.getProperties().size()));
        }

        final StringBuilder keyBuilder = new StringBuilder(entityIndex.getName());

        for (int index = 0; index < values.size(); index++) {
            final String value = encode(entityIndex.getProperties().get(index), values.get(index));
            keyBuilder.append('|').append(value.length()).append(':').append(value);
        }

        return keyBuilder.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String encodeUnchecked(final EntityProperty entityProperty, final Object value) {
        return ValueCodec.encode(entityProperty, value);
    }
}