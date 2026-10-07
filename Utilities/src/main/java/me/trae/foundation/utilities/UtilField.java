package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;

@UtilityClass
public class UtilField {

    public static void set(final Object instance, final Field field, final Object value) throws IllegalAccessException {
        field.trySetAccessible();

        field.set(instance, value);
    }

    public static <T> T get(final Class<T> type, final Object instance, final Field field) throws IllegalAccessException {
        field.trySetAccessible();

        final Object value = field.get(instance);

        if (!type.isInstance(value)) {
            throw new IllegalStateException("Instance %s field %s has value type %s, expected %s".formatted(instance == null ? field.getDeclaringClass().getName() : instance.getClass().getName(), field.getName(), value == null ? "null" : value.getClass().getName(), type.getName()));
        }

        return type.cast(value);
    }
}