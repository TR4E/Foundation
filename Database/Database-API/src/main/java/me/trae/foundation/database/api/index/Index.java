package me.trae.foundation.database.api.index;

import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.tier.Tier;

import java.util.Set;

public sealed interface Index<EntityType, Value> permits UniqueIndex, GroupedIndex {

    Class<EntityType> getEntityType();

    String getName();

    Class<Value> getType();

    Set<Property<EntityType, ?>> getDependencies();

    Set<Tier> getTiers();

    Value extract(final EntityType entity);
}