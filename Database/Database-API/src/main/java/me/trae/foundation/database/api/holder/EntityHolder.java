package me.trae.foundation.database.api.holder;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.repository.EntityRepository;
import me.trae.foundation.database.api.repository.index.EntityIndex;
import me.trae.foundation.database.api.tenant.TenantScope;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface EntityHolder<E extends Entity, R extends EntityRepository<E>> {

    R getRepository();

    InstanceMode getInstanceMode();

    default TenantScope getTenantScope() {
        return this.getRepository().getTenantScope();
    }

    Optional<E> getById(final UUID id, final Set<LookupTier> lookupTiers);

    default Optional<E> getById(final UUID id) {
        return this.getById(id, EnumSet.allOf(LookupTier.class));
    }

    <Value> Optional<E> getByProperty(final EntityProperty<? super E, Value> entityProperty, final Value value);

    default <Value> Optional<E> getByProperty(final EntityProperty<? super E, Value> entityProperty, final Value value, final Set<LookupTier> lookupTiers) {
        if (!lookupTiers.equals(EnumSet.allOf(LookupTier.class))) {
            throw new UnsupportedOperationException("This holder does not support lookup tier selection for properties");
        }

        return this.getByProperty(entityProperty, value);
    }

    default <Value> void transferUniqueProperty(final UUID claimantId, final UUID currentOwnerId, final EntityProperty<? super E, Value> entityProperty, final Value claimedValue, final Value replacementValue) {
        throw new UnsupportedOperationException("This holder does not support unique property transfers");
    }

    default Optional<E> getByIndex(final EntityIndex<? super E> entityIndex, final Object... values) {
        throw new UnsupportedOperationException("This holder does not support indexed lookups");
    }

    default Optional<E> getByIndex(final EntityIndex<? super E> entityIndex, final Set<LookupTier> lookupTiers, final Object... values) {
        if (!lookupTiers.equals(EnumSet.allOf(LookupTier.class))) {
            throw new UnsupportedOperationException("This holder does not support lookup tier selection for indexes");
        }

        return this.getByIndex(entityIndex, values);
    }

    List<E> getPinned();

    void pin(final E entity);

    void unpin(final E entity);

    default void save(final E entity, final Runnable commitRunnable, final Runnable failureRunnable) {
        throw new UnsupportedOperationException("This holder does not support save callbacks");
    }

    default void save(final E entity, final Runnable commitRunnable) {
        this.save(entity, commitRunnable, () -> {});
    }

    void save(final E entity);

    /**
     * Runs the commit callback immediately when no write is needed, after Redis synchronization for
     * non-persistent changes, or after the database commit for persistent changes.
     * Callbacks run on the calling thread for immediate completion, or on the batch processing thread.
     * A commit does not acknowledge cache refreshes in other instances.
     */
    default void update(final E entity, final Runnable commitRunnable, final Runnable failureRunnable) {
        throw new UnsupportedOperationException("This holder does not support update callbacks");
    }

    default void update(final E entity, final Runnable commitRunnable) {
        this.update(entity, commitRunnable, () -> {});
    }

    default void update(final E entity) {
        throw new UnsupportedOperationException("This holder does not support entity updates");
    }

    default void delete(final E entity, final Runnable commitRunnable, final Runnable failureRunnable) {
        throw new UnsupportedOperationException("This holder does not support delete callbacks");
    }

    default void delete(final E entity, final Runnable commitRunnable) {
        this.delete(entity, commitRunnable, () -> {});
    }

    void delete(final E entity);

    void cache(final E entity);

    void evict(final E entity);

    long increment(final E entity, final EntityProperty<? super E, ? extends Number> entityProperty, final long delta);
}