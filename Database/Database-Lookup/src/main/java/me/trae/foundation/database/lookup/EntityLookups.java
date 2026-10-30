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
import me.trae.foundation.database.api.repository.index.EntityIndex;
import me.trae.foundation.database.lookup.index.EntityIndexKey;
import me.trae.foundation.database.lookup.index.PropertyIndex;
import me.trae.foundation.database.lookup.index.RedisEntityIndex;
import me.trae.foundation.database.lookup.step.LookupStep;
import me.trae.foundation.database.storage.local.LocalStorage;
import me.trae.foundation.database.storage.redis.RedisStorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

@UtilityClass
public final class EntityLookups {

    public static <E extends Entity> TieredLookup<UUID, E> byId(final LocalStorage<UUID, E> localStorage, final RedisStorage<E> redisStorage, final EntityRepository<E> entityRepository, final Consumer<E> localWriter) {
        final List<LookupStep<UUID, E>> stepList = new ArrayList<>();

        if (localStorage != null) {
            stepList.add(LookupStep.of(LookupTier.LOCAL, localStorage::get, localStorage::getAll, (_, entity) -> localWriter.accept(entity)));
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

    public static <E extends Entity> TieredLookup<UUID, E> byId(final LocalStorage<UUID, E> localStorage, final RedisStorage<E> redisStorage, final EntityRepository<E> entityRepository) {
        return byId(localStorage, redisStorage, entityRepository, entity -> localStorage.put(entity.getId(), entity));
    }

    public static <E extends Entity> TieredLookup<List<Object>, E> byIndex(final EntityIndex<E> entityIndex, final LocalStorage<UUID, E> localStorage, final Function<List<Object>, Optional<E>> localReader, final Consumer<E> localWriter, final RedisStorage<E> redisStorage, final RedisEntityIndex<E> redisEntityIndex, final EntityRepository<E> entityRepository) {
        final List<LookupStep<List<Object>, E>> stepList = new ArrayList<>();

        if (localStorage != null) {
            stepList.add(LookupStep.of(LookupTier.LOCAL, localReader, (_, entity) -> localWriter.accept(entity)));
        }

        if (redisStorage != null && redisEntityIndex != null) {
            stepList.add(LookupStep.of(LookupTier.REDIS, values -> findRedisByIndex(redisStorage, redisEntityIndex, entityIndex, values), (_, entity) -> writeRedisByIndex(redisStorage, redisEntityIndex, entityIndex, entity)));
        }

        stepList.add(LookupStep.of(LookupTier.DATABASE, values -> findByIndex(entityRepository, entityIndex, values)));

        return new TieredLookup<>(stepList);
    }

    public static <E extends Entity, Value> TieredLookup<Value, E> byProperty(final EntityProperty<? super E, Value> entityProperty, final LocalStorage<UUID, E> localStorage, final RedisStorage<E> redisStorage, final PropertyIndex<Value> propertyIndex, final EntityRepository<E> entityRepository, final Consumer<E> localWriter) {
        final List<LookupStep<Value, E>> stepList = new ArrayList<>();

        if (localStorage != null) {
            stepList.add(LookupStep.of(LookupTier.LOCAL, value -> findLocal(localStorage, entityProperty, value), (_, entity) -> localWriter.accept(entity)));
        }

        if (redisStorage != null && propertyIndex != null) {
            stepList.add(LookupStep.of(LookupTier.REDIS, value -> findRedisByProperty(redisStorage, propertyIndex, entityProperty, value), (value, entity) -> writeRedisByProperty(redisStorage, propertyIndex, value, entity)));
        }

        stepList.add(LookupStep.of(LookupTier.DATABASE, value -> entityRepository.findOne(Query.where(entityProperty, Operator.EQUALS, value))));

        return new TieredLookup<>(stepList);
    }

    public static <E extends Entity, Value> TieredLookup<Value, E> byProperty(final EntityProperty<? super E, Value> entityProperty, final LocalStorage<UUID, E> localStorage, final RedisStorage<E> redisStorage, final PropertyIndex<Value> propertyIndex, final EntityRepository<E> entityRepository) {
        return byProperty(entityProperty, localStorage, redisStorage, propertyIndex, entityRepository, entity -> localStorage.put(entity.getId(), entity));
    }

    private static <E extends Entity, Value> Optional<E> findLocal(final LocalStorage<UUID, E> localStorage, final EntityProperty<? super E, Value> entityProperty, final Value value) {
        final Object normalizedValue = entityProperty.normalizeIndexValue(value);

        return localStorage.getValues().stream()
                .filter(entity -> Objects.equals(entityProperty.normalizeIndexValue(entityProperty.getValue(entity)), normalizedValue))
                .findFirst();
    }

    private static <E extends Entity, Value> Optional<E> findRedis(final RedisStorage<E> redisStorage, final PropertyIndex<Value> propertyIndex, final EntityProperty<? super E, Value> entityProperty, final Value value) {
        final Object normalizedValue = entityProperty.normalizeIndexValue(value);

        final Optional<E> result = propertyIndex.find(value)
                .flatMap(redisStorage::get)
                .filter(entity -> Objects.equals(entityProperty.normalizeIndexValue(entityProperty.getValue(entity)), normalizedValue));

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

                if (loaded.isPresent()) {
                    return loaded;
                }

                throw exception;
            }

            return entityRepository.findById(id);
        }
    }

    private static <E extends Entity> Map<UUID, E> loadManyById(final RedisStorage<E> redisStorage, final EntityRepository<E> entityRepository, final Collection<UUID> ids) {
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

    private static <E extends Entity> Optional<E> findRedisByIndex(final RedisStorage<E> redisStorage, final RedisEntityIndex<E> redisEntityIndex, final EntityIndex<E> entityIndex, final List<Object> values) {
        try {
            final Optional<UUID> id = redisEntityIndex.find(values);

            final Optional<E> entity = id.flatMap(redisStorage::get).filter(found -> entityIndex.hasValues(found) && EntityIndexKey.encode(entityIndex, entityIndex.getValues(found)).equals(EntityIndexKey.encode(entityIndex, entityIndex.getValues(values.toArray()))));

            if (entity.isEmpty()) {
                id.ifPresent(foundId -> redisEntityIndex.remove(values, foundId));
            }

            return entity;
        } catch (final RedisException | ConnectionException exception) {
            return Optional.empty();
        }
    }

    private static <E extends Entity> Optional<E> findByIndex(final EntityRepository<E> entityRepository, final EntityIndex<E> entityIndex, final List<Object> values) {
        final Query<E> query = Query.of(entityRepository.getEntityType());

        for (int index = 0; index < values.size(); index++) {
            addCondition(query, entityIndex.getProperties().get(index), values.get(index));
        }

        return entityRepository.findOne(query);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <E extends Entity> void addCondition(final Query<E> query, final EntityProperty entityProperty, final Object value) {
        query.and(entityProperty, Operator.EQUALS, value);
    }

    private static <E extends Entity> void writeRedisByIndex(final RedisStorage<E> redisStorage, final RedisEntityIndex<E> redisEntityIndex, final EntityIndex<E> entityIndex, final E entity) {
        try {
            redisStorage.cache(entity);

            redisEntityIndex.put(entityIndex.getValues(entity), entity.getId());
        } catch (final RedisException | ConnectionException ignored) {
        }
    }

    private static <E extends Entity, Value> void writeRedisByProperty(final RedisStorage<E> redisStorage, final PropertyIndex<Value> propertyIndex, final Value value, final E entity) {
        try {
            redisStorage.cache(entity);

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