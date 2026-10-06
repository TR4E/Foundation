package me.trae.foundation.injector.core.container;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class AssignableCache {

    private final List<Object> list;
    private final Map<Class<?>, Object> map;

    public static AssignableCache of(final Class<?> type, final Collection<Object> instances) {
        final Set<Object> seenSet = Collections.newSetFromMap(new IdentityHashMap<>());
        final List<Object> list = instances.stream().filter(type::isInstance).filter(seenSet::add).toList();

        final LinkedHashMap<Class<?>, Object> map = new LinkedHashMap<>();

        for (final Object instance : list) {
            map.putIfAbsent(instance.getClass(), instance);
        }

        return new AssignableCache(list, Collections.unmodifiableMap(map));
    }
}