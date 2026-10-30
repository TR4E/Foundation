package me.trae.foundation.database.api.property;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.property.converter.ValueConverter;

import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class EntityProperty<E extends Entity, Value> {

    private final Class<E> entityType;
    private final String name;
    private final Function<E, Value> getter;
    private final BiConsumer<E, Value> setter;
    private final Class<Value> valueType;
    private final ValueConverter<Value, ?> valueConverter;

    /**
     * Whether this property is stored in PostgreSQL. Non-persistent properties still synchronize through Redis.
     */
    private final boolean persistent;

    private final boolean caseInsensitive;

    public static <E extends Entity, Value> EntityProperty<E, Value> register(final Class<E> entityType, final String name, final Function<E, Value> getter, final BiConsumer<E, Value> setter, final Class<Value> valueType, final boolean persistent) {
        if (valueType.isPrimitive()) {
            throw new SchemaException("Property %s must use a wrapper type instead of %s".formatted(name, valueType.getName()));
        }

        return EntityPropertyRegistry.register(new EntityProperty<>(entityType, name, getter, setter, valueType, null, persistent, false));
    }

    public static <E extends Entity> EntityProperty<E, String> registerCaseInsensitive(final Class<E> entityType, final String name, final Function<E, String> getter, final BiConsumer<E, String> setter, final boolean persistent) {
        return EntityPropertyRegistry.register(new EntityProperty<>(entityType, name, getter, setter, String.class, null, persistent, true));
    }

    public static <E extends Entity, Value> EntityProperty<E, Value> registerWithConverter(final Class<E> entityType, final String name, final Function<E, Value> getter, final BiConsumer<E, Value> setter, final ValueConverter<Value, ?> valueConverter, final boolean persistent) {
        return EntityPropertyRegistry.register(new EntityProperty<>(entityType, name, getter, setter, valueConverter.getValueType(), valueConverter, persistent, false));
    }

    public Value getValue(final E entity) {
        return this.getter.apply(entity);
    }

    public void setValue(final E entity, final Value value) {
        this.setter.accept(entity, value);
    }

    public Optional<ValueConverter<Value, ?>> getValueConverter() {
        return Optional.ofNullable(this.valueConverter);
    }

    public Object normalizeIndexValue(final Object value) {
        return this.caseInsensitive && value != null ? String.class.cast(value).toLowerCase(Locale.ROOT) : value;
    }
}