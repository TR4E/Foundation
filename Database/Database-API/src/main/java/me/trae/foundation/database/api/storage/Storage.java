package me.trae.foundation.database.api.storage;

import java.util.Collection;
import java.util.Optional;

public interface Storage<Key, Value> {

    Namespace<Key, Value> getNamespace();

    Optional<Value> get(final Key key);

    void put(final Key key, final Value value);

    void remove(final Key key);

    boolean contains(final Key key);

    Collection<Value> values();

    void refresh(final Key key);
}