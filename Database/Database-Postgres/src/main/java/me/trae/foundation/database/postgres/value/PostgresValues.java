package me.trae.foundation.database.postgres.value;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.exception.SchemaException;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

@UtilityClass
public final class PostgresValues {

    public Object write(final Object value) {
        if (value instanceof final Enum<?> enumValue) {
            return enumValue.name();
        }

        if (value instanceof final Instant instant) {
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }

        return value;
    }

    public <Value> Field<?> field(final String column, final Class<Value> type) {
        if (type.isEnum()) {
            return DSL.field(DSL.name(column), String.class);
        }

        if (type == Instant.class) {
            return DSL.field(DSL.name(column), OffsetDateTime.class);
        }

        if (type == BigInteger.class) {
            return DSL.field(DSL.name(column), BigDecimal.class);
        }

        return DSL.field(DSL.name(column), type);
    }

    public <Value> Value read(final org.jooq.Record record, final Field<?> field, final Class<Value> type) {
        final Object value = record.get(field);

        if (value == null) {
            return null;
        }

        if (type.isEnum()) {
            return type.cast(constant(type, String.class.cast(value)));
        }

        if (type == Instant.class) {
            return type.cast(OffsetDateTime.class.cast(value).toInstant());
        }

        if (type == BigInteger.class) {
            return type.cast(BigDecimal.class.cast(value).toBigInteger());
        }

        return type.cast(value);
    }

    public <Value> Value read(final org.jooq.Record record, final String column, final Class<Value> type) {
        return read(record, field(column, type), type);
    }

    private Object constant(final Class<?> type, final String name) {
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> Enum.class.cast(constant).name().equals(name))
                .findFirst()
                .orElseThrow(() -> new SchemaException("%s has no constant named %s".formatted(type.getName(), name)));
    }
}