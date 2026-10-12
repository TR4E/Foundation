package me.trae.foundation.database.storage.redis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.storage.driver.RedisDriver;

import java.time.Duration;
import java.util.UUID;

@AllArgsConstructor
@Getter
public final class RedisInvalidation {

    private static final long LOCK_MILLIS = 30_000L;
    private static final long WAIT_MILLIS = 35_000L;

    private final String cacheKey;
    private final String missingKey;
    private final String lockKey;
    private final String channel;
    private final UUID entityId;
    private final String instanceId;

    public void dispatch(final RedisDriver redisDriver) {
        final String owner = UUID.randomUUID().toString();

        final long deadline = System.nanoTime() + Duration.ofMillis(WAIT_MILLIS).toNanos();

        while (System.nanoTime() < deadline) {
            if (redisDriver.tryAcquireLock(this.lockKey, owner, Duration.ofMillis(LOCK_MILLIS))) {
                try {
                    redisDriver.getCommands().del(this.cacheKey, this.missingKey);
                    redisDriver.publish(this.channel, "%s|%s".formatted(this.instanceId, this.entityId));
                    return;
                } finally {
                    redisDriver.releaseLock(this.lockKey, owner);
                }
            }

            final long remainingNanos = deadline - System.nanoTime();

            if (remainingNanos > 0L) {
                try {
                    Thread.sleep(Math.min(25L, Math.max(1L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remainingNanos))));
                } catch (final InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while dispatching Redis cache invalidation", exception);
                }
            }
        }

        throw new IllegalStateException("Timed out waiting to invalidate Redis cache entry %s".formatted(this.cacheKey));
    }
}