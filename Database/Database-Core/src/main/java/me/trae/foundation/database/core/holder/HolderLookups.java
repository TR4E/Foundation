package me.trae.foundation.database.core.holder;

import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.lookup.EntityLookups;
import me.trae.foundation.database.lookup.TieredLookup;
import me.trae.foundation.database.lookup.index.RedisPropertyIndex;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.local.LocalStorage;
import me.trae.foundation.database.storage.redis.RedisStorage;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HolderLookups<E extends Entity> {

    private final Map<String, TieredLookup<?, E>> propertyLookupMap = new ConcurrentHashMap<>();

    private final AbstractEntityRepository<E> repository;
    private final LocalStorage<UUID, E> localStorage;
    private final RedisStorage<E> redisStorage;
    private final RedisDriver redisDriver;

    @Getter
    private final TieredLookup<UUID, E> idLookup;

    public HolderLookups(final AbstractEntityRepository<E> repository, final LocalStorage<UUID, E> localStorage, final RedisStorage<E> redisStorage, final RedisDriver redisDriver) {
        this.repository = repository;
        this.localStorage = localStorage;
        this.redisStorage = redisStorage;
        this.redisDriver = redisDriver;
        this.idLookup = EntityLookups.byId(localStorage, redisStorage, repository);
    }

    public <Value> Optional<E> lookup(final EntityProperty<? super E, Value> entityProperty, final Value value) {
        return this.getPropertyLookup(entityProperty).lookup(value);
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

    @SuppressWarnings("unchecked")
    private <Value> TieredLookup<Value, E> getPropertyLookup(final EntityProperty<? super E, Value> entityProperty) {
        return (TieredLookup<Value, E>) this.propertyLookupMap.computeIfAbsent(entityProperty.getName(), _ -> EntityLookups.byProperty(entityProperty, this.localStorage, this.redisStorage, this.getPropertyIndex(entityProperty).orElse(null), this.repository));
    }
}