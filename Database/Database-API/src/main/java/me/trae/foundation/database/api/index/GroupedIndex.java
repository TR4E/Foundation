package me.trae.foundation.database.api.index;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.tier.Tier;

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class GroupedIndex<EntityType, Value> implements Index<EntityType, Value> {

    private final Class<EntityType> entityType;
    private final String name;
    private final Class<Value> type;
    private final Function<EntityType, Value> extractor;
    private final Set<Property<EntityType, ?>> dependencies;
    private final Set<Tier> tiers;

    public static <EntityType, Value> GroupedIndex<EntityType, Value> of(final String name, final Property<EntityType, Value> property, final Set<Tier> tiers) {
        return IndexRegistry.register(new GroupedIndex<>(property.getEntityType(), name, property.getType(), property::get, Set.of(property), tiers));
    }

    public static <EntityType, Value> GroupedIndex<EntityType, Value> of(final String name, final Property<EntityType, Value> property) {
        return of(name, property, Collections.emptySet());
    }

    public static <EntityType, Value> GroupedIndex<EntityType, Value> derived(final String name, final Class<EntityType> entityType, final Class<Value> type, final Function<EntityType, Value> extractor, final Set<Property<EntityType, ?>> dependencies, final Set<Tier> tiers) {
        return IndexRegistry.register(new GroupedIndex<>(entityType, name, type, extractor, dependencies, tiers));
    }

    public static <EntityType, Value> GroupedIndex<EntityType, Value> derived(final String name, final Class<EntityType> entityType, final Class<Value> type, final Function<EntityType, Value> extractor, final Set<Property<EntityType, ?>> dependencies) {
        return derived(name, entityType, type, extractor, dependencies, Collections.emptySet());
    }

    @Override
    public Value extract(final EntityType entity) {
        return this.extractor.apply(entity);
    }
}