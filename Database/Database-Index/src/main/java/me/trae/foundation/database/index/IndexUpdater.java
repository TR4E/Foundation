package me.trae.foundation.database.index;

import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.index.Index;
import me.trae.foundation.database.api.property.Property;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.UUID;

@Getter
public final class IndexUpdater {

    private final IdentityHashMap<Property<?, ?>, List<UniqueIndexStore>> uniqueMap = new IdentityHashMap<>();
    private final IdentityHashMap<Property<?, ?>, List<GroupedIndexStore>> groupedMap = new IdentityHashMap<>();

    private final List<UniqueIndexStore> wildcardUnique = new ArrayList<>();
    private final List<GroupedIndexStore> wildcardGrouped = new ArrayList<>();

    private final IndexStores stores;

    public IndexUpdater(final IndexStores stores) {
        this.stores = stores;

        stores.getUniqueStores().forEach(store -> this.map(this.uniqueMap, this.wildcardUnique, store.getIndex(), store));
        stores.getGroupedStores().forEach(store -> this.map(this.groupedMap, this.wildcardGrouped, store.getIndex(), store));
    }

    public void index(final Entity entity, final Property<?, ?> property) {
        this.unique(property).forEach(store -> store.index(entity));
        this.grouped(property).forEach(store -> store.index(entity));
    }

    public void index(final Entity entity) {
        this.stores.getUniqueStores().forEach(store -> store.index(entity));
        this.stores.getGroupedStores().forEach(store -> store.index(entity));
    }

    public void index(final Collection<? extends Entity> entities) {
        this.stores.getUniqueStores().forEach(store -> store.indexAll(entities));
        this.stores.getGroupedStores().forEach(store -> entities.forEach(store::index));
    }

    public void unindex(final UUID id) {
        this.stores.getUniqueStores().forEach(store -> store.unindex(id));
        this.stores.getGroupedStores().forEach(store -> store.unindex(id));
    }

    public void refresh(final Collection<? extends Entity> entities) {
        this.stores.getUniqueStores().forEach(store -> store.refreshAll(entities));
    }

    public void refresh(final Entity entity) {
        this.stores.getUniqueStores().forEach(store -> store.refresh(entity));
    }

    private List<UniqueIndexStore> unique(final Property<?, ?> property) {
        if (this.wildcardUnique.isEmpty()) {
            return this.uniqueMap.getOrDefault(property, Collections.emptyList());
        }

        final List<UniqueIndexStore> affected = new ArrayList<>(this.wildcardUnique);

        affected.addAll(this.uniqueMap.getOrDefault(property, Collections.emptyList()));

        return affected;
    }

    private List<GroupedIndexStore> grouped(final Property<?, ?> property) {
        if (this.wildcardGrouped.isEmpty()) {
            return this.groupedMap.getOrDefault(property, Collections.emptyList());
        }

        final List<GroupedIndexStore> affected = new ArrayList<>(this.wildcardGrouped);

        affected.addAll(this.groupedMap.getOrDefault(property, Collections.emptyList()));

        return affected;
    }

    private <StoreType> void map(final IdentityHashMap<Property<?, ?>, List<StoreType>> storeMap, final List<StoreType> wildcard, final Index<?, ?> index, final StoreType store) {
        if (index.getDependencies().isEmpty()) {
            wildcard.add(store);
            return;
        }

        index.getDependencies().forEach(property -> storeMap.computeIfAbsent(property, _ -> new ArrayList<>()).add(store));
    }
}