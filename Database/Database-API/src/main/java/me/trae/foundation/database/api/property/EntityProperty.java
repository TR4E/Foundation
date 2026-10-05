package me.trae.foundation.database.api.property;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.property.converter.ValueConverter;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class EntityProperty<E extends Entity, Value> {

    private final Class<E> entityType;
    private final String name;
    private final Function<E, Value> getter;
    private final BiConsumer<E, Value> setter;
    private final Class<Value> valueType;
    private final ValueConverter<Value, ?> valueConverter;
    private final boolean persistent;

    public static <E extends Entity, V> EntityProperty<E, V> register(final Class<E> entityType, final String name, final Function<E, V> getter, final BiConsumer<E, V> setter, final Class<V> valueType, final boolean persistent) {
        if (valueType.isPrimitive()) {
            throw new SchemaException("Property %s must use a wrapper type instead of %s".formatted(name, valueType.getName()));
        }

        return EntityPropertyRegistry.register(new EntityProperty<>(entityType, name, getter, setter, valueType, null, persistent));
    }

    public static <E extends Entity, V> EntityProperty<E, V> registerWithConverter(final Class<E> entityType, final String name, final Function<E, V> getter, final BiConsumer<E, V> setter, final ValueConverter<V, ?> valueConverter, final boolean persistent) {
        return EntityPropertyRegistry.register(new EntityProperty<>(entityType, name, getter, setter, valueConverter.getValueType(), valueConverter, persistent));
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
}