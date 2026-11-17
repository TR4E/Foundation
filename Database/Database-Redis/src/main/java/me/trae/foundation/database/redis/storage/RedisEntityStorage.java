package me.trae.foundation.database.redis.storage;

import io.lettuce.core.api.sync.RedisCommands;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.redis.codec.EntityCodec;
import me.trae.foundation.database.redis.driver.RedisDriver;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor
public final class RedisEntityStorage<EntityType extends Entity> implements Storage<UUID, EntityType> {

    @Getter
    private final Namespace<UUID, EntityType> namespace;

    private final RedisDriver driver;
    private final EntityCodec<EntityType> codec;

    @Override
    public Optional<EntityType> get(final UUID key) {
        final Map<String, String> fieldMap = this.driver.getCommands().hgetall(this.path(key));

        return fieldMap.isEmpty() ? Optional.empty() : Optional.of(this.codec.decode(key, fieldMap));
    }

    @Override
    public void put(final UUID key, final EntityType value) {
        final RedisCommands<String, String> commands = this.driver.getCommands();

        final String path = this.path(key);

        commands.hset(path, this.codec.encode(value));

        if (!this.namespace.getTtl().isZero()) {
            commands.pexpire(path, this.namespace.getTtl().toMillis());
        }
    }

    @Override
    public void remove(final UUID key) {
        this.driver.getCommands().del(this.path(key));
    }

    @Override
    public boolean contains(final UUID key) {
        return this.driver.getCommands().exists(this.path(key)) > 0;
    }

    @Override
    public Collection<EntityType> values() {
        throw new UnsupportedOperationException("Redis cannot enumerate %s".formatted(this.namespace.getName()));
    }

    @Override
    public void refresh(final UUID key) {
        this.driver.getCommands().pexpire(this.path(key), this.namespace.getTtl().toMillis());
    }

    public String path(final UUID key) {
        return this.namespace.getName() + ":" + this.namespace.getKeyEncoder().apply(key);
    }

    public String channel(final UUID key) {
        return this.path(key) + ":events";
    }

    public long version(final UUID key) {
        final String version = this.driver.getCommands().hget(this.path(key), "version");

        return version == null ? 0L : Long.parseLong(version);
    }
}