package me.trae.foundation.database.core.holder.claim;

import lombok.AllArgsConstructor;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.UniqueValueTakenException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.query.Operator;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.core.holder.HolderLookups;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.storage.driver.RedisDriver;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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

        return callbackList;
    }

    public void release(final E entity) {
        for (final EntityProperty<? super E, ?> entityProperty : this.repository.getUniqueProperties()) {
            this.releaseProperty(entityProperty, entity);
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

        final String key = this.getKey(entityProperty, value);
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
            ClaimScripts.release(this.redisDriver, this.getKey(entityProperty, value), entity.getId().toString());
        }
    }

    private <Value> boolean steal(final EntityProperty<? super E, Value> entityProperty, final Value value, final String key, final String owner, final String id) {
        if (owner == null) {
            return false;
        }

        return this.holderLookups.getIdLookup().lookup(UUID.fromString(owner))
                .map(found -> !Objects.equals(entityProperty.getValue(found), value) && ClaimScripts.steal(this.redisDriver, key, owner, id, LEASE, false))
                .orElseGet(() -> ClaimScripts.steal(this.redisDriver, key, owner, id, LEASE, true));
    }

    private <Value> boolean isStillOwner(final EntityProperty<? super E, Value> entityProperty, final Value value, final UUID ownerId) {
        return this.holderLookups.getIdLookup().lookup(ownerId)
                .map(owner -> Objects.equals(entityProperty.getValue(owner), value))
                .orElse(false);
    }

    private <Value> String getKey(final EntityProperty<? super E, Value> entityProperty, final Value value) {
        return this.holderLookups.getPropertyIndex(entityProperty)
                .map(propertyIndex -> propertyIndex.getKey(value))
                .orElseThrow(() -> new IllegalStateException("No Redis index for %s".formatted(entityProperty.getName())));
    }

    private UniqueValueTakenException taken(final EntityProperty<?, ?> entityProperty, final Object value) {
        return new UniqueValueTakenException("%s %s is already taken".formatted(entityProperty.getName(), value));
    }
}