package me.trae.foundation.utilities.data;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class Data {

    private final ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<>();

    public void put(final String key, final Object value) {
        if (value == null) {
            this.map.remove(key);
            return;
        }

        this.map.put(key, value);
    }

    public void remove(final String key) {
        this.map.remove(key);
    }

    public <T> Optional<T> get(final Class<T> type, final String key, final T defaultValue) {
        return Optional.ofNullable(this.map.get(key))
                .filter(type::isInstance)
                .map(type::cast)
                .or(() -> Optional.ofNullable(defaultValue));
    }

    public <T> Optional<T> get(final Class<T> type, final String key) {
        return this.get(type, key, null);
    }

    public boolean contains(final String key) {
        return this.map.containsKey(key);
    }

    public int size() {
        return this.map.size();
    }

    public List<String> getKeys() {
        return List.copyOf(this.map.keySet());
    }
}