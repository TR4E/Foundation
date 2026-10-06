package me.trae.foundation.injector.core.container.collection;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import me.trae.foundation.injector.core.container.ComponentContainer;

import java.util.AbstractList;
import java.util.Iterator;

@AllArgsConstructor
public final class LiveList<T> extends AbstractList<T> {

    private final ComponentContainer componentContainer;
    private final Class<T> type;

    @Override
    public T get(final int index) {
        return this.type.cast(this.componentContainer.getAssignable(this.type).getList().get(index));
    }

    @Override
    public int size() {
        return this.componentContainer.getAssignable(this.type).getList().size();
    }

    @Override
    public @NonNull Iterator<T> iterator() {
        return this.componentContainer.getAssignable(this.type).getList().stream()
                .map(this.type::cast)
                .iterator();
    }
}