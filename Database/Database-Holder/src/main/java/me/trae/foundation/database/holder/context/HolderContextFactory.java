package me.trae.foundation.database.holder.context;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.repository.Repository;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.holder.entity.EntityHolder;

import java.util.Set;

public interface HolderContextFactory {

    <EntityType extends Entity> HolderContext<EntityType> create(final Repository<EntityType> repository, final Set<Tier> tiers);

    void register(final EntityHolder<?, ?> holder);
}