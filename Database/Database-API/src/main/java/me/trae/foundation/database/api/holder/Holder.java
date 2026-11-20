package me.trae.foundation.database.api.holder;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.index.GroupedIndex;
import me.trae.foundation.database.api.index.UniqueIndex;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.tier.Tier;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface Holder<EntityType extends Entity> {

    Optional<EntityType> get(final UUID id, final Set<Tier> tiers);

    Optional<EntityType> get(final UUID id);

    List<EntityType> getAll(final Collection<UUID> ids);

    <Value> Optional<EntityType> getByProperty(final Property<EntityType, Value> property, final Value value);

    <Value> Optional<EntityType> getByIndex(final UniqueIndex<EntityType, Value> index, final Value key);

    <Value> List<EntityType> getAllByIndex(final GroupedIndex<EntityType, Value> index, final Value key);

    List<EntityType> getHeld();

    void holdAll();

    void hold(final UUID id);

    void release(final UUID id);

    boolean holds(final UUID id);

    void save(final EntityType entity);

    <Value> void set(final EntityType entity, final Property<EntityType, Value> property, final Value value);

    long increment(final EntityType entity, final Property<EntityType, Long> property, final long delta);

    long decrement(final EntityType entity, final Property<EntityType, Long> property, final long delta);

    boolean decrementIfAtLeast(final EntityType entity, final Property<EntityType, Long> property, final long delta, final long floor);

    void delete(final EntityType entity);

    void reindex(final EntityType entity);
}