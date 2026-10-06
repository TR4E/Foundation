package me.trae.foundation.database.api.holder;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.repository.EntityRepository;
import me.trae.foundation.database.api.tenant.TenantScope;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface EntityHolder<E extends Entity> {

    EntityRepository<E> getRepository();

    InstanceMode getInstanceMode();

    default TenantScope getTenantScope() {
        return this.getRepository().getTenantScope();
    }

    Optional<E> getById(final UUID id, final Set<LookupTier> lookupTiers);

    default Optional<E> getById(final UUID id) {
        return this.getById(id, EnumSet.allOf(LookupTier.class));
    }

    <V> Optional<E> getByProperty(final EntityProperty<? super E, V> entityProperty, final V value);

    List<E> getPinned();

    void pin(final E entity);

    void unpin(final E entity);

    void save(final E entity);

    void delete(final E entity);

    void cache(final E entity);

    void evict(final E entity);

    long increment(final E entity, final EntityProperty<? super E, ? extends Number> entityProperty, final long delta);
}