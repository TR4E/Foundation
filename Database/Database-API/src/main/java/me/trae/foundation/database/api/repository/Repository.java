package me.trae.foundation.database.api.repository;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Repository<EntityType extends Entity> {

    Class<EntityType> getEntityType();

    String getTable();

    WriteResult apply(final Write write);

    default void apply(final List<Write> writes) {
        writes.forEach(this::apply);
    }

    default boolean claim(final String tenant) {
        return true;
    }

    default void verify() {
    }

    Optional<EntityType> findById(final UUID id);

    List<EntityType> find(final Query query);

    long count(final Query query);
}