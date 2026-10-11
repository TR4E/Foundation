package me.trae.foundation.database.core.holder;

import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.holder.EntityHolder;
import me.trae.foundation.database.api.holder.InstanceMode;
import me.trae.foundation.database.api.holder.LookupTier;
import me.trae.foundation.database.api.holder.RedisMode;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.repository.index.EntityIndex;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.storage.local.LocalStorage;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public abstract class AbstractEntityHolder<E extends Entity, R extends AbstractEntityRepository<E>> implements EntityHolder<E, R> {

    @Getter
    private final R repository;

    @Getter
    private final InstanceMode instanceMode;

    private final HolderComponents<E> holderComponents;
    private final HolderCache<E> holderCache;

    protected AbstractEntityHolder(final R repository, final InstanceMode instanceMode, final RedisMode redisMode, final Duration localExpiry, final Duration redisExpiry) {
        this.repository = repository;
        this.instanceMode = instanceMode;
        this.holderComponents = HolderComponents.create(repository, instanceMode, redisMode, localExpiry, redisExpiry, LocalStorage.DEFAULT_MAXIMUM_AGE);
        this.holderCache = new HolderCache<>(instanceMode, this.holderComponents, repository.getCoreDatabase().getBatchQueue());

        repository.getCoreDatabase().getBatchQueue().addPreFlushTask(this.holderCache::flushAndEvict);
    }

    @Override
    public final Optional<E> getById(final UUID id, final Set<LookupTier> lookupTiers) {
        final Optional<E> result = this.holderComponents.getHolderLookups().getIdLookup().lookup(id, lookupTiers);

        result.ifPresent(this.holderCache::track);

        return result;
    }

    @Override
    public final <Value> Optional<E> getByProperty(final EntityProperty<? super E, Value> entityProperty, final Value value) {
        final Optional<E> result = this.holderComponents.getHolderLookups().lookup(entityProperty, value);

        result.ifPresent(this.holderCache::track);

        return result;
    }

    @Override
    public final <Value> void transferUniqueProperty(final UUID claimantId, final UUID currentOwnerId, final EntityProperty<? super E, Value> entityProperty, final Value claimedValue, final Value replacementValue) {
        final E claimant = this.getById(claimantId).orElseThrow(() -> new DatabaseException("Cannot find claimant %s %s".formatted(this.repository.getEntityType().getSimpleName(), claimantId)));
        final E currentOwner = this.getById(currentOwnerId).orElseThrow(() -> new DatabaseException("Cannot find current owner %s %s".formatted(this.repository.getEntityType().getSimpleName(), currentOwnerId)));

        this.holderComponents.getHolderWriter().transferUniqueProperty(claimant, currentOwner, entityProperty, claimedValue, replacementValue);
        this.holderComponents.getHolderLookups().cache(currentOwner);
        this.holderComponents.getHolderLookups().cache(claimant);
    }

    @Override
    public final Optional<E> getByIndex(final EntityIndex<? super E> entityIndex, final Object... values) {
        final Optional<E> result = this.holderComponents.getHolderLookups().lookup(entityIndex, values);

        result.ifPresent(this.holderCache::track);

        return result;
    }

    @Override
    public final List<E> getPinned() {
        return this.holderCache.getPinned();
    }

    @Override
    public final void pin(final E entity) {
        this.holderCache.pin(entity);
    }

    @Override
    public final void unpin(final E entity) {
        this.holderCache.unpin(entity);
    }

    @Override
    public final void save(final E entity) {
        this.holderComponents.getHolderWriter().save(entity);
    }

    @Override
    public final void update(final E entity) {
        this.holderComponents.getHolderWriter().update(entity);
    }

    @Override
    public final void delete(final E entity) {
        this.holderComponents.getHolderWriter().delete(entity);
    }

    @Override
    public final void cache(final E entity) {
        this.holderCache.cache(entity);
    }

    @Override
    public final void evict(final E entity) {
        this.holderCache.evict(entity);
    }

    @Override
    public final long increment(final E entity, final EntityProperty<? super E, ? extends Number> entityProperty, final long delta) {
        return this.holderComponents.getHolderWriter().increment(entity, entityProperty, delta);
    }
}