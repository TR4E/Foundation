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
}