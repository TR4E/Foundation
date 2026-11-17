package me.trae.foundation.database.index;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.index.Index;
import me.trae.foundation.database.api.property.Property;

import java.util.UUID;

@AllArgsConstructor
@Getter
public final class IndexUpdater {

    private final IndexStores stores;

    public void index(final Entity entity, final Property<?, ?> property) {
        this.stores.getUniqueStores().stream()
                .filter(store -> affects(store.getIndex(), property))
                .forEach(store -> store.index(entity));

        this.stores.getGroupedStores().stream()
                .filter(store -> affects(store.getIndex(), property))
                .forEach(store -> store.index(entity));
    }

    public void index(final Entity entity) {
        this.stores.getUniqueStores().forEach(store -> store.index(entity));
        this.stores.getGroupedStores().forEach(store -> store.index(entity));
    }

    public void unindex(final UUID id) {
        this.stores.getUniqueStores().forEach(store -> store.unindex(id));
        this.stores.getGroupedStores().forEach(store -> store.unindex(id));
    }

    public void refresh(final Entity entity) {
        this.stores.getUniqueStores().forEach(store -> store.refresh(entity));
    }

    private static boolean affects(final Index<?, ?> index, final Property<?, ?> property) {
        return index.getDependencies().isEmpty() || index.getDependencies().contains(property);
    }
}