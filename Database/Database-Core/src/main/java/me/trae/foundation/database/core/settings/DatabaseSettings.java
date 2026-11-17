package me.trae.foundation.database.core.settings;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.tier.Tier;

import java.time.Duration;
import java.util.Set;

@AllArgsConstructor
@Getter
public final class DatabaseSettings {

    private final Set<Tier> tiers;
    private final String tenant;
    private final Duration entityTtl, refreshInterval, batchInterval, markerLease, markerTimeout;
}