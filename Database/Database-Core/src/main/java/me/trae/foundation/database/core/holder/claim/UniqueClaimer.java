package me.trae.foundation.database.core.holder.claim;

import io.lettuce.core.RedisException;
import lombok.AllArgsConstructor;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.ConnectionException;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.exception.UniqueValueTakenException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.query.Operator;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.repository.index.EntityIndex;
import me.trae.foundation.database.core.holder.HolderLookups;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.lookup.index.EntityIndexKey;
import me.trae.foundation.database.lookup.index.RedisEntityIndex;
import me.trae.foundation.database.lookup.index.RedisPropertyIndex;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.redis.RedisNamespace;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@AllArgsConstructor
public final class UniqueClaimer<E extends Entity> {

    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final Runnable NO_CALLBACK = () -> {};

    private final AbstractEntityRepository<E> repository;
    private final HolderLookups<E> holderLookups;
    private final RedisDriver redisDriver;

    public List<Runnable> claim(final E entity, final Collection<? extends EntityProperty<?, ?>> changedProperties) {
        final Set<String> changedNameSet = changedProperties.stream().map(EntityProperty::getName).collect(Collectors.toSet());
        final List<Runnable> callbackList = new ArrayList<>();

        for (final EntityProperty<? super E, ?> entityProperty : this.repository.getUniqueProperties()) {
            if (changedNameSet.contains(entityProperty.getName())) {
                callbackList.add(this.claimProperty(entityProperty, entity));
            }
        }

        for (final EntityIndex<E> entityIndex : this.repository.getCompositeIndexes()) {
            if (entityIndex.getProperties().stream().anyMatch(property -> changedNameSet.contains(property.getName()))) {
                callbackList.add(this.claimIndex(entityIndex, entity));
            }
        }

        return callbackList;
    }

    public EntityIdClaim claimId(final E entity) {
        if (this.redisDriver == null) {
            return EntityIdClaim.noop();
        }

        final String key = this.getEntityIdClaimKey(entity.getId());
        final String token = UUID.randomUUID().toString();
        final String owner = ClaimScripts.claim(this.redisDriver, key, token, LEASE);

        if (!token.equals(owner)) {
            throw new DatabaseException("Cannot save %s %s because its ID is already claimed".formatted(entity.getClass().getSimpleName(), entity.getId()));
        }

        return new EntityIdClaim(this.redisDriver, key, token);
    }

    public void cacheRedisIndexes(final E entity) {
        this.holderLookups.cacheRedisIndexes(entity);
    }

    public void release(final E entity) {
        for (final EntityProperty<? super E, ?> entityProperty : this.repository.getUniqueProperties()) {
            this.releaseProperty(entityProperty, entity);
        }

        for (final EntityIndex<E> entityIndex : this.repository.getCompositeIndexes()) {
            this.releaseIndex(entityIndex, entity);
        }
    }

    public void releaseFailedCreate(final E entity, final Optional<E> persistedEntityOptional) {
        if (persistedEntityOptional.isEmpty()) {
            this.release(entity);
            return;
        }

        final E persistedEntity = persistedEntityOptional.get();

        for (final EntityProperty<? super E, ?> entityProperty : this.repository.getUniqueProperties()) {
            if (!this.hasSameIndexValue(entityProperty, entity, persistedEntity)) {
                this.releaseProperty(entityProperty, entity);
            }
        }

        for (final EntityIndex<E> entityIndex : this.repository.getCompositeIndexes()) {
            if (entityIndex.hasValues(entity) && (!entityIndex.hasValues(persistedEntity) || !entityIndex.getValues(entity).equals(entityIndex.getValues(persistedEntity)))) {
                this.releaseIndex(entityIndex, entity);
            }
        }
    }

