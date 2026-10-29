package me.trae.foundation.injector.core.container.collection;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import me.trae.foundation.injector.core.container.ComponentContainer;

import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.Iterator;

@AllArgsConstructor
public final class LiveList<Component> extends AbstractList<Component> {

    private final ComponentContainer componentContainer;
    private final Type type;

    @Override
    @SuppressWarnings("unchecked")
    public Component get(final int index) {
        // AssignableCache already validates element types.
        return (Component) this.componentContainer.getAssignable(this.type).getList().get(index);
    }

    @Override
    public int size() {
        return this.componentContainer.getAssignable(this.type).getList().size();
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NonNull Iterator<Component> iterator() {
        // AssignableCache already validates element types; its immutable list preserves the iteration snapshot.
        return (Iterator<Component>) this.componentContainer.getAssignable(this.type).getList().iterator();
    }
}
