package me.trae.foundation.database.core.holder;

import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.holder.EntityHolder;
import me.trae.foundation.database.api.holder.InstanceMode;
import me.trae.foundation.database.api.holder.LookupTier;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public abstract class AbstractEntityHolder<E extends Entity> implements EntityHolder<E> {

    @Getter
    private final AbstractEntityRepository<E> repository;

    @Getter
    private final InstanceMode instanceMode;

    private final HolderComponents<E> holderComponents;
    private final HolderCache<E> holderCache;

    protected AbstractEntityHolder(final AbstractEntityRepository<E> repository, final InstanceMode instanceMode, final Duration localExpiry, final Duration redisExpiry) {
        this.repository = repository;
        this.instanceMode = instanceMode;
        this.holderComponents = HolderComponents.create(repository, instanceMode, localExpiry, redisExpiry);
        this.holderCache = new HolderCache<>(instanceMode, this.holderComponents, repository.getCoreDatabase().getBatchQueue());

        repository.getCoreDatabase().getBatchQueue().addPreFlushTask(this.holderCache::flushAndEvict);
    }

    protected AbstractEntityHolder(final AbstractEntityRepository<E> repository, final InstanceMode instanceMode) {
        this(repository, instanceMode, Duration.ofMinutes(30), Duration.ofHours(1));
    }

    @Override
    public Optional<E> getById(final UUID id, final Set<LookupTier> lookupTiers) {
        final Optional<E> result = this.holderComponents.getHolderLookups().getIdLookup().lookup(id, lookupTiers);

        result.ifPresent(this.holderCache::track);

        return result;
    }

    @Override
    public <Value> Optional<E> getByProperty(final EntityProperty<? super E, Value> entityProperty, final Value value) {
        final Optional<E> result = this.holderComponents.getHolderLookups().lookup(entityProperty, value);

        result.ifPresent(this.holderCache::track);

        return result;
    }

    @Override
    public List<E> getPinned() {
        return this.holderCache.getPinned();
    }

    @Override
    public void pin(final E entity) {
        this.holderCache.pin(entity);
    }

    @Override
    public void unpin(final E entity) {
        this.holderCache.unpin(entity);
    }

    @Override
    public void save(final E entity) {
        this.holderComponents.getHolderWriter().save(entity);
    }

    @Override
    public void delete(final E entity) {
        this.holderComponents.getHolderWriter().delete(entity);
    }

    @Override
    public void cache(final E entity) {
        this.holderCache.cache(entity);
    }

    @Override
    public void evict(final E entity) {
        this.holderCache.evict(entity);
    }

    @Override
    public long increment(final E entity, final EntityProperty<? super E, ? extends Number> entityProperty, final long delta) {
        return this.holderComponents.getHolderWriter().increment(entity, entityProperty, delta);
    }
}