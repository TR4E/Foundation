package me.trae.foundation.injector.core.container.collection;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import me.trae.foundation.injector.core.container.ComponentContainer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.Iterator;

@AllArgsConstructor
public final class LiveList<T> extends AbstractList<T> {

    private final ComponentContainer componentContainer;
    private final Type type;

    @Override
    public T get(final int index) {
        return this.rawType().cast(this.componentContainer.getAssignable(this.type).getList().get(index));
    }

    @Override
    public int size() {
        return this.componentContainer.getAssignable(this.type).getList().size();
    }

    @Override
    public @NonNull Iterator<T> iterator() {
        return this.componentContainer.getAssignable(this.type).getList().stream().map(this.rawType()::cast).iterator();
    }

    @SuppressWarnings("unchecked")
    private Class<T> rawType() {
        return (Class<T>) (this.type instanceof final Class<?> clazz ? clazz : ((ParameterizedType) this.type).getRawType());
    }
}