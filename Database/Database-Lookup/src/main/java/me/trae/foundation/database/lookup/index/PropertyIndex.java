package me.trae.foundation.database.lookup.index;

import java.util.Optional;
import java.util.UUID;

public interface PropertyIndex<Value> {

    Optional<UUID> find(final Value value);

    void put(final Value value, final UUID id);

    void remove(final Value value);
}