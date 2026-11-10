package me.trae.foundation.spring.ratelimit.store;

import me.trae.foundation.spring.common.FoundationProperties;
import me.trae.foundation.spring.common.address.IpAddressProperties;
import me.trae.foundation.spring.common.address.IpAddressResolver;
import me.trae.foundation.spring.ratelimit.RateLimitData;
import me.trae.foundation.spring.ratelimit.annotation.RateLimit;
import me.trae.foundation.spring.ratelimit.interceptor.RateLimitInterceptor;
import me.trae.foundation.spring.ratelimit.registry.RateLimitRegistry;
import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;
import me.trae.foundation.spring.security.account.EmptySecurityAccountProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void existingKeysKeepTheirBudgetAtCapacityWhileNewKeysAreRefused() {
        final MemoryRateLimitStore memoryRateLimitStore = new MemoryRateLimitStore(1);

        try {
            final RateLimitData rateLimitData = data(60000L, 2);

            assertEquals(0L, memoryRateLimitStore.tryConsume("existing", rateLimitData));
            assertTrue(memoryRateLimitStore.tryConsume("new", rateLimitData) > 0L);
            assertEquals(0L, memoryRateLimitStore.tryConsume("existing", rateLimitData));
            assertTrue(memoryRateLimitStore.tryConsume("existing", rateLimitData) > 0L);
            assertEquals(1, windows(memoryRateLimitStore).size());
        } finally {
            memoryRateLimitStore.shutdown();
        }
    }

    @Test
    void anExpiredEntryIsSweptOnTheCapacityPathAndTheNewKeyIsAccepted() {
        final MemoryRateLimitStore memoryRateLimitStore = new MemoryRateLimitStore(1);

        try {
            assertEquals(0L, memoryRateLimitStore.tryConsume("expired", data(60000L, 1)));
            windows(memoryRateLimitStore).put("expired", new RateLimitWindow(System.currentTimeMillis() - 1L));

            assertEquals(0L, memoryRateLimitStore.tryConsume("new", data(60000L, 1)));
            assertEquals(1, windows(memoryRateLimitStore).size());
            assertTrue(windows(memoryRateLimitStore).containsKey("new"));
        } finally {
            memoryRateLimitStore.shutdown();
        }
    }

    @Test
    void anImmediateCapacityCheckDoesNotSweepExpiredEntriesAgain() {
        final MemoryRateLimitStore memoryRateLimitStore = new MemoryRateLimitStore(2);

        try {
            memoryRateLimitStore.tryConsume("first", data(60000L, 1));
            memoryRateLimitStore.tryConsume("second", data(60000L, 1));
            final AtomicLong lastSweepMillis = (AtomicLong) ReflectionTestUtils.getField(memoryRateLimitStore, "lastSweepMillis");
            lastSweepMillis.set(System.currentTimeMillis() + 1000L);
            windows(memoryRateLimitStore).put("first", new RateLimitWindow(System.currentTimeMillis() - 1L));

            assertTrue(memoryRateLimitStore.tryConsume("third", data(60000L, 1)) > 0L);
            assertTrue(windows(memoryRateLimitStore).containsKey("first"));
            lastSweepMillis.set(0L);
            assertEquals(0L, memoryRateLimitStore.tryConsume("third", data(60000L, 1)));
            final long sweepMillis = lastSweepMillis.get();
            windows(memoryRateLimitStore).put("second", new RateLimitWindow(System.currentTimeMillis() - 1L));

            assertTrue(memoryRateLimitStore.tryConsume("fourth", data(60000L, 1)) > 0L);
            assertTrue(windows(memoryRateLimitStore).containsKey("second"));
            assertEquals(sweepMillis, lastSweepMillis.get());
            lastSweepMillis.set(0L);

            assertEquals(0L, memoryRateLimitStore.tryConsume("fourth", data(60000L, 1)));
        } finally {
            memoryRateLimitStore.shutdown();
        }
    }

    @Test
    void accountingTracksAttemptsRemainingTimeAndExpiryWithoutSleeping() {
        final RateLimitData rateLimitData = data(60000L, 2);

        assertEquals(0L, this.store.tryConsume("accounting", rateLimitData));
        assertEquals(1, windows(this.store).get("accounting").getAttempts());
        assertEquals(0L, this.store.tryConsume("accounting", rateLimitData));
        assertEquals(2, windows(this.store).get("accounting").getAttempts());
        final long firstRemaining = this.store.tryConsume("accounting", rateLimitData);
        final RateLimitWindow shorterWindow = new RateLimitWindow(System.currentTimeMillis() + 10000L);
        shorterWindow.addAttempt();
        shorterWindow.addAttempt();
        windows(this.store).put("accounting", shorterWindow);

        assertTrue(this.store.tryConsume("accounting", rateLimitData) < firstRemaining);
        windows(this.store).put("accounting", new RateLimitWindow(System.currentTimeMillis() - 1L));

        assertEquals(0L, this.store.tryConsume("accounting", rateLimitData));
        assertEquals(1, windows(this.store).get("accounting").getAttempts());
    }

    @Test
    void concurrentDistinctKeysHaveIndependentExactAllowances() throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<Long>> firstFutureList = new ArrayList<>();
        final List<Future<Long>> secondFutureList = new ArrayList<>();

        try (final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            for (final int ignored : IntStream.range(0, 100).toArray()) {
                firstFutureList.add(executorService.submit(() -> {
                    start.await();

                    return this.store.tryConsume("first-concurrent", data(60000L, 7));
                }));
                secondFutureList.add(executorService.submit(() -> {
                    start.await();

                    return this.store.tryConsume("second-concurrent", data(60000L, 11));
                }));
            }

            start.countDown();

            assertEquals(7L, allowedCount(firstFutureList));
            assertEquals(11L, allowedCount(secondFutureList));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, RateLimitWindow> windows(final MemoryRateLimitStore memoryRateLimitStore) {
        return (Map<String, RateLimitWindow>) ReflectionTestUtils.getField(memoryRateLimitStore, "windowMap");
    }

    private static long allowedCount(final List<Future<Long>> futureList) throws Exception {
        final AtomicLong allowedCount = new AtomicLong();

        for (final Future<Long> future : futureList) {
            if (future.get() == 0L) {
                allowedCount.incrementAndGet();
            }
        }

        return allowedCount.get();
    }

    @Test
    void capacityRefusalIsConvertedTo429ByTheInterceptor() throws Exception {
        final MemoryRateLimitStore memoryRateLimitStore = new MemoryRateLimitStore(1);

        try {
            memoryRateLimitStore.tryConsume("existing", data(60000L, 1));
            final HandlerMethod handlerMethod = new HandlerMethod(new LimitedController(), LimitedController.class.getMethod("limited"));
            final RateLimitRegistry rateLimitRegistry = new RateLimitRegistry();
            rateLimitRegistry.register(RateLimitRegistry.build(Collections.singletonMap("limited", handlerMethod)));
            final RateLimitInterceptor rateLimitInterceptor = new RateLimitInterceptor(rateLimitRegistry, memoryRateLimitStore, new IpAddressResolver(new FoundationProperties(), new IpAddressProperties()), new EmptySecurityAccountProvider());
            final MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
            httpServletRequest.addHeader("X-Forwarded-For", "203.0.113.9");
            final MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

            assertFalse(rateLimitInterceptor.preHandle(httpServletRequest, httpServletResponse, handlerMethod));
            assertEquals(429, httpServletResponse.getStatus());
            assertTrue(Long.parseLong(httpServletResponse.getHeader("Retry-After")) > 0L);
        } finally {
            memoryRateLimitStore.shutdown();
        }
    }

    @Test
    void concurrentNewKeysCannotOverrunTheCapacityBound() throws Exception {
        final MemoryRateLimitStore memoryRateLimitStore = new MemoryRateLimitStore(1);
        final CountDownLatch ready = new CountDownLatch(100);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<Long>> futureList = new ArrayList<>();

        try (final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            for (final int index : IntStream.range(0, 100).toArray()) {
                futureList.add(executorService.submit(() -> {
                    ready.countDown();
                    start.await();

                    return memoryRateLimitStore.tryConsume("capacity-%s".formatted(index), data(60000L, 1));
                }));
            }

            final boolean allReady = ready.await(5L, TimeUnit.SECONDS);
            start.countDown();

            assertTrue(allReady);

            assertEquals(1L, allowedCount(futureList));
            assertEquals(1, windows(memoryRateLimitStore).size());
        } finally {
            start.countDown();
            memoryRateLimitStore.shutdown();
        }
    }

    private static void awaitQuietly(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    static final class LimitedController {

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 1, unit = TimeUnit.MINUTES)
        public void limited() {
        }
    }
}