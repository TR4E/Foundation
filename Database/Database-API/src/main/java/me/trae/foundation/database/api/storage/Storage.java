package me.trae.foundation.database.api.storage;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public interface Storage<Key, Value> {

    Namespace<Key, Value> getNamespace();

    Optional<Value> get(final Key key);

    void put(final Key key, final Value value);

    void remove(final Key key);

    boolean contains(final Key key);

    Collection<Value> values();

    void refresh(final Key key);

    default void putAll(final Map<Key, Value> values) {
        values.forEach(this::put);
    }

    default boolean remove(final Key key, final Value value) {
        if (this.get(key).filter(value::equals).isEmpty()) {
            return false;
        }

        this.remove(key);

        return true;
    }

    default void refreshAll(final Collection<Key> keys) {
        keys.forEach(this::refresh);
    }

    default void onEviction(final Consumer<Key> listener) {
    }
}