package me.trae.foundation.injector.core.container;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class AssignableCache {

    private final List<Object> list;
    private final Set<Object> set;
    private final Map<Class<?>, Object> map;

    public static AssignableCache of(final Class<?> type, final Collection<Object> instances) {
        final LinkedHashMap<Class<?>, Object> map = new LinkedHashMap<>();

        for (final Object instance : instances) {
            if (!type.isInstance(instance)) {
                continue;
            }

            map.put(instance.getClass(), instance);
        }

        return new AssignableCache(
                List.copyOf(map.values()),
                Collections.unmodifiableSet(new LinkedHashSet<>(map.values())),
                Collections.unmodifiableMap(map)
        );
    }
}