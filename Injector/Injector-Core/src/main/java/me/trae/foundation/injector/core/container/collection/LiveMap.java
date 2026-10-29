package me.trae.foundation.injector.core.container.collection;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import me.trae.foundation.injector.core.container.ComponentContainer;

import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@AllArgsConstructor
public final class LiveMap<Component> extends AbstractMap<Class<? extends Component>, Component> {

    private final Set<Entry<Class<? extends Component>, Component>> entrySet = Collections.unmodifiableSet(new AbstractSet<>() {

        @Override
        @SuppressWarnings("unchecked")
        public @NonNull Iterator<Entry<Class<? extends Component>, Component>> iterator() {
            // Cache keys are value classes; the unmodifiable map also protects entries from setValue().
            return (Iterator<Entry<Class<? extends Component>, Component>>) (Iterator<?>) LiveMap.this.getMap().entrySet().iterator();
        }

        @Override
        public int size() {
            return LiveMap.this.size();
        }
    });

    private final ComponentContainer componentContainer;
    private final Type type;

    @Override
    @SuppressWarnings("unchecked")
    public Component get(final Object key) {
        // AssignableCache already validates value types.
        return (Component) this.getMap().get(key);
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
    public @NonNull Set<Entry<Class<? extends Component>, Component>> entrySet() {
        return this.entrySet;
    }

    private Map<Class<?>, Object> getMap() {
        return this.componentContainer.getAssignable(this.type).getMap();
    }
}
