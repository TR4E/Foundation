package me.trae.foundation.database.local;

import lombok.Getter;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.api.storage.StorageProvider;
import me.trae.foundation.database.api.tier.Tier;

public final class LocalStorageProvider implements StorageProvider {

    @Getter
    private final Tier tier = Tier.LOCAL;

    @Override
    public <Key, Value> Storage<Key, Value> create(final Namespace<Key, Value> namespace) {
        return new LocalStorage<>(namespace);
    }
}