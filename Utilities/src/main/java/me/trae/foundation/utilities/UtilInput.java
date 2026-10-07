package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

@UtilityClass
public class UtilInput {

    private static final ClassValue<Executable> PARSER_CLASS_VALUE = new ClassValue<>() {
        @Override
        protected Executable computeValue(final Class<?> type) {
            try {
                final Method method = type.getMethod("valueOf", String.class);

                if (Modifier.isStatic(method.getModifiers()) && type.isAssignableFrom(method.getReturnType())) {
                    method.trySetAccessible();

                    return method;
                }
            } catch (final NoSuchMethodException ignored) {
            }

            try {
                final Constructor<?> constructor = type.getConstructor(String.class);

                constructor.trySetAccessible();

                return constructor;
            } catch (final NoSuchMethodException exception) {
                throw new IllegalArgumentException("%s has no valueOf(String) method or String constructor".formatted(type.getName()), exception);
            }
        }
    };

    public static <T> Optional<T> getInput(final Class<T> type, final String input) {
        try {
            return Optional.of(type.cast(switch (PARSER_CLASS_VALUE.get(type)) {
                case final Method method -> method.invoke(null, input);
                case final Constructor<?> constructor -> constructor.newInstance(input);
            })).filter(value -> isValid(value, input));
        } catch (final Exception ignored) {
        }

        return Optional.empty();
    }

    public static <T extends Number & Comparable<T>> Optional<T> getNumber(final Class<T> clazz, final T minimumValue, final T maximumValue, final String input) {
        return getInput(clazz, input).filter(number -> number.compareTo(minimumValue) >= 0 && number.compareTo(maximumValue) <= 0);
    }

    private static boolean isValid(final Object value, final String input) {
        return switch (value) {
            case final Boolean parsed -> parsed.toString().equalsIgnoreCase(input);
            case final Double parsed -> Double.isFinite(parsed);
            case final Float parsed -> Float.isFinite(parsed);
            default -> true;
        };
    }
}