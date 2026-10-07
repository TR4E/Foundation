package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;

import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

@UtilityClass
public class UtilClass {

    public static <T> T create(final Class<T> type, final Object... args) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        final Object[] arguments = args == null ? new Object[0] : args;

        for (final Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (isApplicable(constructor.getParameterTypes(), arguments)) {
                constructor.trySetAccessible();

                return type.cast(constructor.newInstance(arguments));
            }
        }

        throw new NoSuchMethodException("%s has no constructor accepting %d argument(s) of the given types".formatted(type.getName(), arguments.length));
    }

    public static <T> T create(final Class<T> type) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return create(type, new Object[0]);
    }

    public static String formatName(final String basePackage, final Class<?> type) {
        final String name = type.getName();

        if (name.startsWith(basePackage + ".")) {
            return name.substring(basePackage.length() + 1);
        }

        return name;
    }

    public static String formatName(final Class<?> type) {
        final String name = type.getName();

        final String[] parts = name.split("\\.");

        if (parts.length <= 3) {
            return name;
        }

        final String basePackage = String.join(".", Arrays.copyOfRange(parts, 0, 3));

        return formatName(basePackage, type);
    }

    private static boolean isApplicable(final Class<?>[] parameterTypes, final Object[] arguments) {
        if (parameterTypes.length != arguments.length) {
            return false;
        }

        for (int i = 0; i < parameterTypes.length; i++) {
            final Class<?> parameterType = parameterTypes[i];

            if (arguments[i] == null ? parameterType.isPrimitive() : !MethodType.methodType(parameterType).wrap().returnType().isInstance(arguments[i])) {
                return false;
            }
        }

        return true;
    }
}