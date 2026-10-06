package me.trae.foundation.database.lookup;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.holder.InstanceMode;
import me.trae.foundation.database.api.holder.LookupTier;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.query.Operator;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.repository.EntityRepository;
import me.trae.foundation.database.lookup.index.PropertyIndex;
import me.trae.foundation.database.lookup.step.LookupStep;
import me.trae.foundation.database.storage.local.LocalStorage;
import me.trae.foundation.database.storage.redis.RedisStorage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@UtilityClass
public class EntityLookups {

    public <E extends Entity> TieredLookup<UUID, E> byId(final InstanceMode instanceMode, final LocalStorage<UUID, E> localStorage, final RedisStorage<E> redisStorage, final EntityRepository<E> entityRepository) {
        final List<LookupStep<UUID, E>> stepList = new ArrayList<>();

        if (instanceMode == InstanceMode.SINGLETON && localStorage != null) {
            stepList.add(LookupStep.of(LookupTier.LOCAL, localStorage));
        }

        if (redisStorage != null) {
            stepList.add(LookupStep.of(LookupTier.REDIS, redisStorage));
        }

        stepList.add(LookupStep.of(LookupTier.DATABASE, entityRepository::findById, ids -> mapById(entityRepository.findManyById(ids))));

        return new TieredLookup<>(stepList);
    }

    public <E extends Entity, Value> TieredLookup<Value, E> byProperty(final InstanceMode instanceMode, final EntityProperty<? super E, Value> entityProperty, final LocalStorage<UUID, E> localStorage, final RedisStorage<E> redisStorage, final PropertyIndex<Value> propertyIndex, final EntityRepository<E> entityRepository) {
        final List<LookupStep<Value, E>> stepList = new ArrayList<>();

        if (instanceMode == InstanceMode.SINGLETON && localStorage != null) {
            stepList.add(LookupStep.of(LookupTier.LOCAL, value -> findLocal(localStorage, entityProperty, value), (value, entity) -> localStorage.put(entity.getId(), entity)));
        }

        if (redisStorage != null && propertyIndex != null) {
            stepList.add(LookupStep.of(LookupTier.REDIS, value -> findRedis(redisStorage, propertyIndex, entityProperty, value), (value, entity) -> {
                redisStorage.put(entity.getId(), entity);
                propertyIndex.put(value, entity.getId());
            }));
        }

        stepList.add(LookupStep.of(LookupTier.DATABASE, value -> entityRepository.findOne(Query.where(entityProperty, Operator.EQUALS, value))));

        return new TieredLookup<>(stepList);
    }

    private <E extends Entity, Value> Optional<E> findLocal(final LocalStorage<UUID, E> localStorage, final EntityProperty<? super E, Value> entityProperty, final Value value) {
        return localStorage.getValues().stream()
                .filter(entity -> Objects.equals(entityProperty.getValue(entity), value))
                .findFirst();
    }

    private <E extends Entity, Value> Optional<E> findRedis(final RedisStorage<E> redisStorage, final PropertyIndex<Value> propertyIndex, final EntityProperty<? super E, Value> entityProperty, final Value value) {
        final Optional<E> result = propertyIndex.find(value)
                .flatMap(redisStorage::get)
                .filter(entity -> Objects.equals(entityProperty.getValue(entity), value));

        if (result.isEmpty()) {
            propertyIndex.remove(value);
        }

        return result;
    }

    private <E extends Entity> Map<UUID, E> mapById(final List<E> entities) {
        final Map<UUID, E> entityMap = new LinkedHashMap<>();

        for (final E entity : entities) {
            entityMap.put(entity.getId(), entity);
        }

        return entityMap;
    }
}