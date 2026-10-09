package me.trae.foundation.injector.core.resolver;

import me.trae.foundation.injector.api.exception.MissingDependencyException;
import me.trae.foundation.injector.api.exception.UnsupportedDependencyTypeException;
import me.trae.foundation.injector.core.container.ComponentContainer;
import me.trae.foundation.injector.core.container.ComponentRegistration;
import me.trae.foundation.injector.core.container.collection.LiveList;
import me.trae.foundation.injector.core.container.collection.LiveMap;
import me.trae.foundation.injector.core.container.collection.LiveSet;
import me.trae.foundation.injector.core.dependency.Dependency;
import me.trae.foundation.injector.core.dependency.DependencyKind;
import me.trae.foundation.injector.core.resolver.abstracts.AbstractResolver;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class DependencyResolver extends AbstractResolver {

    private static final Set<Class<?>> COLLECTION_TYPES = Set.of(List.class, Set.class, Map.class);

    public DependencyResolver(final ComponentContainer componentContainer) {
        super(componentContainer);
    }

    public Object[] resolveArguments(final Type[] parameterTypes, final Consumer<Type> ensurer, final Collection<Type> dependencyTypeSet) {
        final List<Dependency> dependencyList = Arrays.stream(parameterTypes).map(this::parse).toList();

        dependencyList.stream()
                .filter(dependency -> dependency.getKind() == DependencyKind.SINGLE)
                .map(Dependency::getType)
                .forEach(ensurer);

        return dependencyList.stream().map(dependency -> this.resolve(dependency, dependencyTypeSet)).toArray();
    }

    public Dependency parse(final Type type) {
        if (type instanceof final Class<?> clazz && !clazz.isPrimitive() && !COLLECTION_TYPES.contains(clazz)) {
            return new Dependency(DependencyKind.SINGLE, clazz);
        }

        if (type instanceof final ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof final Class<?> rawType) {
            if (COLLECTION_TYPES.contains(rawType)) {
                return this.parseParameterized(parameterizedType, rawType);
            }

            TypeResolver.validateDependencyType(parameterizedType);

            return new Dependency(DependencyKind.SINGLE, parameterizedType);
        }

        throw new UnsupportedDependencyTypeException("Unsupported dependency type %s".formatted(type.getTypeName()));
    }

    private Dependency parseParameterized(final ParameterizedType parameterizedType, final Class<?> rawType) {
        final Type[] arguments = parameterizedType.getActualTypeArguments();

        if (rawType == List.class && arguments.length == 1) {
            this.validateType(arguments[0]);

            return new Dependency(DependencyKind.LIST, arguments[0]);
        }

        if (rawType == Set.class && arguments.length == 1) {
            this.validateType(arguments[0]);

            return new Dependency(DependencyKind.SET, arguments[0]);
        }

        if (rawType == Map.class && arguments.length == 2 && arguments[0] instanceof final ParameterizedType keyType && keyType.getRawType() == Class.class) {
            this.validateMapKey(keyType);

            this.validateType(arguments[1]);

            return new Dependency(DependencyKind.MAP, arguments[1]);
        }

        if (COLLECTION_TYPES.contains(rawType)) {
            throw new UnsupportedDependencyTypeException("Unsupported dependency type %s".formatted(parameterizedType.getTypeName()));
        }

        return new Dependency(DependencyKind.SINGLE, parameterizedType);
    }

    private void validateType(final Type type) {
        if (type instanceof final Class<?> clazz && clazz.isArray() && clazz.getComponentType().isPrimitive()) {
            throw new UnsupportedDependencyTypeException("Unsupported dependency type %s".formatted(type.getTypeName()));
        }

        TypeResolver.validateDependencyType(type);
    }

    private void validateMapKey(final ParameterizedType keyType) {
        final Type[] arguments = keyType.getActualTypeArguments();

        if (arguments.length != 1) {
            throw new UnsupportedDependencyTypeException("Unsupported dependency type %s".formatted(keyType.getTypeName()));
        }

        final Type argument = arguments[0];

        if (argument instanceof final WildcardType wildcardType) {
            Arrays.stream(wildcardType.getUpperBounds()).forEach(bound -> this.validateMapKeyBound(bound, keyType));

            Arrays.stream(wildcardType.getLowerBounds()).forEach(bound -> this.validateMapKeyBound(bound, keyType));
        } else if (!(argument instanceof Class<?>)) {
            throw new UnsupportedDependencyTypeException("Unsupported dependency type %s".formatted(keyType.getTypeName()));
        }
    }

    private void validateMapKeyBound(final Type bound, final ParameterizedType keyType) {
        try {
            TypeResolver.validateDependencyType(bound);
        } catch (final UnsupportedDependencyTypeException exception) {
            throw new UnsupportedDependencyTypeException("Unsupported dependency type %s".formatted(keyType.getTypeName()));
        }
    }

    private Object resolve(final Dependency dependency, final Collection<Type> dependencyTypeSet) {
        return switch (dependency.getKind()) {
            case SINGLE -> this.resolveSingle(dependency.getType(), dependencyTypeSet);
            case LIST -> new LiveList<>(this.getComponentContainer(), dependency.getType());
            case SET -> new LiveSet<>(this.getComponentContainer(), dependency.getType());
            case MAP -> new LiveMap<>(this.getComponentContainer(), dependency.getType());
        };
    }

    private Object resolveSingle(final Type type, final Collection<Type> dependencyTypeSet) {
        final ComponentRegistration componentRegistration = this.getComponentContainer().resolve(type).orElseThrow(() -> new MissingDependencyException("No component found for %s".formatted(type.getTypeName())));

        dependencyTypeSet.add(componentRegistration.getType());

        return componentRegistration.getInstance();
    }
}