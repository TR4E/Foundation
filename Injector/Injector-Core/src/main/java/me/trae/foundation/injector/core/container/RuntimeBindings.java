package me.trae.foundation.injector.core.container;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;

public final class RuntimeBindings {

    private final LinkedHashMap<Type, ComponentRegistration> registrationMap = new LinkedHashMap<>();

    public synchronized <Component> void provide(final Class<Component> type, final Component instance) {
        this.registrationMap.put(type, new ComponentRegistration(type, type.cast(instance), null, null, Set.of()));
    }

    public synchronized Optional<ComponentRegistration> getRegistration(final Type type) {
        return Optional.ofNullable(this.registrationMap.get(type));
    }

    public synchronized void remove(final Type type) {
        this.registrationMap.remove(type);
    }
}