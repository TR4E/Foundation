package me.trae.foundation.database.api;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.repository.EntityRepository;

import java.util.List;
import java.util.Optional;

public interface Database {

    boolean isReady();

    List<EntityRepository<?>> getRepositories();

    <E extends Entity> Optional<EntityRepository<E>> getRepository(final Class<E> entityType);
}