    private <Value> Runnable claimProperty(final EntityProperty<? super E, Value> entityProperty, final E entity) {
        final Value value = entityProperty.getValue(entity);
        if (value == null) {
            return NO_CALLBACK;
        }

        this.repository.findOne(Query.where(entityProperty, Operator.EQUALS, value))
                .filter(found -> !found.getId().equals(entity.getId()) && this.isStillOwner(entityProperty, value, found.getId()))
                .ifPresent(_ -> {
                    throw this.taken(entityProperty, value);
                });

        if (this.redisDriver == null) {
            return NO_CALLBACK;
        }

        final String key = this.getClaimKey(entityProperty, value);
        final String id = entity.getId().toString();
        final String owner = ClaimScripts.claim(this.redisDriver, key, id, LEASE);

        if (!id.equals(owner) && !this.steal(entityProperty, value, key, owner, id)) {
            throw this.taken(entityProperty, value);
        }

        if (entityProperty.isPersistent()) {
            return () -> ClaimScripts.persist(this.redisDriver, key, id);
        }

        ClaimScripts.persist(this.redisDriver, key, id);

        return NO_CALLBACK;
    }

    private <Value> void releaseProperty(final EntityProperty<? super E, Value> entityProperty, final E entity) {
        final Value value = entityProperty.getValue(entity);

        if (value != null && this.redisDriver != null) {
            ClaimScripts.release(this.redisDriver, this.getClaimKey(entityProperty, value), entity.getId().toString());
        }
    }

    private <Value> boolean steal(final EntityProperty<? super E, Value> entityProperty, final Value value, final String key, final String owner, final String id) {
        if (owner == null) {
            return false;
        }

        return this.holderLookups.getIdLookup().lookup(UUID.fromString(owner))
                .map(found -> !Objects.equals(entityProperty.normalizeIndexValue(entityProperty.getValue(found)), entityProperty.normalizeIndexValue(value)) && ClaimScripts.steal(this.redisDriver, key, owner, id, LEASE, false))
                .orElseGet(() -> ClaimScripts.steal(this.redisDriver, key, owner, id, LEASE, true));
    }

    private <Value> boolean isStillOwner(final EntityProperty<? super E, Value> entityProperty, final Value value, final UUID ownerId) {
        return this.holderLookups.getIdLookup().lookup(ownerId)
                .map(owner -> Objects.equals(entityProperty.normalizeIndexValue(entityProperty.getValue(owner)), entityProperty.normalizeIndexValue(value)))
                .orElse(false);
    }

    private <Value> String getClaimKey(final EntityProperty<? super E, Value> entityProperty, final Value value) {
        return this.holderLookups.getPropertyIndex(entityProperty)
                .map(propertyIndex -> propertyIndex.getClaimKey(value))
                .orElseThrow(() -> new IllegalStateException("No Redis index for %s".formatted(entityProperty.getName())));
    }

    private String getEntityIdClaimKey(final UUID id) {
        final RedisNamespace namespace = RedisNamespace.of(this.repository.getTable(), this.repository.getTenantId().orElse(null));
        return namespace.getKey("claim:id:%s".formatted(id));
    }

    public static final class EntityIdClaim {

        private final RedisDriver redisDriver;
        private final String key;
        private final String token;

        private EntityIdClaim(final RedisDriver redisDriver, final String key, final String token) {
            this.redisDriver = redisDriver;
            this.key = key;
            this.token = token;
        }

        private static EntityIdClaim noop() {
            return new EntityIdClaim(null, null, null);
        }

        public void release() {
            if (this.redisDriver != null) {
                ClaimScripts.release(this.redisDriver, this.key, this.token);
            }
        }
    }

    private <Value> boolean hasSameIndexValue(final EntityProperty<? super E, Value> entityProperty, final E first, final E second) {
        return Objects.equals(entityProperty.normalizeIndexValue(entityProperty.getValue(first)), entityProperty.normalizeIndexValue(entityProperty.getValue(second)));
    }

