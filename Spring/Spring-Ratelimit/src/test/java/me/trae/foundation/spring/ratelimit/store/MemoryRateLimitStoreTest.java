package me.trae.foundation.spring.ratelimit.store;

import me.trae.foundation.spring.ratelimit.RateLimitData;
import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MemoryRateLimitStoreTest {

    private final MemoryRateLimitStore store = new MemoryRateLimitStore();

    @AfterEach
    void shutdown() {
        this.store.shutdown();
    }

    @Test
    void allowsUpToTheAttemptLimit() {
        final RateLimitData rateLimitData = data(60_000L, 3);

        assertEquals(0L, this.store.tryConsume("key", rateLimitData));
        assertEquals(0L, this.store.tryConsume("key", rateLimitData));
        assertEquals(0L, this.store.tryConsume("key", rateLimitData));
        assertTrue(this.store.tryConsume("key", rateLimitData) > 0L);
    }

    @Test
    void remainingMillisNeverExceedsTheWindow() {
        final RateLimitData rateLimitData = data(60_000L, 1);

        this.store.tryConsume("key", rateLimitData);

        final long remaining = this.store.tryConsume("key", rateLimitData);

        assertTrue(remaining > 0L && remaining <= 60_000L);
    }

    @Test
    void separateKeysHaveSeparateWindows() {
        final RateLimitData rateLimitData = data(60_000L, 1);

        assertEquals(0L, this.store.tryConsume("first", rateLimitData));
        assertEquals(0L, this.store.tryConsume("second", rateLimitData));
        assertTrue(this.store.tryConsume("first", rateLimitData) > 0L);
    }

    @Test
    void anAlreadyLimitedCallerDoesNotExtendItsOwnLockout() throws Exception {
        final RateLimitData rateLimitData = data(250L, 1);

        this.store.tryConsume("key", rateLimitData);

        final long first = this.store.tryConsume("key", rateLimitData);

        Thread.sleep(50L);

        final long second = this.store.tryConsume("key", rateLimitData);

        assertTrue(first > 0L);
        assertTrue(second > 0L);
        assertTrue(second < first);
    }

    @Test
    void windowExpiryStartsAFreshCount() throws Exception {
        final RateLimitData rateLimitData = data(150L, 1);

        assertEquals(0L, this.store.tryConsume("key", rateLimitData));
        assertTrue(this.store.tryConsume("key", rateLimitData) > 0L);

        Thread.sleep(200L);

        assertEquals(0L, this.store.tryConsume("key", rateLimitData));
    }

    @Test
    void concurrentCallersNeverExceedTheLimit() throws Exception {
        final RateLimitData rateLimitData = data(60_000L, 50);

        final int callerCount = 200;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch finish = new CountDownLatch(callerCount);
        final AtomicInteger allowed = new AtomicInteger();

        try (final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < callerCount; index++) {
                executorService.submit(() -> {
                    awaitQuietly(start);

                    if (this.store.tryConsume("key", rateLimitData) == 0L) {
                        allowed.incrementAndGet();
                    }

                    finish.countDown();
                });
            }

            start.countDown();
            finish.await();
        }

        assertEquals(50, allowed.get());
    }

    private static RateLimitData data(final long durationMillis, final int attempts) {
        return new RateLimitData("key", RateLimitScope.IP_ADDRESS, durationMillis, attempts);
    }

    private static void awaitQuietly(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}