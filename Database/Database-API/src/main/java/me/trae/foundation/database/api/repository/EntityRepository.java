package me.trae.foundation.database.api.repository;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.repository.index.IndexType;
import me.trae.foundation.database.api.tenant.TenantScope;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface EntityRepository<E extends Entity> {

    Class<E> getEntityType();

    String getTable();

    TenantScope getTenantScope();

    List<EntityProperty<?, ?>> getProperties();

    default Map<EntityProperty<E, ?>, IndexType> getIndexes() {
        return Collections.emptyMap();
    }

    Optional<E> findById(final UUID id);

    List<E> findManyById(final Collection<UUID> ids);

    Optional<E> findOne(final Query<E> query);

    List<E> findMany(final Query<E> query);

    long count(final Query<E> query);

    boolean exists(final Query<E> query);

    void save(final E entity);

    void delete(final E entity);
}