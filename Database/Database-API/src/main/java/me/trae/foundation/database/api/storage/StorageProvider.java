package me.trae.foundation.database.api.storage;

import me.trae.foundation.database.api.tier.Tier;

public interface StorageProvider {

    Tier getTier();

    <Key, Value> Storage<Key, Value> create(final Namespace<Key, Value> namespace);
}