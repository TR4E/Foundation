package me.trae.foundation.database.lookup;

import me.trae.foundation.database.api.holder.LookupTier;
import me.trae.foundation.database.lookup.inflight.InFlightLookup;
import me.trae.foundation.database.lookup.inflight.LookupKey;
import me.trae.foundation.database.lookup.step.LookupStep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TieredLookup<Key, Value> {

    private final InFlightLookup<Value> inFlightLookup = new InFlightLookup<>();

    private final List<LookupStep<Key, Value>> stepList;

    public TieredLookup(final List<LookupStep<Key, Value>> stepList) {
        this.stepList = List.copyOf(stepList);
    }

    public Optional<Value> lookup(final Key key, final Set<LookupTier> lookupTiers) {
        return this.inFlightLookup.compute(new LookupKey(key, Set.copyOf(lookupTiers)), () -> this.walk(key, lookupTiers));
    }

    public Optional<Value> lookup(final Key key) {
        return this.lookup(key, EnumSet.allOf(LookupTier.class));
    }

    public Map<Key, Value> lookupAll(final Collection<Key> keys, final Set<LookupTier> lookupTiers) {
        final Map<Key, Value> foundMap = new LinkedHashMap<>();
        final Set<Key> remainingSet = new LinkedHashSet<>(keys);
        final List<LookupStep<Key, Value>> visitedList = new ArrayList<>();

        for (final LookupStep<Key, Value> lookupStep : this.stepList) {
            if (remainingSet.isEmpty()) {
                break;
            }

            if (!lookupTiers.contains(lookupStep.getLookupTier())) {
                continue;
            }

            lookupStep.readAll(List.copyOf(remainingSet)).forEach((key, value) -> {
                foundMap.put(key, value);
                remainingSet.remove(key);
                visitedList.forEach(visited -> visited.write(key, value));
            });

            visitedList.add(lookupStep);
        }

        final Map<Key, Value> resultMap = new LinkedHashMap<>();

        for (final Key key : keys) {
            final Value value = foundMap.get(key);

            if (value != null) {
                resultMap.put(key, value);
            }
        }

        return resultMap;
    }

    public Map<Key, Value> lookupAll(final Collection<Key> keys) {
        return this.lookupAll(keys, EnumSet.allOf(LookupTier.class));
    }

    private Optional<Value> walk(final Key key, final Set<LookupTier> lookupTiers) {
        final List<LookupStep<Key, Value>> visitedList = new ArrayList<>();

        for (final LookupStep<Key, Value> lookupStep : this.stepList) {
            if (!lookupTiers.contains(lookupStep.getLookupTier())) {
                continue;
            }

            final Optional<Value> result = lookupStep.read(key);
            if (result.isEmpty()) {
                visitedList.add(lookupStep);
                continue;
            }

            result.ifPresent(value -> visitedList.forEach(visited -> visited.write(key, value)));

            return result;
        }

        return Optional.empty();
    }
}