    public <Value> void transferProperty(final EntityProperty<? super E, Value> entityProperty, final Value claimantPreviousValue, final Value claimedValue, final UUID claimantId, final UUID currentOwnerId) {
        if (this.redisDriver == null) {
            return;
        }

        final RedisPropertyIndex<Value> propertyIndex = this.holderLookups.getPropertyIndex(entityProperty).orElse(null);

        if (propertyIndex == null) {
            return;
        }

        try {
            if (claimantPreviousValue != null && !Objects.equals(entityProperty.normalizeIndexValue(claimantPreviousValue), entityProperty.normalizeIndexValue(claimedValue))) {
                ClaimScripts.release(this.redisDriver, propertyIndex.getClaimKey(claimantPreviousValue), claimantId.toString());
            }

            ClaimScripts.transfer(this.redisDriver, propertyIndex.getClaimKey(claimedValue), currentOwnerId.toString(), claimantId.toString());
        } catch (final RedisException | ConnectionException ignored) {
        }
    }

    private Runnable claimIndex(final EntityIndex<E> entityIndex, final E entity) {
        if (!entityIndex.hasValues(entity)) {
            return NO_CALLBACK;
        }

        final List<Object> values = entityIndex.getValues(entity);

        this.findByIndex(entityIndex, values).filter(found -> !found.getId().equals(entity.getId()) && this.isStillOwner(entityIndex, values, found.getId())).ifPresent(_ -> {
            throw this.taken(entityIndex, values);
        });

        if (this.redisDriver == null) {
            return NO_CALLBACK;
        }

        final RedisEntityIndex<E> redisEntityIndex = this.holderLookups.getRedisEntityIndex(entityIndex).orElseThrow();
        final String key = redisEntityIndex.getClaimKey(values);
        final String id = entity.getId().toString();
        final String owner = ClaimScripts.claim(this.redisDriver, key, id, LEASE);

        if (!id.equals(owner) && !this.steal(entityIndex, values, key, owner, id)) {
            throw this.taken(entityIndex, values);
        }

        return () -> ClaimScripts.persist(this.redisDriver, key, id);
    }

    private void releaseIndex(final EntityIndex<E> entityIndex, final E entity) {
        if (this.redisDriver != null && entityIndex.hasValues(entity)) {
            this.holderLookups.getRedisEntityIndex(entityIndex).ifPresent(redisEntityIndex -> ClaimScripts.release(this.redisDriver, redisEntityIndex.getClaimKey(entityIndex.getValues(entity)), entity.getId().toString()));
        }
    }

    private Optional<E> findByIndex(final EntityIndex<E> entityIndex, final List<Object> values) {
        final Query<E> query = Query.of(this.repository.getEntityType());

        for (int index = 0; index < values.size(); index++) {
            addCondition(query, entityIndex.getProperties().get(index), values.get(index));
        }

        return this.repository.findOne(query);
    }

    private boolean isStillOwner(final EntityIndex<E> entityIndex, final List<Object> values, final UUID ownerId) {
        final String requestedKey = EntityIndexKey.encode(entityIndex, values);

        return this.holderLookups.getIdLookup().lookup(ownerId)
                .map(owner -> entityIndex.hasValues(owner) && EntityIndexKey.encode(entityIndex, entityIndex.getValues(owner)).equals(requestedKey))
                .orElse(false);
    }

    private boolean steal(final EntityIndex<E> entityIndex, final List<Object> values, final String key, final String owner, final String id) {
        if (owner == null) {
            return false;
        }

        return this.holderLookups.getIdLookup().lookup(UUID.fromString(owner))
                .map(found -> (!entityIndex.hasValues(found) || !EntityIndexKey.encode(entityIndex, entityIndex.getValues(found)).equals(EntityIndexKey.encode(entityIndex, values))) && ClaimScripts.steal(this.redisDriver, key, owner, id, LEASE, false))
                .orElseGet(() -> ClaimScripts.steal(this.redisDriver, key, owner, id, LEASE, true));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <E extends Entity> void addCondition(final Query<E> query, final EntityProperty entityProperty, final Object value) {
        query.and(entityProperty, Operator.EQUALS, value);
    }

    private UniqueValueTakenException taken(final EntityProperty<?, ?> entityProperty, final Object value) {
        return new UniqueValueTakenException("%s %s is already taken".formatted(entityProperty.getName(), value));
    }

    private UniqueValueTakenException taken(final EntityIndex<?> entityIndex, final List<Object> values) {
        return new UniqueValueTakenException("%s %s is already taken".formatted(entityIndex.getName(), values));
    }
}