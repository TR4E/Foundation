package me.trae.foundation.injector.core.container.collection;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import me.trae.foundation.injector.core.container.ComponentContainer;

import java.util.AbstractSet;
import java.util.Iterator;

@AllArgsConstructor
public final class LiveSet<T> extends AbstractSet<T> {

    private final ComponentContainer componentContainer;
    private final Class<T> type;

    @Override
    public boolean contains(final Object object) {
        return this.componentContainer.getAssignable(this.type).getSet().contains(object);
    }

    @Override
    public int size() {
        return this.componentContainer.getAssignable(this.type).getSet().size();
    }

    @Override
    public @NonNull Iterator<T> iterator() {
        final Iterator<Object> iterator = this.componentContainer.getAssignable(this.type).getSet().iterator();

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public T next() {
                return LiveSet.this.type.cast(iterator.next());
            }
        };
    }
}