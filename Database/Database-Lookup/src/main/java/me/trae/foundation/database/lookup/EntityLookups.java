package me.trae.foundation.database.lookup;

import io.lettuce.core.RedisException;
import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.ConnectionException;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@UtilityClass
public class EntityLookups {

    public static <E extends Entity> TieredLookup<UUID, E> byId(final LocalStorage<UUID, E> localStorage, final RedisStorage<E> redisStorage, final EntityRepository<E> entityRepository) {
        final List<LookupStep<UUID, E>> stepList = new ArrayList<>();

        if (localStorage != null) {
            stepList.add(LookupStep.of(LookupTier.LOCAL, localStorage));
        }

        if (redisStorage != null) {
            stepList.add(LookupStep.of(LookupTier.REDIS, id -> findRedisById(redisStorage, id)));
        }

        stepList.add(LookupStep.of(
                LookupTier.DATABASE,
                id -> loadById(redisStorage, entityRepository, id),
                ids -> loadManyById(redisStorage, entityRepository, ids)
        ));

        return new TieredLookup<>(stepList);
    }

    public static <E extends Entity, Value> TieredLookup<Value, E> byProperty(final EntityProperty<? super E, Value> entityProperty, final LocalStorage<UUID, E> localStorage, final RedisStorage<E> redisStorage, final PropertyIndex<Value> propertyIndex, final EntityRepository<E> entityRepository) {
        final List<LookupStep<Value, E>> stepList = new ArrayList<>();

        if (localStorage != null) {
            stepList.add(LookupStep.of(LookupTier.LOCAL, value -> findLocal(localStorage, entityProperty, value), (value, entity) -> localStorage.put(entity.getId(), entity)));
        }

        if (redisStorage != null && propertyIndex != null) {
            stepList.add(LookupStep.of(LookupTier.REDIS, value -> findRedisByProperty(redisStorage, propertyIndex, entityProperty, value), (value, entity) -> writeRedisByProperty(redisStorage, propertyIndex, value, entity)));
        }

        stepList.add(LookupStep.of(LookupTier.DATABASE, value -> entityRepository.findOne(Query.where(entityProperty, Operator.EQUALS, value))));

        return new TieredLookup<>(stepList);
    }

    private static <E extends Entity, Value> Optional<E> findLocal(final LocalStorage<UUID, E> localStorage, final EntityProperty<? super E, Value> entityProperty, final Value value) {
        return localStorage.getValues().stream()
                .filter(entity -> Objects.equals(entityProperty.getValue(entity), value))
                .findFirst();
    }

    private static <E extends Entity, Value> Optional<E> findRedis(final RedisStorage<E> redisStorage, final PropertyIndex<Value> propertyIndex, final EntityProperty<? super E, Value> entityProperty, final Value value) {
        final Optional<E> result = propertyIndex.find(value)
                .flatMap(redisStorage::get)
                .filter(entity -> Objects.equals(entityProperty.getValue(entity), value));

        if (result.isEmpty()) {
            propertyIndex.remove(value);
        }

        return result;
    }

    private static <E extends Entity> Optional<E> findRedisById(final RedisStorage<E> redisStorage, final UUID id) {
        try {
            return redisStorage.get(id);
        } catch (final RedisException | ConnectionException exception) {
            return Optional.empty();
        }
    }

    private static <E extends Entity> Optional<E> loadById(final RedisStorage<E> redisStorage, final EntityRepository<E> entityRepository, final UUID id) {
        if (redisStorage == null) {
            return entityRepository.findById(id);
        }

        final AtomicBoolean loaderStarted = new AtomicBoolean();
        final AtomicReference<Optional<E>> loadedReference = new AtomicReference<>();

        try {
            return redisStorage.getOrLoad(id, () -> {
                loaderStarted.set(true);
                final Optional<E> loaded = entityRepository.findById(id);
                loadedReference.set(loaded);
                return loaded;
            });
        } catch (final RedisException | ConnectionException exception) {
            if (loaderStarted.get()) {
                final Optional<E> loaded = loadedReference.get();
                if (loaded != null) {
                    return loaded;
                }

                throw exception;
            }

            return entityRepository.findById(id);
        }
    }

    private static <E extends Entity> Map<UUID, E> loadManyById(final RedisStorage<E> redisStorage, final EntityRepository<E> entityRepository, final java.util.Collection<UUID> ids) {
        if (redisStorage == null) {
            return mapById(entityRepository.findManyById(ids));
        }

        final AtomicBoolean loaderStarted = new AtomicBoolean();
        final AtomicReference<Map<UUID, E>> loadedReference = new AtomicReference<>();

        try {
            return redisStorage.getOrLoadAll(ids, missingIds -> {
                loaderStarted.set(true);
                final Map<UUID, E> loaded = mapById(entityRepository.findManyById(missingIds));
                loadedReference.set(loaded);
                return loaded.values();
            });
        } catch (final RedisException | ConnectionException exception) {
            if (loaderStarted.get()) {
                final Map<UUID, E> loaded = loadedReference.get();
                if (loaded != null) {
                    return loaded;
                }

                throw exception;
            }

            return mapById(entityRepository.findManyById(ids));
        }
    }

    private static <E extends Entity, Value> Optional<E> findRedisByProperty(final RedisStorage<E> redisStorage, final PropertyIndex<Value> propertyIndex, final EntityProperty<? super E, Value> entityProperty, final Value value) {
        try {
            return findRedis(redisStorage, propertyIndex, entityProperty, value);
        } catch (final RedisException | ConnectionException exception) {
            return Optional.empty();
        }
    }

    private static <E extends Entity, Value> void writeRedisByProperty(final RedisStorage<E> redisStorage, final PropertyIndex<Value> propertyIndex, final Value value, final E entity) {
        try {
            redisStorage.put(entity.getId(), entity);
            propertyIndex.put(value, entity.getId());
        } catch (final RedisException | ConnectionException ignored) {
        }
    }

    private static <E extends Entity> Map<UUID, E> mapById(final List<E> entities) {
        final Map<UUID, E> entityMap = new LinkedHashMap<>();

        for (final E entity : entities) {
            entityMap.put(entity.getId(), entity);
        }

        return entityMap;
    }
}