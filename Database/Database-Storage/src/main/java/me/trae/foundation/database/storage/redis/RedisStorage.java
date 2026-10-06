package me.trae.foundation.database.storage.redis;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.storage.codec.EntityCodec;
import me.trae.foundation.database.storage.driver.RedisDriver;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedisStorage<E extends Entity> implements Storage<UUID, E> {

    private final RedisDriver redisDriver;

    @Getter
    private final RedisNamespace redisNamespace;

    private final EntityCodec<E> entityCodec;
    private final RedisHashWriter redisHashWriter;

    public RedisStorage(final RedisDriver redisDriver, final RedisNamespace redisNamespace, final Class<E> entityType, final Duration expiry) {
        this(redisDriver, redisNamespace, new EntityCodec<>(entityType), new RedisHashWriter(redisDriver, expiry));
    }

    public RedisStorage(final RedisDriver redisDriver, final RedisNamespace redisNamespace, final Class<E> entityType) {
        this(redisDriver, redisNamespace, entityType, null);
    }

    @Override
    public void put(final UUID id, final E entity) {
        this.redisHashWriter.write(this.redisNamespace.getKey(id), this.entityCodec.encode(entity));
    }

    @Override
    public void remove(final UUID id) {
        this.redisDriver.getCommands().del(this.redisNamespace.getKey(id));
    }

    @Override
    public Optional<E> get(final UUID id) {
        return this.decode(id, this.redisDriver.getCommands().hgetall(this.redisNamespace.getKey(id)));
    }

    @Override
    public Map<UUID, E> getAll(final Collection<UUID> ids) {
        final Map<UUID, CompletableFuture<Map<String, String>>> futureMap = new LinkedHashMap<>();

        for (final UUID id : ids) {
            futureMap.put(id, this.redisDriver.getAsyncCommands().hgetall(this.redisNamespace.getKey(id)).toCompletableFuture());
        }

        CompletableFuture.allOf(futureMap.values().toArray(CompletableFuture[]::new)).join();

        final Map<UUID, E> entityMap = new LinkedHashMap<>();

        futureMap.forEach((id, future) -> this.decode(id, future.join()).ifPresent(entity -> entityMap.put(id, entity)));

        return entityMap;
    }

    @Override
    public boolean contains(final UUID id) {
        return this.redisDriver.getCommands().exists(this.redisNamespace.getKey(id)) > 0;
    }

    public void putProperties(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties) {
        if (entityProperties.isEmpty()) {
            return;
        }

        this.redisHashWriter.write(this.redisNamespace.getKey(entity.getId()), this.entityCodec.encode(entity, entityProperties));
    }

    public long increment(final UUID id, final EntityProperty<?, ? extends Number> entityProperty, final long delta) {
        if (entityProperty.getValueConverter().isPresent()) {
            throw new SchemaException("Property %s cannot be incremented because it uses a converter".formatted(entityProperty.getName()));
        }

        if (entityProperty.getValueType() != Long.class && entityProperty.getValueType() != Integer.class) {
            throw new SchemaException("Property %s must be a Long or Integer to be incremented".formatted(entityProperty.getName()));
        }

        return this.redisHashWriter.increment(this.redisNamespace.getKey(id), entityProperty.getName(), delta);
    }

    public RedisStorage<E> forTenant(final String tenantId) {
        return new RedisStorage<>(this.redisDriver, this.redisNamespace.withTenant(tenantId), this.entityCodec, this.redisHashWriter);
    }

    private Optional<E> decode(final UUID id, final Map<String, String> fieldMap) {
        if (fieldMap == null || fieldMap.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(this.entityCodec.decode(id, fieldMap));
        } catch (final RuntimeException exception) {
            this.remove(id);
            return Optional.empty();
        }
    }
}