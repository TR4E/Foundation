package me.trae.foundation.injector.core.resolver;

import me.trae.foundation.injector.api.annotation.Provider;
import me.trae.foundation.injector.api.exception.ComponentCreationException;
import me.trae.foundation.injector.core.application.ApplicationContext;
import me.trae.foundation.injector.core.container.ComponentContainer;
import me.trae.foundation.injector.core.resolver.abstracts.AbstractResolver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class ProviderResolver extends AbstractResolver {

    private final Map<Class<?>, Class<?>> ownerMap = new LinkedHashMap<>();

    private final ApplicationContext applicationContext;
    private final DependencyResolver dependencyResolver;

    public ProviderResolver(final ComponentContainer componentContainer, final ApplicationContext applicationContext, final DependencyResolver dependencyResolver, final List<Class<?>> pendingClassList) {
        super(componentContainer);

        this.applicationContext = applicationContext;
        this.dependencyResolver = dependencyResolver;

        for (final Class<?> pendingClass : pendingClassList) {
            for (final Method method : pendingClass.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Provider.class)) {
                    continue;
                }

                this.ownerMap.put(method.getReturnType(), pendingClass);
            }
        }
    }

    public Optional<Class<?>> findOwner(final Class<?> dependencyType) {
        return this.ownerMap.entrySet().stream()
                .filter(entry -> dependencyType.isAssignableFrom(entry.getKey()))
                .<Class<?>>map(Map.Entry::getValue)
                .findFirst();
    }

    public void provide(final Object instance, final Consumer<Class<?>> ensurer) {
        for (final Method method : instance.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Provider.class)) {
                continue;
            }

            this.invoke(instance, method, ensurer);
        }
    }

    private void invoke(final Object instance, final Method method, final Consumer<Class<?>> ensurer) {
        final String name = "%s#%s".formatted(method.getDeclaringClass().getName(), method.getName());

        final Class<?> providedType = method.getReturnType();
        if (providedType == void.class) {
            throw new ComponentCreationException("@Provider method %s must return a value".formatted(name));
        }

        final Object[] arguments = this.dependencyResolver.resolveArguments(method.getGenericParameterTypes(), ensurer);

        try {
            method.setAccessible(true);

            final Object provided = method.invoke(instance, arguments);
            if (provided == null) {
                throw new ComponentCreationException("@Provider method %s returned null".formatted(name));
            }

            this.getComponentContainer().register(providedType, provided);

            this.applicationContext.getComponentClassList().add(providedType);
        } catch (final ReflectiveOperationException exception) {
            throw new ComponentCreationException("Failed to invoke @Provider method %s".formatted(name), exception instanceof final InvocationTargetException invocationTargetException ? invocationTargetException.getCause() : exception);
        }
    }
}