package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

@UtilityClass
public class UtilJava {

    public static <T> T cast(final Class<T> type, final Object object) {
        if ((type != null && object != null) && type.isInstance(object)) {
            try {
                return type.cast(object);
            } catch (final Exception exception) {
                throw new IllegalStateException("Failed to cast object to %s".formatted(type.getName()), exception);
            }
        }

        return null;
    }

    public static <T extends Collection<?>> T createCollection(final T collection, final Consumer<T> consumer) {
        consumer.accept(collection);

        return collection;
    }

    public static <T extends Map<?, ?>> T createMap(final T map, final Consumer<T> consumer) {
        consumer.accept(map);

        return map;
    }

    public static <T extends Collection<?>> void updateCollection(final T collection, final Consumer<T> consumer) {
        consumer.accept(collection);
    }

    public static <T extends Map<?, ?>> void updateMap(final T map, final Consumer<T> consumer) {
        consumer.accept(map);
    }
}