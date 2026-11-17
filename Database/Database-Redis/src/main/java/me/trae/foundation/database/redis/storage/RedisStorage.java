package me.trae.foundation.database.redis.storage;

import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.redis.driver.RedisDriver;

import java.util.Collection;
import java.util.Optional;

@AllArgsConstructor
public final class RedisStorage<Key, Value> implements Storage<Key, Value> {

    @Getter
    private final Namespace<Key, Value> namespace;

    private final RedisDriver driver;

    @Override
    public Optional<Value> get(final Key key) {
        return Optional.ofNullable(this.driver.getCommands().get(this.path(key))).map(this.namespace.getValueConverter()::deserialize);
    }

    @Override
    public void put(final Key key, final Value value) {
        final RedisCommands<String, String> commands = this.driver.getCommands();

        final String path = this.path(key);

        final String serialized = this.namespace.getValueConverter().serialize(value);

        if (this.namespace.getTtl().isZero()) {
            commands.set(path, serialized);
            return;
        }

        commands.set(path, serialized, SetArgs.Builder.px(this.namespace.getTtl().toMillis()));
    }

    @Override
    public void remove(final Key key) {
        this.driver.getCommands().del(this.path(key));
    }

    @Override
    public boolean contains(final Key key) {
        return this.driver.getCommands().exists(this.path(key)) > 0;
    }

    @Override
    public Collection<Value> values() {
        throw new UnsupportedOperationException("Redis cannot enumerate %s".formatted(this.namespace.getName()));
    }

    @Override
    public void refresh(final Key key) {
        this.driver.getCommands().pexpire(this.path(key), this.namespace.getTtl().toMillis());
    }

    private String path(final Key key) {
        return this.namespace.getName() + ":" + this.namespace.getKeyEncoder().apply(key);
    }
}