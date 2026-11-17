package me.trae.foundation.database.api;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.holder.Holder;
import me.trae.foundation.database.api.repository.Repository;

public interface Database {

    void start();

    void stop();

    boolean isReady();

    <EntityType extends Entity> Holder<EntityType> getHolder(final Class<EntityType> entityType);

    <EntityType extends Entity> Repository<EntityType> getRepository(final Class<EntityType> entityType);
}