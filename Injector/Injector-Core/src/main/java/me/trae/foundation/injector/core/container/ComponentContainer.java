package me.trae.foundation.injector.core.container;

import me.trae.foundation.injector.api.exception.AmbiguousDependencyException;
import me.trae.foundation.injector.api.exception.DuplicateComponentException;
import me.trae.foundation.injector.core.application.ApplicationContext;
import me.trae.foundation.injector.core.resolver.TypeResolver;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ComponentContainer {

    private final LinkedHashMap<Type, ComponentRegistration> registrationMap = new LinkedHashMap<>();
    private final Map<Type, List<ComponentRegistration>> assignableRegistrationCacheMap = new HashMap<>();
    private final Map<Type, AssignableCache> assignableCacheMap = new HashMap<>();

    private final List<ApplicationContext> applicationContextList = new ArrayList<>();

    private final RuntimeBindings runtimeBindings = new RuntimeBindings();

    public synchronized void registerApplication(final ApplicationContext applicationContext) {
        this.applicationContextList.add(applicationContext);

        this.invalidateCaches();
    }

    public synchronized void unregisterApplication(final ApplicationContext applicationContext) {
        this.applicationContextList.remove(applicationContext);

        this.invalidateCaches();
    }

    public synchronized <Component> void provide(final Class<Component> type, final Component instance) {
        this.runtimeBindings.provide(type, instance);
    }

    public synchronized void unprovide(final Class<?> type) {
        this.runtimeBindings.remove(type);
    }

    public synchronized boolean isProvided(final Type type) {
        return this.runtimeBindings.getRegistration(type).isPresent();
    }

    public synchronized void register(final Type type, final Object instance) {
        this.register(type, instance, null, null, Set.of());
    }

    public synchronized void register(final Type type, final Object instance, final ApplicationContext applicationContext, final Type ownerType, final Collection<Type> dependencyTypeList) {
        final ComponentRegistration componentRegistration = new ComponentRegistration(
                type,
                instance,
                applicationContext,
                ownerType,
                Collections.unmodifiableSet(new LinkedHashSet<>(dependencyTypeList))
        );

        if (this.registrationMap.putIfAbsent(type, componentRegistration) != null) {
            throw new DuplicateComponentException("%s is already registered".formatted(type.getTypeName()));
        }

        this.invalidateCaches();
    }

    public synchronized void unregister(final Type type) {
        this.registrationMap.remove(type);

        this.runtimeBindings.remove(type);

        this.invalidateCaches();
    }

    public synchronized boolean isRegistered(final Type type) {
        return this.getInstance(type).isPresent();
    }

    public synchronized Optional<Object> getInstance(final Type type) {
        return this.getRegistration(type).map(ComponentRegistration::getInstance);
    }

    public synchronized Optional<Object> getAssignableInstance(final Type type) {
        final Optional<ComponentRegistration> exact = this.getRegistration(type);

        if (exact.isPresent()) {
            return exact.map(ComponentRegistration::getInstance);
        }

        final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        final List<ComponentRegistration> matching = this.getAssignableRegistrations(type).stream()
                .filter(registration -> seen.add(registration.getInstance()))
                .toList();

        if (matching.size() > 1) {
            throw new AmbiguousDependencyException("Found %s components for %s".formatted(matching.size(), type.getTypeName()));
        }

        return matching.stream().findFirst().map(ComponentRegistration::getInstance);
    }

    public synchronized Optional<ComponentRegistration> resolve(final Type type) {
        final Optional<ComponentRegistration> exact = this.getRegistration(type);

        if (exact.isPresent()) {
            return exact;
        }

        final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        final List<ComponentRegistration> matching = this.getAssignableRegistrations(type).stream()
                .filter(registration -> seen.add(registration.getInstance()))
                .toList();

        if (matching.size() > 1) {
            throw new AmbiguousDependencyException("Found %s components for %s".formatted(matching.size(), type.getTypeName()));
        }

        return matching.stream().findFirst();
    }

    public synchronized Optional<ComponentRegistration> getRegistration(final Type type) {
        return this.runtimeBindings.getRegistration(type).or(() -> Optional.ofNullable(this.registrationMap.get(type)));
    }

    public synchronized AssignableCache getAssignable(final Class<?> type) {
        return this.assignableCacheMap.computeIfAbsent(type, key -> AssignableCache.of(type, this.getAssignableRegistrations(key).stream().map(ComponentRegistration::getInstance).toList()));
    }

    public synchronized AssignableCache getAssignable(final Type type) {
        if (type instanceof final Class<?> clazz) {
            return this.getAssignable(clazz);
        }

        return this.assignableCacheMap.computeIfAbsent(type, key -> AssignableCache.of(TypeResolver.rawType(key), this.getAssignableRegistrations(key).stream().map(ComponentRegistration::getInstance).toList()));
    }

    public synchronized List<ComponentRegistration> getRemovalOrder(final Collection<Type> rootTypeList) {
        final Set<Type> removalTypeSet = new LinkedHashSet<>();

        rootTypeList.stream().filter(this.registrationMap::containsKey).forEach(removalTypeSet::add);

        boolean expanded;

        do {
            expanded = false;

            for (final ComponentRegistration componentRegistration : this.registrationMap.values()) {
                if (removalTypeSet.contains(componentRegistration.getType())) {
                    if (componentRegistration.getOwnerType() != null && removalTypeSet.add(componentRegistration.getOwnerType())) {
                        expanded = true;
                    }
                    continue;
                }

                final boolean ownerRemoved = componentRegistration.getOwnerType() != null && removalTypeSet.contains(componentRegistration.getOwnerType());

                final boolean dependencyRemoved = componentRegistration.getDependencyTypeSet().stream().anyMatch(removalTypeSet::contains);

                if (ownerRemoved || dependencyRemoved) {
                    removalTypeSet.add(componentRegistration.getType());

                    expanded = true;
                }
            }
        } while (expanded);

        final List<ComponentRegistration> registrationList = new ArrayList<>(this.registrationMap.values());

        Collections.reverse(registrationList);

        return registrationList.stream()
                .filter(registration -> removalTypeSet.contains(registration.getType()))
                .toList();
    }

    public synchronized List<ComponentRegistration> getAssignableRegistrations(final Type type) {
        return this.assignableRegistrationCacheMap.computeIfAbsent(type, requestedType -> this.registrationMap.values().stream()
                .filter(registration -> TypeResolver.isAssignable(requestedType, registration.getType()))
                .sorted(Comparator.comparingInt(registration -> this.applicationContextList.indexOf(registration.getApplicationContext())))
                .toList());
    }

    private void invalidateCaches() {
        this.assignableRegistrationCacheMap.clear();

        this.assignableCacheMap.clear();
    }
}
