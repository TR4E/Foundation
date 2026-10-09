package me.trae.foundation.injector.core.resolver;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.injector.api.exception.UnsupportedDependencyTypeException;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TypeResolver {

    public static Class<?> rawType(final Type type) {
        if (type instanceof final Class<?> clazz) {
            return clazz;
        }

        if (type instanceof final ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof final Class<?> rawType) {
            return rawType;
        }

        throw new UnsupportedDependencyTypeException("Unsupported dependency type %s".formatted(type.getTypeName()));
    }

    public static void validateConcrete(final Type type) {
        if (type instanceof final Class<?> clazz) {
            if (clazz.isArray()) {
                validateConcrete(clazz.getComponentType());
            }
            return;
        }

        if (type instanceof final ParameterizedType parameterizedType) {
            if (parameterizedType.getOwnerType() != null) {
                validateConcrete(parameterizedType.getOwnerType());
            }

            Arrays.stream(parameterizedType.getActualTypeArguments()).forEach(TypeResolver::validateConcrete);

            rawType(parameterizedType);
            return;
        }

        if (type instanceof TypeVariable<?> || type instanceof WildcardType || type instanceof GenericArrayType) {
            throw new UnsupportedDependencyTypeException("Unsupported dependency type %s".formatted(type.getTypeName()));
        }

        throw new UnsupportedDependencyTypeException("Unsupported dependency type %s".formatted(type.getTypeName()));
    }

    public static void validateDependencyType(final Type type) {
        if (type instanceof final Class<?> clazz) {
            if (clazz.isArray()) {
                validateDependencyType(clazz.getComponentType());
            }
            return;
        }

        if (type instanceof final ParameterizedType parameterizedType) {
            if (parameterizedType.getOwnerType() != null) {
                validateDependencyType(parameterizedType.getOwnerType());
            }

            Arrays.stream(parameterizedType.getActualTypeArguments()).forEach(TypeResolver::validateDependencyType);

            rawType(parameterizedType);
            return;
        }

        if (type instanceof final WildcardType wildcardType) {
            Arrays.stream(wildcardType.getUpperBounds()).forEach(TypeResolver::validateDependencyType);

            Arrays.stream(wildcardType.getLowerBounds()).forEach(TypeResolver::validateDependencyType);
            return;
        }

        if (type instanceof TypeVariable<?> || type instanceof GenericArrayType) {
            throw new UnsupportedDependencyTypeException("Unsupported dependency type %s".formatted(type.getTypeName()));
        }

        throw new UnsupportedDependencyTypeException("Unsupported dependency type %s".formatted(type.getTypeName()));
    }

    public static boolean isAssignable(final Type requestedType, final Type candidateType) {
        if (requestedType.equals(candidateType)) {
            return true;
        }

        final Class<?> requestedRawType = rawType(requestedType);
        final Class<?> candidateRawType = rawType(candidateType);

        if (!requestedRawType.isAssignableFrom(candidateRawType)) {
            return false;
        }

        if (!(requestedType instanceof final ParameterizedType requestedParameterizedType)) {
            return true;
        }

        final Type candidateView = asSupertype(candidateType, requestedRawType, new HashSet<>());

        if (!(candidateView instanceof final ParameterizedType candidateParameterizedType)) {
            return false;
        }

        return matchesTypeArguments(requestedParameterizedType, candidateParameterizedType);
    }

    private static boolean matchesTypeArguments(final ParameterizedType requestedType, final ParameterizedType candidateType) {
        if (!Objects.equals(requestedType.getOwnerType(), candidateType.getOwnerType())) {
            return false;
        }

        final Type[] requestedArgumentArray = requestedType.getActualTypeArguments();
        final Type[] candidateArgumentArray = candidateType.getActualTypeArguments();

        if (requestedArgumentArray.length != candidateArgumentArray.length) {
            return false;
        }

        for (int index = 0; index < requestedArgumentArray.length; index++) {
            if (!matchesTypeArgument(requestedArgumentArray[index], candidateArgumentArray[index])) {
                return false;
            }
        }

        return true;
    }

    private static boolean matchesTypeArgument(final Type requestedType, final Type candidateType) {
        if (requestedType instanceof final WildcardType wildcardType) {
            return matchesWildcard(wildcardType, candidateType);
        }

        if (requestedType instanceof final ParameterizedType requestedParameterizedType) {
            return candidateType instanceof final ParameterizedType candidateParameterizedType && Objects.equals(requestedParameterizedType.getRawType(), candidateParameterizedType.getRawType()) && matchesTypeArguments(requestedParameterizedType, candidateParameterizedType);
        }

        return requestedType.equals(candidateType);
    }

    private static boolean matchesWildcard(final WildcardType wildcardType, final Type candidateType) {
        try {
            for (final Type upperBound : wildcardType.getUpperBounds()) {
                if (!isAssignable(upperBound, candidateType)) {
                    return false;
                }
            }

            for (final Type lowerBound : wildcardType.getLowerBounds()) {
                if (!isAssignable(candidateType, lowerBound)) {
                    return false;
                }
            }

            return true;
        } catch (final UnsupportedDependencyTypeException exception) {
            return false;
        }
    }

    private static Type asSupertype(final Type sourceType, final Class<?> targetRawType, final Set<Type> visitedTypeSet) {
        if (!visitedTypeSet.add(sourceType)) {
            return null;
        }

        final Class<?> sourceRawType = rawType(sourceType);

        if (sourceRawType == targetRawType) {
            return sourceType;
        }

        if (!targetRawType.isAssignableFrom(sourceRawType)) {
            return null;
        }

        final Map<TypeVariable<?>, Type> typeArgumentMap = new HashMap<>();

        if (sourceType instanceof final ParameterizedType parameterizedType) {
            final TypeVariable<?>[] typeVariableArray = sourceRawType.getTypeParameters();

            final Type[] typeArgumentArray = parameterizedType.getActualTypeArguments();

            for (int index = 0; index < typeVariableArray.length; index++) {
                typeArgumentMap.put(typeVariableArray[index], typeArgumentArray[index]);
            }
        }

        for (final Type interfaceType : sourceRawType.getGenericInterfaces()) {
            final Type resolvedInterfaceType = resolveType(interfaceType, typeArgumentMap);

            final Type match = asSupertype(resolvedInterfaceType, targetRawType, visitedTypeSet);

            if (match != null) {
                return match;
            }
        }

        final Type superclassType = sourceRawType.getGenericSuperclass();
        if (superclassType == null) {
            return null;
        }

        return asSupertype(resolveType(superclassType, typeArgumentMap), targetRawType, visitedTypeSet);
    }

    private static Type resolveType(final Type type, final Map<TypeVariable<?>, Type> typeArgumentMap) {
        if (type instanceof final TypeVariable<?> typeVariable) {
            return typeArgumentMap.getOrDefault(typeVariable, typeVariable);
        }

        if (type instanceof final ParameterizedType parameterizedType) {
            final Type ownerType = parameterizedType.getOwnerType() == null ? null : resolveType(parameterizedType.getOwnerType(), typeArgumentMap);

            final Type[] argumentArray = Arrays.stream(parameterizedType.getActualTypeArguments())
                    .map(argument -> resolveType(argument, typeArgumentMap))
                    .toArray(Type[]::new);

            return new ResolvedParameterizedType(ownerType, parameterizedType.getRawType(), argumentArray);
        }

        if (type instanceof final GenericArrayType genericArrayType) {
            return new ResolvedGenericArrayType(resolveType(genericArrayType.getGenericComponentType(), typeArgumentMap));
        }

        return type;
    }

    @Getter
    private static final class ResolvedParameterizedType implements ParameterizedType {

        private final Type ownerType, rawType;
        private final Type[] actualTypeArguments;

        private ResolvedParameterizedType(final Type ownerType, final Type rawType, Type[] actualTypeArguments) {
            actualTypeArguments = actualTypeArguments.clone();

            this.ownerType = ownerType;
            this.rawType = rawType;
            this.actualTypeArguments = actualTypeArguments;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return this.actualTypeArguments.clone();
        }

        @Override
        public boolean equals(final Object object) {
            if (!(object instanceof final ParameterizedType parameterizedType)) {
                return false;
            }

            return Objects.equals(this.ownerType, parameterizedType.getOwnerType()) && Objects.equals(this.rawType, parameterizedType.getRawType()) && Arrays.equals(this.actualTypeArguments, parameterizedType.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(this.actualTypeArguments) ^ Objects.hashCode(this.ownerType) ^ Objects.hashCode(this.rawType);
        }

        @Override
        public String getTypeName() {
            return this.rawType.getTypeName() + Arrays.toString(this.actualTypeArguments);
        }
    }

    @AllArgsConstructor
    @Getter
    private static final class ResolvedGenericArrayType implements GenericArrayType {

        private final Type genericComponentType;

        @Override
        public String getTypeName() {
            return this.genericComponentType.getTypeName() + "[]";
        }

        @Override
        public boolean equals(final Object object) {
            return object instanceof final GenericArrayType genericArrayType && Objects.equals(this.genericComponentType, genericArrayType.getGenericComponentType());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.genericComponentType);
        }
    }
}