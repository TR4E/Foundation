package me.trae.foundation.database.lookup;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.api.tier.Tier;

import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

@AllArgsConstructor
@Getter
public final class Backfill<EntityType extends Entity> {

    private final LinkedHashMap<Tier, Storage<UUID, EntityType>> storageMap;
    private final Predicate<UUID> held;

    public void apply(final EntityType entity, final Set<Tier> tiers) {
        this.storageMap.forEach((tier, storage) -> this.put(tier, storage, entity, tiers));
    }

    private void put(final Tier tier, final Storage<UUID, EntityType> storage, final EntityType entity, final Set<Tier> tiers) {
        if (!tiers.contains(tier)) {
            return;
        }

        if (tier == Tier.LOCAL && !this.held.test(entity.getId())) {
            return;
        }

        storage.put(entity.getId(), entity);
    }
}