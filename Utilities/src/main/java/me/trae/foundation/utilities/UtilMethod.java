package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@UtilityClass
public class UtilMethod {

    public static void invoke(final Object instance, final Method method, final Object... args) throws InvocationTargetException, IllegalAccessException {
        method.trySetAccessible();

        if (args != null && args.length > 0) {
            method.invoke(instance, args);
        } else {
            method.invoke(instance);
        }
    }

    public static void invoke(final Object instance, final Method method) throws InvocationTargetException, IllegalAccessException {
        invoke(instance, method, new Object[0]);
    }

    public static <T> T invoke(final Class<T> type, final Object instance, final Method method, final Object... args) throws InvocationTargetException, IllegalAccessException {
        method.trySetAccessible();

        final Object value = args != null && args.length > 0 ? method.invoke(instance, args) : method.invoke(instance);

        if (!type.isInstance(value)) {
            throw new IllegalStateException("Instance %s method %s returned value type %s, expected %s".formatted(instance == null ? method.getDeclaringClass().getName() : instance.getClass().getName(), method.getName(), value == null ? "null" : value.getClass().getName(), type.getName()));
        }

        return type.cast(value);
    }

    public static <T> T invoke(final Class<T> type, final Object instance, final Method method) throws InvocationTargetException, IllegalAccessException {
        return invoke(type, instance, method, new Object[0]);
    }
}