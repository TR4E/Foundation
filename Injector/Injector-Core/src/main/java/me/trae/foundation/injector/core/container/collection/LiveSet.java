package me.trae.foundation.injector.core.container.collection;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import me.trae.foundation.injector.core.container.ComponentContainer;

import java.lang.reflect.Type;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;

@AllArgsConstructor
public final class LiveSet<Component> extends AbstractSet<Component> {

    private final ComponentContainer componentContainer;
    private final Type type;

    @Override
    public boolean contains(final Object object) {
        for (final Object instance : this.getList()) {
            if (instance == object) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int size() {
        return this.getList().size();
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NonNull Iterator<Component> iterator() {
        // AssignableCache already validates element types; its immutable list preserves the iteration snapshot.
        return (Iterator<Component>) (Iterator<?>) this.getList().iterator();
    }

    private List<Object> getList() {
        return this.componentContainer.getAssignable(this.type).getList();
    }
}
