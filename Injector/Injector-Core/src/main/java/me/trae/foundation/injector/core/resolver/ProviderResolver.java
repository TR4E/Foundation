package me.trae.foundation.injector.core.resolver;

import me.trae.foundation.injector.api.annotation.Provider;
import me.trae.foundation.injector.api.exception.AmbiguousDependencyException;
import me.trae.foundation.injector.api.exception.ComponentCreationException;
import me.trae.foundation.injector.api.exception.UnsupportedDependencyTypeException;
import me.trae.foundation.injector.core.application.ApplicationContext;
import me.trae.foundation.injector.core.container.ComponentContainer;
import me.trae.foundation.injector.core.extension.ExtensionRegistry;
import me.trae.foundation.injector.core.resolver.abstracts.AbstractResolver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class ProviderResolver extends AbstractResolver {

    private final Map<Type, Class<?>> ownerMap = new LinkedHashMap<>();
    private final Map<Type, Optional<Class<?>>> ownerCacheMap = new LinkedHashMap<>();

    private final ExtensionRegistry extensionRegistry;
    private final ApplicationContext applicationContext;
    private final DependencyResolver dependencyResolver;

    public ProviderResolver(final ComponentContainer componentContainer, final ExtensionRegistry extensionRegistry, final ApplicationContext applicationContext, final DependencyResolver dependencyResolver, final List<Class<?>> pendingClassList) {
        super(componentContainer);

        this.extensionRegistry = extensionRegistry;
        this.applicationContext = applicationContext;
        this.dependencyResolver = dependencyResolver;

        for (final Class<?> pendingClass : pendingClassList) {
            for (final Method method : pendingClass.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Provider.class)) {
                    continue;
                }

                final Type returnType = method.getGenericReturnType();

                this.validateProviderType(returnType, method);

                final Class<?> previous = this.ownerMap.putIfAbsent(returnType, pendingClass);

                if (previous != null && previous != pendingClass) {
                    throw new me.trae.foundation.injector.api.exception.DuplicateComponentException("Multiple @Provider methods return %s".formatted(returnType.getTypeName()));
                }
            }
        }
    }

    public Optional<Class<?>> findOwner(final Type dependencyType) {
        final Optional<Class<?>> exact = this.findExactOwner(dependencyType);

        if (exact.isPresent()) {
            return exact;
        }

        return this.ownerCacheMap.computeIfAbsent(dependencyType, type -> {
            final List<Map.Entry<Type, Class<?>>> providerList = this.ownerMap.entrySet().stream()
                    .filter(entry -> TypeResolver.isAssignable(type, entry.getKey()))
                    .toList();

            if (providerList.size() > 1) {
                throw new AmbiguousDependencyException("Found %s @Provider methods for %s".formatted(providerList.size(), type.getTypeName()));
            }

            if (providerList.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(providerList.getFirst().getValue());
        });
    }

    public Optional<Class<?>> findExactOwner(final Type dependencyType) {
        return Optional.ofNullable(this.ownerMap.get(dependencyType));
    }

    public void provide(final Object instance, final Type ownerType, final Consumer<Type> ensurer) {
        for (final Method method : instance.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Provider.class)) {
                continue;
            }

            this.invoke(instance, ownerType, method, ensurer);
        }
    }

    private void invoke(final Object instance, final Type ownerType, final Method method, final Consumer<Type> ensurer) {
        final String name = "%s#%s".formatted(method.getDeclaringClass().getName(), method.getName());

        final Type providedType = method.getGenericReturnType();

        if (method.getReturnType() == void.class) {
            throw new ComponentCreationException("@Provider method %s must return a value".formatted(name));
        }

        final LinkedHashSet<Type> dependencyTypeSet = new LinkedHashSet<>();

        final Object[] arguments = this.dependencyResolver.resolveArguments(method.getGenericParameterTypes(), ensurer, dependencyTypeSet);

        try {
            method.setAccessible(true);

            final Object provided = method.invoke(instance, arguments);
            if (provided == null) {
                throw new ComponentCreationException("@Provider method %s returned null".formatted(name));
            }

            this.getComponentContainer().register(providedType, provided, this.applicationContext, ownerType, dependencyTypeSet);

            this.applicationContext.getComponentTypeList().add(providedType);

            this.extensionRegistry.onComponentCreate(this.applicationContext, provided);
        } catch (final ReflectiveOperationException exception) {
            throw new ComponentCreationException("Failed to invoke @Provider method %s".formatted(name), exception instanceof final InvocationTargetException invocationTargetException ? invocationTargetException.getCause() : exception);
        }
    }

    private void validateProviderType(final Type type, final Method method) {
        if (method.getReturnType() == void.class || method.getReturnType().isPrimitive()) {
            throw new ComponentCreationException("@Provider method %s must return a reference type".formatted(method.getName()));
        }

        try {
            TypeResolver.validateConcrete(type);
        } catch (final UnsupportedDependencyTypeException exception) {
            throw new UnsupportedDependencyTypeException("Unsupported @Provider return type %s".formatted(type.getTypeName()));
        }
    }
}