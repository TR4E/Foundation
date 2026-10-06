package me.trae.foundation.database.storage.codec;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.property.converter.ValueConverter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@UtilityClass
public final class ValueCodec {

    private final Map<Class<?>, Function<String, Object>> PARSER_MAP = Map.ofEntries(
            Map.entry(String.class, raw -> raw),
            Map.entry(UUID.class, UUID::fromString),
            Map.entry(Long.class, Long::valueOf),
            Map.entry(Integer.class, Integer::valueOf),
            Map.entry(Short.class, Short::valueOf),
            Map.entry(Byte.class, Byte::valueOf),
            Map.entry(Double.class, Double::valueOf),
            Map.entry(Float.class, Float::valueOf),
            Map.entry(Boolean.class, Boolean::valueOf),
            Map.entry(Character.class, raw -> raw.charAt(0)),
            Map.entry(BigDecimal.class, BigDecimal::new),
            Map.entry(BigInteger.class, BigInteger::new),
            Map.entry(Instant.class, Instant::parse)
    );

    private final Map<Class<?>, Function<String, Object>> RESOLVED_PARSER_MAP = new ConcurrentHashMap<>();

    public <Value> String encode(final EntityProperty<?, Value> entityProperty, final Value value) {
        if (value == null) {
            return null;
        }

        return entityProperty.getValueConverter()
                .map(valueConverter -> serialize(valueConverter, value))
                .orElseGet(() -> stringify(value));
    }

    public <Value> Value decode(final EntityProperty<?, Value> entityProperty, final String raw) {
        if (raw == null) {
            return null;
        }

        return entityProperty.getValueConverter()
                .map(valueConverter -> deserialize(valueConverter, raw))
                .orElseGet(() -> entityProperty.getValueType().cast(parse(raw, entityProperty.getValueType())));
    }

    private <Value, Stored> String serialize(final ValueConverter<Value, Stored> valueConverter, final Value value) {
        return stringify(valueConverter.serialize(value));
    }

    private <Value, Stored> Value deserialize(final ValueConverter<Value, Stored> valueConverter, final String raw) {
        return valueConverter.deserialize(valueConverter.getStoredType().cast(parse(raw, valueConverter.getStoredType())));
    }

    private String stringify(final Object value) {
        if (value == null) {
            return null;
        }

        return value instanceof final Enum<?> enumValue ? enumValue.name() : String.valueOf(value);
    }

    private Object parse(final String raw, final Class<?> type) {
        return RESOLVED_PARSER_MAP.computeIfAbsent(type, ValueCodec::resolveParser).apply(raw);
    }

    private Function<String, Object> resolveParser(final Class<?> type) {
        final Function<String, Object> parser = PARSER_MAP.get(type);
        if (parser != null) {
            return parser;
        }

        if (type.isEnum()) {
            return raw -> Arrays.stream(type.getEnumConstants())
                    .filter(constant -> Enum.class.cast(constant).name().equals(raw))
                    .findFirst()
                    .orElseThrow(() -> new SchemaException("%s has no constant named %s".formatted(type.getName(), raw)));
        }

        try {
            final Method method = type.getMethod("valueOf", String.class);

            if (Modifier.isStatic(method.getModifiers()) && type.isAssignableFrom(method.getReturnType())) {
                return raw -> invoke(method, raw);
            }
        } catch (final NoSuchMethodException ignored) {
        }

        return _ -> {
            throw new SchemaException("%s has no string representation, register it with a ValueConverter".formatted(type.getName()));
        };
    }

    private Object invoke(final Method method, final String raw) {
        try {
            return method.invoke(null, raw);
        } catch (final ReflectiveOperationException exception) {
            throw new SchemaException("Failed to parse %s as %s".formatted(raw, method.getDeclaringClass().getName()), exception instanceof final InvocationTargetException invocationTargetException ? invocationTargetException.getCause() : exception);
        }
    }
}