package me.trae.foundation.database.core.holder;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.holder.InstanceMode;
import me.trae.foundation.database.core.holder.change.ChangeTracker;
import me.trae.foundation.database.core.holder.claim.UniqueClaimer;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.local.LocalStorage;
import me.trae.foundation.database.storage.redis.RedisNamespace;
import me.trae.foundation.database.storage.redis.RedisStorage;

import java.time.Duration;
import java.util.UUID;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class HolderComponents<E extends Entity> {

    private final LocalStorage<UUID, E> localStorage;
    private final ChangeTracker<E> changeTracker;
    private final HolderLookups<E> holderLookups;
    private final HolderWriter<E> holderWriter;

    public static <E extends Entity> HolderComponents<E> create(final AbstractEntityRepository<E> repository, final InstanceMode instanceMode, final Duration localExpiry, final Duration redisExpiry) {
        final LocalStorage<UUID, E> localStorage = instanceMode == InstanceMode.SINGLETON ? new LocalStorage<>(localExpiry) : null;
        final ChangeTracker<E> changeTracker = new ChangeTracker<>(repository.getEntityType(), localExpiry);

        final RedisDriver redisDriver = repository.getCoreDatabase().getRedisDriver().orElse(null);
        final RedisStorage<E> redisStorage = redisDriver == null ? null : new RedisStorage<>(redisDriver, RedisNamespace.of(repository.getTable(), repository.getTenantId().orElse(null)), repository.getEntityType(), redisExpiry);

        final HolderLookups<E> holderLookups = new HolderLookups<>(instanceMode, repository, localStorage, redisStorage, redisDriver);
        final HolderWriter<E> holderWriter = new HolderWriter<>(repository, localStorage, redisStorage, changeTracker, new UniqueClaimer<>(repository, holderLookups, redisDriver));

        return new HolderComponents<>(localStorage, changeTracker, holderLookups, holderWriter);
    }
}