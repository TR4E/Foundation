package me.trae.foundation.database.index;

import lombok.Getter;
import me.trae.foundation.database.api.index.GroupedIndex;
import me.trae.foundation.database.api.index.Index;
import me.trae.foundation.database.api.index.IndexRegistry;
import me.trae.foundation.database.api.index.UniqueIndex;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.api.storage.StorageProvider;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.index.exception.IndexException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class IndexStores {

    private final IdentityHashMap<Index<?, ?>, UniqueIndexStore> uniqueMap = new IdentityHashMap<>();
    private final IdentityHashMap<Index<?, ?>, GroupedIndexStore> groupedMap = new IdentityHashMap<>();
    private final IdentityHashMap<Property<?, ?>, UniqueIndexStore> propertyMap = new IdentityHashMap<>();

    @Getter
    private final List<UniqueIndexStore> uniqueStores = new ArrayList<>();

    @Getter
    private final List<GroupedIndexStore> groupedStores = new ArrayList<>();

    public IndexStores(final Class<?> entityType, final Set<Tier> tiers, final List<StorageProvider> providers, final Function<Index<?, ?>, Namespace<Object, UUID>> namespaceFactory) {
        IndexRegistry.getIndexes(entityType).forEach(index -> this.create(index, tiers, providers, namespaceFactory));
    }

    public UniqueIndexStore unique(final UniqueIndex<?, ?> index) {
        return this.uniqueMap.get(index);
    }

    public GroupedIndexStore grouped(final GroupedIndex<?, ?> index) {
        return this.groupedMap.get(index);
    }

    public UniqueIndexStore byProperty(final Property<?, ?> property) {
        return this.propertyMap.get(property);
    }

    private void create(final Index<?, ?> index, final Set<Tier> tiers, final List<StorageProvider> providers, final Function<Index<?, ?>, Namespace<Object, UUID>> namespaceFactory) {
        final Set<Tier> resolved = index.getTiers().isEmpty() ? tiers : index.getTiers();

        if (index instanceof final GroupedIndex<?, ?> groupedIndex) {
            if (resolved.contains(Tier.REDIS)) {
                throw new IndexException("Grouped index %s cannot live in Redis, use the repository index for that lookup".formatted(index.getName()));
            }

            final GroupedIndexStore store = new GroupedIndexStore(groupedIndex);

            this.groupedStores.add(store);
            this.groupedMap.put(index, store);
            return;
        }

        if (index instanceof final UniqueIndex<?, ?> uniqueIndex) {
            final UniqueIndexStore store = new UniqueIndexStore(uniqueIndex, this.storages(index, resolved, providers, namespaceFactory));

            this.uniqueStores.add(store);
            this.uniqueMap.put(index, store);

            if (index.getDependencies().size() == 1) {
                index.getDependencies().forEach(property -> this.propertyMap.putIfAbsent(property, store));
            }
        }
    }

    private List<Storage<Object, UUID>> storages(final Index<?, ?> index, final Set<Tier> tiers, final List<StorageProvider> providers, final Function<Index<?, ?>, Namespace<Object, UUID>> namespaceFactory) {
        return providers.stream()
                .filter(provider -> provider.getTier() != Tier.POSTGRES)
                .filter(provider -> tiers.contains(provider.getTier()))
                .sorted(Comparator.comparing(StorageProvider::getTier))
                .map(provider -> provider.create(namespaceFactory.apply(index)))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}