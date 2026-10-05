package me.trae.foundation.injector.extensions.commons.resolver;

import lombok.experimental.UtilityClass;
import me.trae.foundation.injector.extensions.commons.exception.LifecycleException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

@UtilityClass
public class MethodResolver {

    public void invoke(final Object instance, final Class<? extends Annotation> annotation) {
        final Set<String> invokedSet = new HashSet<>();

        for (Class<?> type = instance.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (final Method method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(annotation) || !invokedSet.add(method.getName())) {
                    continue;
                }

                invokeMethod(instance, method, annotation);
            }
        }
    }

    private void invokeMethod(final Object instance, final Method method, final Class<? extends Annotation> annotation) {
        final String name = "%s#%s".formatted(method.getDeclaringClass().getName(), method.getName());

        if (method.getParameterCount() != 0) {
            throw new LifecycleException("@%s method %s must have no parameters".formatted(annotation.getSimpleName(), name));
        }

        try {
            method.setAccessible(true);

            method.invoke(instance);
        } catch (final ReflectiveOperationException exception) {
            throw new LifecycleException("Failed to invoke @%s method %s".formatted(annotation.getSimpleName(), name), exception instanceof final InvocationTargetException invocationTargetException ? invocationTargetException.getCause() : exception);
        }
    }
}