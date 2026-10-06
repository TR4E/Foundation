package me.trae.foundation.injector.core.resolver;

import me.trae.foundation.injector.api.exception.AmbiguousDependencyException;
import me.trae.foundation.injector.api.exception.MissingDependencyException;
import me.trae.foundation.injector.api.exception.UnsupportedDependencyTypeException;
import me.trae.foundation.injector.core.container.ComponentContainer;
import me.trae.foundation.injector.core.container.collection.LiveList;
import me.trae.foundation.injector.core.container.collection.LiveMap;
import me.trae.foundation.injector.core.container.collection.LiveSet;
import me.trae.foundation.injector.core.dependency.Dependency;
import me.trae.foundation.injector.core.dependency.DependencyKind;
import me.trae.foundation.injector.core.resolver.abstracts.AbstractResolver;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class DependencyResolver extends AbstractResolver {

    private static final Set<Class<?>> COLLECTION_TYPES = Set.of(List.class, Set.class, Map.class);

    public DependencyResolver(final ComponentContainer componentContainer) {
        super(componentContainer);
    }

    public Object[] resolveArguments(final Type[] parameterTypes, final Consumer<Class<?>> ensurer) {
        final List<Dependency> dependencyList = Arrays.stream(parameterTypes).map(this::parse).toList();

        dependencyList.stream()
                .filter(dependency -> dependency.getKind() == DependencyKind.SINGLE)
                .map(Dependency::getType)
                .forEach(ensurer);

        return dependencyList.stream().map(this::resolve).toArray();
    }

    public Dependency parse(final Type type) {
        if (type instanceof final Class<?> clazz && !clazz.isPrimitive() && !COLLECTION_TYPES.contains(clazz)) {
            return new Dependency(DependencyKind.SINGLE, clazz);
        }

        if (type instanceof final ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof final Class<?> rawType) {
            return this.parseParameterized(parameterizedType, rawType);
        }

        throw new UnsupportedDependencyTypeException("Unsupported dependency type %s".formatted(type.getTypeName()));
    }

    private Dependency parseParameterized(final ParameterizedType parameterizedType, final Class<?> rawType) {
        final Type[] arguments = parameterizedType.getActualTypeArguments();

        if (rawType == List.class && arguments[0] instanceof final Class<?> elementType) {
            return new Dependency(DependencyKind.LIST, elementType);
        }

        if (rawType == Set.class && arguments[0] instanceof final Class<?> elementType) {
            return new Dependency(DependencyKind.SET, elementType);
        }

        if (rawType == Map.class && arguments[0] instanceof final ParameterizedType keyType && keyType.getRawType() == Class.class && arguments[1] instanceof final Class<?> valueType) {
            return new Dependency(DependencyKind.MAP, valueType);
        }

        if (COLLECTION_TYPES.contains(rawType)) {
            throw new UnsupportedDependencyTypeException("Unsupported dependency type %s".formatted(parameterizedType.getTypeName()));
        }

        return new Dependency(DependencyKind.SINGLE, rawType);
    }

    private Object resolve(final Dependency dependency) {
        return switch (dependency.getKind()) {
            case SINGLE -> this.getComponentContainer().getInstance(dependency.getType()).orElseGet(() -> this.resolveAssignable(dependency.getType()));
            case LIST -> new LiveList<>(this.getComponentContainer(), dependency.getType());
            case SET -> new LiveSet<>(this.getComponentContainer(), dependency.getType());
            case MAP -> new LiveMap<>(this.getComponentContainer(), dependency.getType());
        };
    }

    private Object resolveAssignable(final Class<?> type) {
        final List<Object> assignableList = this.getComponentContainer().getAssignable(type).getList();

        if (assignableList.isEmpty()) {
            throw new MissingDependencyException("No component found for %s".formatted(type.getName()));
        }

        if (assignableList.size() > 1) {
            throw new AmbiguousDependencyException("Found %s components for %s".formatted(assignableList.size(), type.getName()));
        }

        return assignableList.getFirst();
    }
}