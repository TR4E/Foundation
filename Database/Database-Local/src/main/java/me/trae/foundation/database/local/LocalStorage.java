package me.trae.foundation.database.local;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.api.storage.Storage;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor
public final class LocalStorage<Key, Value> implements Storage<Key, Value> {

    private final ConcurrentHashMap<String, Value> valueMap = new ConcurrentHashMap<>();

    @Getter
    private final Namespace<Key, Value> namespace;

    @Override
    public Optional<Value> get(final Key key) {
        return Optional.ofNullable(this.valueMap.get(this.path(key)));
    }

    @Override
    public void put(final Key key, final Value value) {
        this.valueMap.put(this.path(key), value);
    }

    @Override
    public void remove(final Key key) {
        this.valueMap.remove(this.path(key));
    }

    @Override
    public boolean contains(final Key key) {
        return this.valueMap.containsKey(this.path(key));
    }

    @Override
    public Collection<Value> values() {
        return this.valueMap.values();
    }

    @Override
    public void refresh(final Key key) {
    }

    public void clear() {
        this.valueMap.clear();
    }

    private String path(final Key key) {
        return this.namespace.getKeyEncoder().apply(key);
    }
}