package me.trae.foundation.database.api.holder;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.SynchronizationException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.repository.EntityRepository;
import me.trae.foundation.database.api.repository.index.EntityIndex;
import me.trae.foundation.database.api.tenant.TenantScope;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

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

    /**
     * Runs the commit callback after the write commits, without waiting for owner acknowledgement.
     * Every user callback runs asynchronously on a dedicated framework virtual thread, including
     * unchanged updates, synchronous rejection and Redis-disabled operations. No user callback runs
     * inline on a caller, Netty, subscription or database worker. Failure means the write did not commit.
     */
    default void save(final E entity, final Runnable commitRunnable, final Runnable failureRunnable) {
        throw new UnsupportedOperationException("This holder does not support save callbacks");
    }

    default void save(final E entity, final Runnable commitRunnable) {
        this.save(entity, commitRunnable, () -> {});
    }

    /**
     * Explicitly waits for owner refreshes after the write. Timeout starts when synchronization begins
     * after commit. A committed but unsynchronized operation invokes synchronizationFailureConsumer,
     * never failureRunnable. Late acknowledgements cannot invoke a suppressed success callback.
     * Every terminal callback runs on a dedicated framework virtual thread.
     */
    default void saveAndSynchronize(final E entity, final Duration timeout, final Runnable synchronizedRunnable, final Runnable failureRunnable, final Consumer<SynchronizationException> synchronizationFailureConsumer) {
        throw new UnsupportedOperationException("This holder does not support synchronized save");
    }

    default void saveAndSynchronize(final E entity, final Runnable synchronizedRunnable, final Runnable failureRunnable, final Consumer<SynchronizationException> synchronizationFailureConsumer) {
        this.saveAndSynchronize(entity, Duration.ofSeconds(5), synchronizedRunnable, failureRunnable, synchronizationFailureConsumer);
    }

    void save(final E entity);

    /**
     * Completes after the write, without waiting for peer caches. An unchanged update schedules its
     * callback without I/O. All user callbacks use the dedicated virtual-thread rule documented on save.
     * Owner notification and repair failures never invoke the write-failure runnable.
     */
    default void update(final E entity, final Runnable commitRunnable, final Runnable failureRunnable) {
        throw new UnsupportedOperationException("This holder does not support update callbacks");
    }

    default void update(final E entity, final Runnable commitRunnable) {
        this.update(entity, commitRunnable, () -> {});
    }

    /**
     * Explicitly waits for owner refreshes after the write. Timeout starts when synchronization begins
     * after commit. A committed but unsynchronized operation invokes synchronizationFailureConsumer,
     * never failureRunnable. Late acknowledgements cannot invoke a suppressed success callback.
     * Every terminal callback runs on a dedicated framework virtual thread.
     */
    default void updateAndSynchronize(final E entity, final Duration timeout, final Runnable synchronizedRunnable, final Runnable failureRunnable, final Consumer<SynchronizationException> synchronizationFailureConsumer) {
        throw new UnsupportedOperationException("This holder does not support synchronized update");
    }

    default void updateAndSynchronize(final E entity, final Runnable synchronizedRunnable, final Runnable failureRunnable, final Consumer<SynchronizationException> synchronizationFailureConsumer) {
        this.updateAndSynchronize(entity, Duration.ofSeconds(5), synchronizedRunnable, failureRunnable, synchronizationFailureConsumer);
    }

    default void update(final E entity) {
        throw new UnsupportedOperationException("This holder does not support entity updates");
    }

    /**
     * Completes after deletion commits; callbacks use the same dedicated virtual-thread rule as save.
     */
    default void delete(final E entity, final Runnable commitRunnable, final Runnable failureRunnable) {
        throw new UnsupportedOperationException("This holder does not support delete callbacks");
    }

    default void delete(final E entity, final Runnable commitRunnable) {
        this.delete(entity, commitRunnable, () -> {});
    }

    /**
     * Explicitly waits for owner refreshes after the write. Timeout starts when synchronization begins
     * after commit. A committed but unsynchronized operation invokes synchronizationFailureConsumer,
     * never failureRunnable. Late acknowledgements cannot invoke a suppressed success callback.
     * Every terminal callback runs on a dedicated framework virtual thread.
     */
    default void deleteAndSynchronize(final E entity, final Duration timeout, final Runnable synchronizedRunnable, final Runnable failureRunnable, final Consumer<SynchronizationException> synchronizationFailureConsumer) {
        throw new UnsupportedOperationException("This holder does not support synchronized delete");
    }

    default void deleteAndSynchronize(final E entity, final Runnable synchronizedRunnable, final Runnable failureRunnable, final Consumer<SynchronizationException> synchronizationFailureConsumer) {
        this.deleteAndSynchronize(entity, Duration.ofSeconds(5), synchronizedRunnable, failureRunnable, synchronizationFailureConsumer);
    }

    void delete(final E entity);

    void cache(final E entity);

    void evict(final E entity);

    long increment(final E entity, final EntityProperty<? super E, ? extends Number> entityProperty, final long delta);
}