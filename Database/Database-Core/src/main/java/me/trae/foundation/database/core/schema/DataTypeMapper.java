package me.trae.foundation.database.core.schema;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.property.converter.ValueConverter;
import org.jooq.DataType;
import org.jooq.JSONB;
import org.jooq.impl.SQLDataType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@UtilityClass
public class DataTypeMapper {

    private final Map<Class<?>, DataType<?>> DATA_TYPE_MAP = Map.ofEntries(
            Map.entry(String.class, SQLDataType.VARCHAR),
            Map.entry(UUID.class, SQLDataType.UUID),
            Map.entry(Long.class, SQLDataType.BIGINT),
            Map.entry(Integer.class, SQLDataType.INTEGER),
            Map.entry(Short.class, SQLDataType.SMALLINT),
            Map.entry(Byte.class, SQLDataType.TINYINT),
            Map.entry(Double.class, SQLDataType.DOUBLE),
            Map.entry(Float.class, SQLDataType.REAL),
            Map.entry(Boolean.class, SQLDataType.BOOLEAN),
            Map.entry(BigDecimal.class, SQLDataType.NUMERIC),
            Map.entry(BigInteger.class, SQLDataType.DECIMAL_INTEGER),
            Map.entry(Instant.class, SQLDataType.INSTANT),
            Map.entry(JSONB.class, SQLDataType.JSONB),
            Map.entry(byte[].class, SQLDataType.BLOB)
    );

    public DataType<?> getDataType(final EntityProperty<?, ?> entityProperty) {
        final Class<?> storedType = getStoredType(entityProperty);

        if (storedType.isEnum()) {
            return SQLDataType.VARCHAR.nullable(true);
        }

        final DataType<?> dataType = DATA_TYPE_MAP.get(storedType);
        if (dataType == null) {
            throw new SchemaException("Property %s has no column type for %s, register it with a ValueConverter".formatted(entityProperty.getName(), storedType.getName()));
        }

        return dataType.nullable(true);
    }

    public Class<?> getStoredType(final EntityProperty<?, ?> entityProperty) {
        return entityProperty.getValueConverter()
                .<Class<?>>map(ValueConverter::getStoredType)
                .orElse(entityProperty.getValueType());
    }
}