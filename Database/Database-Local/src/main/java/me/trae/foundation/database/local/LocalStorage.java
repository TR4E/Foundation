package me.trae.foundation.database.local;

import lombok.Getter;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.api.storage.Storage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class LocalStorage<Key, Value> implements Storage<Key, Value> {

    private static final int SAMPLE = 16;

    private final ConcurrentHashMap<Key, Value> valueMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key, Long> stampMap = new ConcurrentHashMap<>();

    private volatile Consumer<Key> eviction = _ -> {};

    @Getter
    private final Namespace<Key, Value> namespace;

    private final int limit;
    private final Duration ttl;

    public LocalStorage(final Namespace<Key, Value> namespace, final int limit, final Duration ttl) {
        this.namespace = namespace;
        this.limit = limit;
        this.ttl = ttl;
    }

    @Override
    public Optional<Value> get(final Key key) {
        final Value value = this.valueMap.get(key);

        if (value == null) {
            return Optional.empty();
        }

        if (this.expired(key)) {
            this.evict(key);

            return Optional.empty();
        }

        return Optional.of(value);
    }

    @Override
    public void put(final Key key, final Value value) {
        this.valueMap.put(key, value);

        if (this.stamped()) {
            this.stampMap.put(key, System.currentTimeMillis());
        }

        this.bound();
    }

    @Override
    public void remove(final Key key) {
        this.valueMap.remove(key);
        this.stampMap.remove(key);
    }

    @Override
    public boolean remove(final Key key, final Value value) {
        if (!this.valueMap.remove(key, value)) {
            return false;
        }

        this.stampMap.remove(key);

        return true;
    }

    @Override
    public boolean contains(final Key key) {
        return this.valueMap.containsKey(key) && !this.expired(key);
    }

    @Override
    public Collection<Value> values() {
        if (this.ttl.isZero()) {
            return this.valueMap.values();
        }

        return this.valueMap.entrySet().stream()
                .filter(entry -> !this.expired(entry.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public void refresh(final Key key) {
        if (!this.stamped()) {
            return;
        }

        this.stampMap.replace(key, System.currentTimeMillis());
    }

    @Override
    public void onEviction(final Consumer<Key> listener) {
        this.eviction = listener;
    }

    public void clear() {
        this.valueMap.clear();
        this.stampMap.clear();
    }

    private boolean stamped() {
        return !this.ttl.isZero() || this.limit > 0;
    }

    private boolean expired(final Key key) {
        if (this.ttl.isZero()) {
            return false;
        }

        final Long stamp = this.stampMap.get(key);

        return stamp != null && System.currentTimeMillis() - stamp >= this.ttl.toMillis();
    }

    private void bound() {
        if (this.limit <= 0) {
            return;
        }

        while (this.valueMap.size() > this.limit) {
            final Key oldest = this.oldest();

            if (oldest == null) {
                return;
            }

            this.evict(oldest);
        }
    }

    private Key oldest() {
        Key oldest = null;
        long oldestStamp = Long.MAX_VALUE;
        int sampled = 0;

        for (final Map.Entry<Key, Long> entry : this.stampMap.entrySet()) {
            if (entry.getValue() < oldestStamp) {
                oldestStamp = entry.getValue();
                oldest = entry.getKey();
            }

            sampled++;

            if (sampled >= SAMPLE) {
                break;
            }
        }

        return oldest;
    }

    private void evict(final Key key) {
        if (this.valueMap.remove(key) == null) {
            return;
        }

        this.stampMap.remove(key);

        this.eviction.accept(key);
    }
}