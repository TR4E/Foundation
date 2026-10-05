package me.trae.foundation.database.api.storage;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface Storage<Key, Value> {

    Optional<Value> get(final Key key);

    Map<Key, Value> getAll(final Collection<Key> keys);

    void put(final Key key, final Value value);

    void remove(final Key key);

    boolean contains(final Key key);
}