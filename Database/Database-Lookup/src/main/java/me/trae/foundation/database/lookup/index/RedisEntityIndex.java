package me.trae.foundation.database.lookup.index;

import io.lettuce.core.SetArgs;
import lombok.AllArgsConstructor;
import me.trae.foundation.database.api.repository.index.EntityIndex;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.redis.RedisNamespace;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor
public final class RedisEntityIndex<E extends me.trae.foundation.database.api.entity.Entity> {

    private static final String REMOVE_SCRIPT = "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end";

    private final RedisDriver redisDriver;
    private final RedisNamespace redisNamespace;
    private final EntityIndex<E> entityIndex;
    private final Duration expiry;

    public Optional<UUID> find(final List<Object> values) {
        return Optional.ofNullable(this.redisDriver.getCommands().get(this.getKey(values))).map(UUID::fromString);
    }

    public void put(final List<Object> values, final UUID id) {
        this.redisDriver.getCommands().set(this.getKey(values), id.toString(), SetArgs.Builder.px(this.expiry.toMillis()));
    }

    public void remove(final List<Object> values, final UUID id) {
        this.redisDriver.getCommands().eval(REMOVE_SCRIPT, io.lettuce.core.ScriptOutputType.INTEGER, new String[]{this.getKey(values)}, id.toString());
    }

    public String getKey(final List<Object> values) {
        if (this.entityIndex.getProperties().size() == 1) {
            return this.redisNamespace.getKey("index:%s:%s".formatted(this.entityIndex.getProperties().getFirst().getName(), encodeSingle(this.entityIndex, values.getFirst())));
        }

        return this.redisNamespace.getKey("index:%s:%s".formatted(this.entityIndex.getName(), EntityIndexKey.encode(this.entityIndex, values)));
    }

    public String getClaimKey(final List<Object> values) {
        return this.redisNamespace.getKey("claim:%s:%s".formatted(this.entityIndex.getName(), EntityIndexKey.encode(this.entityIndex, values)));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String encodeSingle(final EntityIndex<?> entityIndex, final Object value) {
        return EntityIndexKey.encodeValue(entityIndex.getProperties().getFirst(), value);
    }
}