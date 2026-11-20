package me.trae.foundation.database.redis.storage;

import io.lettuce.core.api.sync.RedisCommands;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.api.storage.VersionedEntity;
import me.trae.foundation.database.redis.codec.EntityCodec;
import me.trae.foundation.database.redis.driver.RedisDriver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@AllArgsConstructor
public final class RedisEntityStorage<EntityType extends Entity> implements Storage<UUID, EntityType> {

    private static final String VERSION = "version";

    @Getter
    private final Namespace<UUID, EntityType> namespace;

    private final RedisDriver driver;
    private final EntityCodec<EntityType> codec;

    @Override
    public Optional<EntityType> get(final UUID key) {
        final Map<String, String> fieldMap = this.driver.getCommands().hgetall(this.path(key));

        return fieldMap.isEmpty() ? Optional.empty() : Optional.of(this.codec.decode(key, fieldMap));
    }

    @Override
    public void put(final UUID key, final EntityType value) {
        final RedisCommands<String, String> commands = this.driver.getCommands();

        final String path = this.path(key);

        final Map<String, String> fieldMap = this.codec.encode(value);

        final List<String> missing = this.codec.getColumns().stream()
                .filter(column -> !fieldMap.containsKey(column))
                .collect(Collectors.toCollection(ArrayList::new));

        if (!missing.isEmpty()) {
            commands.hdel(path, missing.toArray(String[]::new));
        }

        if (!fieldMap.isEmpty()) {
            commands.hset(path, fieldMap);
        }

        if (!this.namespace.getTtl().isZero()) {
            commands.pexpire(path, this.namespace.getTtl().toMillis());
        }
    }

    @Override
    public void remove(final UUID key) {
        this.driver.getCommands().del(this.path(key));
    }

    @Override
    public boolean contains(final UUID key) {
        return this.driver.getCommands().exists(this.path(key)) > 0;
    }

    @Override
    public Collection<EntityType> values() {
        throw new UnsupportedOperationException("Redis cannot enumerate %s".formatted(this.namespace.getName()));
    }

    @Override
    public void refresh(final UUID key) {
        this.driver.getCommands().pexpire(this.path(key), this.namespace.getTtl().toMillis());
    }

    @Override
    public void refreshAll(final Collection<UUID> keys) {
        this.driver.expire(keys.stream().map(this::path).collect(Collectors.toCollection(ArrayList::new)), this.namespace.getTtl());
    }

    public Optional<VersionedEntity<EntityType>> snapshot(final UUID key) {
        final Map<String, String> fieldMap = this.driver.getCommands().hgetall(this.path(key));

        if (fieldMap.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new VersionedEntity<>(this.codec.decode(key, fieldMap), version(fieldMap)));
    }

    public Map<UUID, VersionedEntity<EntityType>> snapshots(final Collection<UUID> keys) {
        final List<UUID> ordered = new ArrayList<>(keys);

        final List<Map<String, String>> hashes = this.driver.hashes(ordered.stream().map(this::path).collect(Collectors.toCollection(ArrayList::new)));

        final LinkedHashMap<UUID, VersionedEntity<EntityType>> snapshotMap = new LinkedHashMap<>();

        for (int index = 0; index < hashes.size(); index++) {
            this.snapshot(snapshotMap, ordered.get(index), hashes.get(index));
        }

        return snapshotMap;
    }

    public Map<UUID, Long> versions(final Collection<UUID> keys) {
        final List<UUID> ordered = new ArrayList<>(keys);

        final List<String> values = this.driver.fields(ordered.stream().map(this::path).collect(Collectors.toCollection(ArrayList::new)), VERSION);

        final LinkedHashMap<UUID, Long> versionMap = new LinkedHashMap<>();

        for (int index = 0; index < values.size(); index++) {
            versionMap.put(ordered.get(index), values.get(index) == null ? 0L : Long.parseLong(values.get(index)));
        }

        return versionMap;
    }

    public String path(final UUID key) {
        return this.namespace.getName() + ":" + this.namespace.getKeyEncoder().apply(key);
    }

    public String channel(final UUID key) {
        return channel(this.path(key));
    }

    public static String channel(final String path) {
        return path + ":events";
    }

    public long version(final UUID key) {
        final String version = this.driver.getCommands().hget(this.path(key), VERSION);

        return version == null ? 0L : Long.parseLong(version);
    }

    private void snapshot(final LinkedHashMap<UUID, VersionedEntity<EntityType>> snapshotMap, final UUID key, final Map<String, String> fieldMap) {
        if (fieldMap.isEmpty()) {
            return;
        }

        snapshotMap.put(key, new VersionedEntity<>(this.codec.decode(key, fieldMap), version(fieldMap)));
    }

    private static long version(final Map<String, String> fieldMap) {
        final String version = fieldMap.get(VERSION);

        return version == null ? 0L : Long.parseLong(version);
    }
}