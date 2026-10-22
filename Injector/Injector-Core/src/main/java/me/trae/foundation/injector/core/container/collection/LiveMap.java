package me.trae.foundation.injector.core.container.collection;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import me.trae.foundation.injector.core.container.ComponentContainer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@AllArgsConstructor
public final class LiveMap<T> extends AbstractMap<Class<? extends T>, T> {

    private final ComponentContainer componentContainer;
    private final Type type;

    @Override
    public T get(final Object key) {
        return Optional.ofNullable(this.getMap().get(key)).map(this.rawType()::cast).orElse(null);
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
        return Collections.unmodifiableSet(new AbstractSet<>() {

            @Override
            public @NonNull Iterator<Entry<Class<? extends T>, T>> iterator() {
                return LiveMap.this.getMap().entrySet().stream()
                        .map((final Entry<Class<?>, Object> mapEntry) -> Map.<Class<? extends T>, T>entry(
                                mapEntry.getKey().asSubclass(LiveMap.this.rawType()),
                                LiveMap.this.rawType().cast(mapEntry.getValue())
                        ))
                        .iterator();
            }

            @Override
            public int size() {
                return LiveMap.this.size();
            }
        });
    }

    private Map<Class<?>, Object> getMap() {
        return this.componentContainer.getAssignable(this.type).getMap();
    }

    @SuppressWarnings("unchecked")
    private Class<T> rawType() {
        return (Class<T>) (this.type instanceof final Class<?> clazz ? clazz : ((ParameterizedType) this.type).getRawType());
    }
}