package me.trae.foundation.injector.core.container;

import me.trae.foundation.injector.api.exception.DuplicateComponentException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ComponentContainer {

    private final LinkedHashMap<Class<?>, Object> instanceMap = new LinkedHashMap<>();
    private final Map<Class<?>, AssignableCache> assignableCacheMap = new HashMap<>();

    public synchronized void register(final Class<?> type, final Object instance) {
        if (this.instanceMap.putIfAbsent(type, instance) != null) {
            throw new DuplicateComponentException("%s is already registered".formatted(type.getName()));
        }

        this.assignableCacheMap.clear();
    }

    public synchronized void unregister(final Class<?> type) {
        this.instanceMap.remove(type);
        this.assignableCacheMap.clear();
    }

    public synchronized boolean isRegistered(final Class<?> type) {
        return this.instanceMap.containsKey(type);
    }

    public synchronized Optional<Object> getInstance(final Class<?> type) {
        return Optional.ofNullable(this.instanceMap.get(type));
    }

    public synchronized AssignableCache getAssignable(final Class<?> type) {
        return this.assignableCacheMap.computeIfAbsent(type, key -> AssignableCache.of(key, this.instanceMap.values()));
    }
}