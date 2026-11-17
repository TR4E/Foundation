package me.trae.foundation.database.api.property;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.converter.ValueConverter;

import java.util.function.BiConsumer;
import java.util.function.Function;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class Property<EntityType, Value> {

    private final Class<EntityType> entityType;
    private final String column;
    private final Class<Value> type;
    private final Function<EntityType, Value> getter;
    private final BiConsumer<EntityType, Value> setter;
    private final ValueConverter<Value, ?> converter;
    private final boolean persistent;

    public static <EntityType, Value> Property<EntityType, Value> persistent(final Class<EntityType> entityType, final String column, final Class<Value> type, final Function<EntityType, Value> getter, final BiConsumer<EntityType, Value> setter, final ValueConverter<Value, ?> converter) {
        return PropertyRegistry.register(new Property<>(entityType, column, type, getter, setter, converter, true));
    }

    public static <EntityType, Value> Property<EntityType, Value> persistent(final Class<EntityType> entityType, final String column, final Class<Value> type, final Function<EntityType, Value> getter, final BiConsumer<EntityType, Value> setter) {
        return persistent(entityType, column, type, getter, setter, null);
    }

    public static <EntityType, Value> Property<EntityType, Value> memory(final Class<EntityType> entityType, final String column, final Class<Value> type, final Function<EntityType, Value> getter, final BiConsumer<EntityType, Value> setter, final ValueConverter<Value, ?> converter) {
        return PropertyRegistry.register(new Property<>(entityType, column, type, getter, setter, converter, false));
    }

    public static <EntityType, Value> Property<EntityType, Value> memory(final Class<EntityType> entityType, final String column, final Class<Value> type, final Function<EntityType, Value> getter, final BiConsumer<EntityType, Value> setter) {
        return memory(entityType, column, type, getter, setter, null);
    }

    public Value get(final EntityType entity) {
        return this.getter.apply(entity);
    }

    public void set(final EntityType entity, final Value value) {
        this.setter.accept(entity, value);
    }
}