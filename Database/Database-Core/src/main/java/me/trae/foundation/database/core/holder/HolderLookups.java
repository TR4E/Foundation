package me.trae.foundation.database.core.holder;

import io.lettuce.core.RedisException;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.ConnectionException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.repository.index.EntityIndex;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.lookup.EntityLookups;
import me.trae.foundation.database.lookup.TieredLookup;
import me.trae.foundation.database.lookup.index.RedisEntityIndex;
import me.trae.foundation.database.lookup.index.RedisPropertyIndex;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.local.LocalStorage;
import me.trae.foundation.database.storage.redis.RedisStorage;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HolderLookups<E extends Entity> {

    private final Map<String, TieredLookup<?, E>> propertyLookupMap = new ConcurrentHashMap<>();
    private final Map<String, TieredLookup<List<Object>, E>> entityIndexLookupMap = new ConcurrentHashMap<>();

    private final AbstractEntityRepository<E> repository;
    private final LocalStorage<UUID, E> localStorage;
    private final LocalEntityIndex<E> localEntityIndex;
    private final RedisStorage<E> redisStorage;
    private final RedisDriver redisDriver;
    private final Duration redisExpiry;

    @Getter
    private final TieredLookup<UUID, E> idLookup;

    public HolderLookups(final AbstractEntityRepository<E> repository, final LocalStorage<UUID, E> localStorage, final LocalEntityIndex<E> localEntityIndex, final RedisStorage<E> redisStorage, final RedisDriver redisDriver, final Duration redisExpiry) {
        this.repository = repository;
        this.localStorage = localStorage;
        this.localEntityIndex = localEntityIndex;
        this.redisStorage = redisStorage;
        this.redisDriver = redisDriver;
        this.redisExpiry = redisExpiry;
        this.idLookup = EntityLookups.byId(localStorage, redisStorage, repository, this::cache);
    }

    public <Value> Optional<E> lookup(final EntityProperty<? super E, Value> entityProperty, final Value value) {
        final EntityIndex<E> entityIndex = this.repository.getEntityIndexes().stream().filter(index -> index.getProperties().size() == 1 && index.getProperties().getFirst().getName().equals(entityProperty.getName())).findFirst().orElse(null);

        if (entityIndex != null) {
            return this.lookup(entityIndex, value);
        }

        return this.getPropertyLookup(entityProperty).lookup(value);
    }

    public Optional<E> lookup(final EntityIndex<? super E> entityIndex, final Object... values) {
        return this.getEntityIndexLookup(entityIndex).lookup(entityIndex.getValues(values));
    }

    public void cache(final E entity) {
        this.localEntityIndex.put(entity, this.localStorage);

        for (final EntityIndex<E> entityIndex : this.repository.getEntityIndexes()) {
            if (entityIndex.hasValues(entity)) {
                this.getRedisEntityIndex(entityIndex).ifPresent(redisEntityIndex -> {
                    try {
                        redisEntityIndex.put(entityIndex.getValues(entity), entity.getId());
                    } catch (final RedisException | ConnectionException ignored) {
                    }
                });
            }
        }
    }

    public void evict(final UUID id) {
        this.localEntityIndex.remove(id, this.localStorage);
    }

    public <Value> Optional<RedisPropertyIndex<Value>> getPropertyIndex(final EntityProperty<? super E, Value> entityProperty) {
        if (this.redisDriver == null || this.redisStorage == null) {
            return Optional.empty();
        }

        if (this.repository.getUniqueProperties().stream().noneMatch(unique -> unique.getName().equals(entityProperty.getName()))) {
            return Optional.empty();
        }

        return Optional.of(new RedisPropertyIndex<>(this.redisDriver, this.redisStorage.getRedisNamespace(), entityProperty));
    }

    public Optional<RedisEntityIndex<E>> getRedisEntityIndex(final EntityIndex<? super E> entityIndex) {
        if (this.redisDriver == null || this.redisStorage == null) {
            return Optional.empty();
        }

        final EntityIndex<E> registeredIndex = this.repository.getEntityIndexes().stream().filter(index -> index.getName().equals(entityIndex.getName())).findFirst().orElse(null);

        return registeredIndex == null ? Optional.empty() : Optional.of(new RedisEntityIndex<>(this.redisDriver, this.redisStorage.getRedisNamespace(), registeredIndex, this.redisExpiry));
    }

    @SuppressWarnings("unchecked")
    private <Value> TieredLookup<Value, E> getPropertyLookup(final EntityProperty<? super E, Value> entityProperty) {
        return (TieredLookup<Value, E>) this.propertyLookupMap.computeIfAbsent(entityProperty.getName(), _ -> EntityLookups.byProperty(entityProperty, this.localStorage, this.redisStorage, this.getPropertyIndex(entityProperty).orElse(null), this.repository, this::cache));
    }

    @SuppressWarnings("unchecked")
    private TieredLookup<List<Object>, E> getEntityIndexLookup(final EntityIndex<? super E> entityIndex) {
        final EntityIndex<E> registeredIndex = this.repository.getEntityIndexes().stream().filter(index -> index.getName().equals(entityIndex.getName())).findFirst().orElseThrow(() -> new IllegalArgumentException("Index %s is not registered for %s".formatted(entityIndex.getName(), this.repository.getEntityType().getSimpleName())));

        if (!registeredIndex.getProperties().equals(entityIndex.getProperties())) {
            throw new IllegalArgumentException("Index %s does not match the repository declaration".formatted(entityIndex.getName()));
        }

        return this.entityIndexLookupMap.computeIfAbsent(registeredIndex.getName(), _ -> {
            final RedisEntityIndex<E> redisIndex = this.redisDriver == null || this.redisStorage == null ? null : new RedisEntityIndex<>(this.redisDriver, this.redisStorage.getRedisNamespace(), registeredIndex, this.redisExpiry);
            return EntityLookups.byIndex(registeredIndex, this.localStorage, values -> this.localEntityIndex.get(registeredIndex, values.toArray(), this.localStorage), this::cache, this.redisStorage, redisIndex, this.repository);
        });
    }
}