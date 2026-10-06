package me.trae.foundation.injector.core.container.collection;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import me.trae.foundation.injector.core.container.ComponentContainer;

import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
public final class LiveMap<T> extends AbstractMap<Class<? extends T>, T> {

    private final ComponentContainer componentContainer;
    private final Class<T> type;

    @Override
    public T get(final Object key) {
        return Optional.ofNullable(this.getMap().get(key))
                .map(this.type::cast)
                .orElse(null);
    }

    @Override
    public boolean containsKey(final Object key) {
        return this.getMap().containsKey(key);
    }

    @Override
    public int size() {
        return this.getMap().size();
    }

    @Override
    public @NonNull Set<Entry<Class<? extends T>, T>> entrySet() {
        return this.getMap().entrySet().stream()
                .map(entry -> Map.<Class<? extends T>, T>entry(entry.getKey().asSubclass(this.type), this.type.cast(entry.getValue())))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<Class<?>, Object> getMap() {
        return this.componentContainer.getAssignable(this.type).getMap();
    }
}