package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    public static <T> void runIfNotNull(final T object, final Consumer<T> consumer) {
        if (object == null) {
            return;
        }

        consumer.accept(object);
    }

    public static <T> void runOrElse(final T object, final Consumer<T> consumer, final Runnable runnable) {
        if (object == null) {
            runnable.run();
            return;
        }

        consumer.accept(object);
    }

    public static <T> T getOrElse(final T object, final Supplier<T> supplier) {
        if (object == null) {
            return supplier.get();
        }

        return object;
    }
}