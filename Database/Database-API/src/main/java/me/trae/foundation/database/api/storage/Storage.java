package me.trae.foundation.database.api.storage;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface Storage<Key, Value> {

    void put(final Key key, final Value value);

    void remove(final Key key);

    Optional<Value> get(final Key key);

    Map<Key, Value> getAll(final Collection<Key> keys);

    boolean contains(final Key key);
}