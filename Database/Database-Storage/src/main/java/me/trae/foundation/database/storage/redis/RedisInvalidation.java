package me.trae.foundation.database.storage.redis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.storage.driver.RedisDriver;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@Getter
public final class RedisInvalidation {

    private static final long LOCK_MILLIS = 30_000L;
    private static final long WAIT_MILLIS = 35_000L;

    private final String cacheKey, missingKey, lockKey, channel;
    private final UUID entityId;
    private final String instanceId;
    private final boolean invalidateCache;
    private final boolean removeSharedState;

    public RedisInvalidation(final String cacheKey, final String missingKey, final String lockKey, final String channel, final UUID entityId, final String instanceId, final boolean invalidateCache) {
        this(cacheKey, missingKey, lockKey, channel, entityId, instanceId, invalidateCache, false);
    }

    public RedisInvalidation(final String cacheKey, final String missingKey, final String lockKey, final String channel, final UUID entityId, final String instanceId) {
        this(cacheKey, missingKey, lockKey, channel, entityId, instanceId, true);
    }

    public void dispatch(final RedisDriver redisDriver) {
        final String owner = UUID.randomUUID().toString();

        final long deadline = System.nanoTime() + Duration.ofMillis(WAIT_MILLIS).toNanos();

        while (System.nanoTime() < deadline) {
            if (redisDriver.tryAcquireLock(this.lockKey, owner, Duration.ofMillis(LOCK_MILLIS))) {
                try {
                    if (this.invalidateCache) {
                        redisDriver.getCommands().del(this.cacheKey, this.missingKey);
                    } else {
                        redisDriver.getCommands().del(this.missingKey);
                    }

                    if (this.removeSharedState) {
                        redisDriver.getCommands().del(this.getSharedKey());
                    }

                    redisDriver.publish(this.channel, "%s|%s".formatted(this.instanceId, this.entityId));
                    return;
                } finally {
                    redisDriver.releaseLock(this.lockKey, owner);
                }
            }

            final long remainingNanos = deadline - System.nanoTime();

            if (remainingNanos > 0L) {
                try {
                    Thread.sleep(Math.clamp(TimeUnit.NANOSECONDS.toMillis(remainingNanos), 1L, 25L));
                } catch (final InterruptedException exception) {
                    Thread.currentThread().interrupt();

                    throw new IllegalStateException("Interrupted while dispatching Redis cache invalidation", exception);
                }
            }
        }

        throw new IllegalStateException("Timed out waiting to invalidate Redis cache entry %s".formatted(this.cacheKey));
    }

    public RedisInvalidation withInstanceId(final String instanceId) {
        return new RedisInvalidation(
                this.cacheKey,
                this.missingKey,
                this.lockKey,
                this.channel,
                this.entityId,
                instanceId,
                this.invalidateCache,
                this.removeSharedState
        );
    }

    public RedisInvalidation withSharedStateRemoval() {
        return new RedisInvalidation(
                this.cacheKey,
                this.missingKey,
                this.lockKey,
                this.channel,
                this.entityId,
                this.instanceId,
                this.invalidateCache,
                true
        );
    }

    private String getSharedKey() {
        return "%scache:shared:%s".formatted(this.cacheKey.substring(0, this.cacheKey.length() - this.entityId.toString().length()), this.entityId);
    }
}