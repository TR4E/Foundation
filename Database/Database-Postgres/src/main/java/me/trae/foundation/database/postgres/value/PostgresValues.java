package me.trae.foundation.database.postgres.value;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.exception.SchemaException;
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

    public <Value> Value read(final org.jooq.Record record, final String column, final Class<Value> type) {
        if (type.isEnum()) {
            final String name = record.get(DSL.field(DSL.name(column), String.class));

            return name == null ? null : type.cast(constant(type, name));
        }

        if (type == Instant.class) {
            final OffsetDateTime timestamp = record.get(DSL.field(DSL.name(column), OffsetDateTime.class));

            return timestamp == null ? null : type.cast(timestamp.toInstant());
        }

        if (type == BigInteger.class) {
            final BigDecimal number = record.get(DSL.field(DSL.name(column), BigDecimal.class));

            return number == null ? null : type.cast(number.toBigInteger());
        }

        return record.get(DSL.field(DSL.name(column), type));
    }

    private Object constant(final Class<?> type, final String name) {
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> Enum.class.cast(constant).name().equals(name))
                .findFirst()
                .orElseThrow(() -> new SchemaException("%s has no constant named %s".formatted(type.getName(), name)));
    }
}