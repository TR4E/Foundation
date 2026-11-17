package me.trae.foundation.database.redis.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.api.storage.StorageProvider;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.redis.driver.RedisDriver;

@AllArgsConstructor
@Getter
public final class RedisStorageProvider implements StorageProvider {

    private final Tier tier = Tier.REDIS;

    private final RedisDriver driver;

    @Override
    public <Key, Value> Storage<Key, Value> create(final Namespace<Key, Value> namespace) {
        return new RedisStorage<>(namespace, this.driver);
    }
}