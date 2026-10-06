package me.trae.foundation.database.lookup.inflight;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import me.trae.foundation.database.api.holder.LookupTier;

import java.util.Set;

@EqualsAndHashCode
@AllArgsConstructor
public final class LookupKey {

    private final Object key;
    private final Set<LookupTier> lookupTiers;
}