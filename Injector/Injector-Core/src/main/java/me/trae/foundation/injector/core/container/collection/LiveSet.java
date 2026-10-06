package me.trae.foundation.injector.core.container.collection;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import me.trae.foundation.injector.core.container.ComponentContainer;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;

@AllArgsConstructor
public final class LiveSet<T> extends AbstractSet<T> {

    private final ComponentContainer componentContainer;
    private final Class<T> type;

    @Override
    public boolean contains(final Object object) {
        return this.getList().stream().anyMatch(instance -> instance == object);
    }

    @Override
    public int size() {
        return this.getList().size();
    }

    @Override
    public @NonNull Iterator<T> iterator() {
        return this.getList().stream()
                .map(this.type::cast)
                .iterator();
    }

    private List<Object> getList() {
        return this.componentContainer.getAssignable(this.type).getList();
    }
}