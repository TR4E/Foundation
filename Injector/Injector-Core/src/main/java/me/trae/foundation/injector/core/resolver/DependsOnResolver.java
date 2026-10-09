package me.trae.foundation.injector.core.resolver;

import me.trae.foundation.injector.api.annotation.DependsOn;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DependsOnResolver {

    public Set<Class<?>> resolve(final Class<?> type) {
        final Set<Class<?>> dependencySet = new LinkedHashSet<>();

        this.collect(type, dependencySet, new HashSet<>());

        return dependencySet;
    }

    private void collect(final Class<?> type, final Set<Class<?>> dependencySet, final Set<Class<?>> visitedSet) {
        if (type == null || type == Object.class || !visitedSet.add(type)) {
            return;
        }

        final DependsOn dependsOn = type.getDeclaredAnnotation(DependsOn.class);

        if (dependsOn != null) {
            dependencySet.addAll(List.of(dependsOn.value()));
        }

        this.collect(type.getSuperclass(), dependencySet, visitedSet);

        for (final Class<?> interfaceType : type.getInterfaces()) {
            this.collect(interfaceType, dependencySet, visitedSet);
        }
    }
}