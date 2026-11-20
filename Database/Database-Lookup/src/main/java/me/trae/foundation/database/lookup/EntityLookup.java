package me.trae.foundation.database.lookup;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.repository.Repository;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.concurrency.InFlight;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@AllArgsConstructor
@Getter
public final class EntityLookup<EntityType extends Entity> {

    private final InFlight<UUID, Optional<EntityType>> loads = new InFlight<>();

    private final Set<Tier> tiers;
    private final LinkedHashMap<Tier, Storage<UUID, EntityType>> storageMap;
    private final Repository<EntityType> repository;
    private final InFlight<String, Optional<EntityType>> inFlight;
    private final LoadGuard<EntityType> guard;
    private final Backfill<EntityType> backfill;

    public Optional<EntityType> find(final UUID id, final Set<Tier> tiers) {
        return this.get(Tier.LOCAL, id, tiers).or(() -> this.inFlight.get(id + ":" + tiers, () -> this.load(id, tiers)));
    }

    public Optional<EntityType> find(final UUID id) {
        return this.get(Tier.LOCAL, id, this.tiers).or(() -> this.loads.get(id, () -> this.load(id, this.tiers)));
    }

    private Optional<EntityType> load(final UUID id, final Set<Tier> tiers) {
        if (!tiers.contains(Tier.POSTGRES)) {
            return this.get(Tier.REDIS, id, tiers);
        }

        return this.get(Tier.REDIS, id, tiers).or(() -> this.guard.load(id, () -> this.query(id, tiers)));
    }

    private Optional<EntityType> query(final UUID id, final Set<Tier> tiers) {
        if (!tiers.contains(Tier.POSTGRES)) {
            return Optional.empty();
        }

        final Optional<EntityType> entity = this.repository.findById(id);

        entity.ifPresent(found -> this.backfill.apply(found, tiers));

        return entity;
    }

    private Optional<EntityType> get(final Tier tier, final UUID id, final Set<Tier> tiers) {
        if (!tiers.contains(tier)) {
            return Optional.empty();
        }

        return Optional.ofNullable(this.storageMap.get(tier))
                .flatMap(storage -> storage.get(id));
    }
}