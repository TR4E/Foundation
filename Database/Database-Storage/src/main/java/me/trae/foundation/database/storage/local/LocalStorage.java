package me.trae.foundation.database.storage.local;

import me.trae.foundation.database.api.storage.Storage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalStorage<Key, Value> implements Storage<Key, Value> {

    public static final Duration DEFAULT_MAXIMUM_AGE = Duration.ofMinutes(5);

    private final Map<Key, CacheEntry<Value>> entryMap = new ConcurrentHashMap<>();

    private final Duration expiry;
    private final boolean refreshOnRead;
    private final Duration maximumAge;

    public LocalStorage() {
        this(null, false);
    }

    public LocalStorage(final Duration expiry) {
        this(expiry, false);
    }

    public LocalStorage(final Duration expiry, final boolean refreshOnRead) {
        this(expiry, refreshOnRead, refreshOnRead ? DEFAULT_MAXIMUM_AGE : expiry);
    }

    public LocalStorage(final Duration expiry, final boolean refreshOnRead, final Duration maximumAge) {
        this.expiry = expiry;
        this.refreshOnRead = refreshOnRead;
        this.maximumAge = maximumAge;
    }

    public void put(final Key key, final Value value, final Duration expiry) {
        this.entryMap.compute(key, (_, existing) -> new CacheEntry<>(value, expiry, existing != null && existing.isPinned(), this.maximumAge));
    }

    @Override
    public void put(final Key key, final Value value) {
        this.put(key, value, this.expiry);
    }

    @Override
    public void remove(final Key key) {
        this.entryMap.remove(key);
    }

    public void clear() {
        this.entryMap.clear();
    }

    @Override
    public Optional<Value> get(final Key key) {
        final CacheEntry<Value> cacheEntry = this.entryMap.get(key);

        if (cacheEntry == null) {
            return Optional.empty();
        }

        if (cacheEntry.isExpired()) {
            this.entryMap.remove(key, cacheEntry);

            return Optional.empty();
        }

        if (this.refreshOnRead) {
            cacheEntry.refreshExpiry();
        }

        return Optional.of(cacheEntry.getValue());
    }

    @Override
    public Map<Key, Value> getAll(final Collection<Key> keys) {
        final Map<Key, Value> valueMap = new LinkedHashMap<>();

        for (final Key key : keys) {
            this.get(key).ifPresent(value -> valueMap.put(key, value));
        }

        return valueMap;
    }

    @Override
    public boolean contains(final Key key) {
        return this.get(key).isPresent();
    }

    public void pin(final Key key, final Value value) {
        this.entryMap.put(key, new CacheEntry<>(value, null, true, null));
    }

    public void unpin(final Key key) {
        this.entryMap.computeIfPresent(key, (_, existing) -> new CacheEntry<>(existing.getValue(), this.expiry, false, this.maximumAge));
    }

    public boolean isPinned(final Key key) {
        final CacheEntry<Value> cacheEntry = this.entryMap.get(key);

        return cacheEntry != null && cacheEntry.isPinned();
    }

    public List<Value> getPinnedValues() {
        return this.entryMap.values().stream()
                .filter(CacheEntry::isPinned)
                .map(CacheEntry::getValue)
                .toList();
    }

    public List<Value> getValues() {
        return this.entryMap.values().stream()
                .filter(cacheEntry -> !cacheEntry.isExpired())
                .map(CacheEntry::getValue)
                .toList();
    }

    public List<Value> evictExpired() {
        final List<Value> evictedList = new ArrayList<>();

        this.entryMap.forEach((key, cacheEntry) -> {
            if (cacheEntry.isExpired() && this.entryMap.remove(key, cacheEntry)) {
                evictedList.add(cacheEntry.getValue());
            }
        });

        return evictedList;
    }
}