package me.trae.foundation.database.holder.context;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.event.ChangeEvent;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.repository.Repository;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.index.IndexStores;
import me.trae.foundation.database.lookup.EntityLookup;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

@AllArgsConstructor
@Getter
public final class HolderContext<EntityType extends Entity> {

    private final Set<Tier> tiers;
    private final String table, tenant;
    private final Repository<EntityType> repository;
    private final LinkedHashMap<Tier, Storage<UUID, EntityType>> storageMap;
    private final EntityLookup<EntityType> lookup;
    private final IndexStores indexStores;
    private final ToLongFunction<Write> authority;
    private final Consumer<Write> persistence;
    private final BiConsumer<UUID, Consumer<ChangeEvent>> subscriber;
    private final Consumer<UUID> unsubscriber;
    private final BiFunction<Property<?, ?>, String, Object> decoder;
    private final ToLongFunction<UUID> version;
    private final Consumer<EntityType> refresher;
    private final Duration refreshInterval;
}