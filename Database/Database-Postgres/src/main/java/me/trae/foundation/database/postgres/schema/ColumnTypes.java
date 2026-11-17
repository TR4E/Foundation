package me.trae.foundation.database.postgres.schema;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.property.Property;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@UtilityClass
public final class ColumnTypes {

    private final Map<Class<?>, String> TYPE_MAP = Map.ofEntries(
            Map.entry(UUID.class, "uuid"),
            Map.entry(String.class, "text"),
            Map.entry(Boolean.class, "boolean"),
            Map.entry(Short.class, "smallint"),
            Map.entry(Integer.class, "integer"),
            Map.entry(Long.class, "bigint"),
            Map.entry(Float.class, "real"),
            Map.entry(Double.class, "double precision"),
            Map.entry(Instant.class, "timestamptz"),
            Map.entry(byte[].class, "bytea")
    );

    public String get(final Property<?, ?> property) {
        final Class<?> type = property.getConverter() == null ? property.getType() : property.getConverter().getStoredType();

        if (type.isEnum()) {
            return "text";
        }

        final String columnType = TYPE_MAP.get(type);

        if (columnType == null) {
            throw new SchemaException("No column type for %s on column %s".formatted(type.getName(), property.getColumn()));
        }

        return columnType;
    }
}