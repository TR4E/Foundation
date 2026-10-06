package me.trae.foundation.database.lookup.index;

import lombok.AllArgsConstructor;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.storage.codec.ValueCodec;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.redis.RedisNamespace;

import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor
public final class RedisPropertyIndex<Value> implements PropertyIndex<Value> {

    private final RedisDriver redisDriver;
    private final RedisNamespace redisNamespace;
    private final EntityProperty<?, Value> entityProperty;

    @Override
    public Optional<UUID> find(final Value value) {
        return Optional.ofNullable(this.redisDriver.getCommands().get(this.getKey(value))).map(UUID::fromString);
    }

    @Override
    public void put(final Value value, final UUID id) {
        this.redisDriver.getCommands().set(this.getKey(value), id.toString());
    }

    @Override
    public void remove(final Value value) {
        this.redisDriver.getCommands().del(this.getKey(value));
    }

    public String getKey(final Value value) {
        return this.redisNamespace.getKey("index:%s:%s".formatted(this.entityProperty.getName(), ValueCodec.encode(this.entityProperty, value)));
    }
}