package me.trae.foundation.spring.ratelimit.store;

import jakarta.annotation.PreDestroy;
import me.trae.foundation.spring.ratelimit.RateLimitData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class MemoryRateLimitStore implements RateLimitStore {

    private static final long SWEEP_INTERVAL_SECONDS = 60L;

    private final Map<String, RateLimitWindow> windowMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executorService;

    public MemoryRateLimitStore() {
        this.executorService = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "rate-limit-sweep");
            thread.setDaemon(true);

            return thread;
        });

        this.executorService.scheduleAtFixedRate(this::sweep, SWEEP_INTERVAL_SECONDS, SWEEP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public long tryConsume(final String key, final RateLimitData rateLimitData) {
        final long now = System.currentTimeMillis();

        final AtomicLong remaining = new AtomicLong();

        this.windowMap.compute(key, (_, window) -> {
            final RateLimitWindow current = window == null || window.isExpired(now) ? new RateLimitWindow(now + rateLimitData.getDurationMillis()) : window;

            if (current.getAttempts() >= rateLimitData.getAttempts()) {
                remaining.set(current.getRemainingMillis(now));

                return current;
            }

            current.addAttempt();

            return current;
        });

        return remaining.get();
    }

    private void sweep() {
        try {
            final long now = System.currentTimeMillis();

            this.windowMap.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        } catch (final Throwable ignored) {
        }
    }

    @PreDestroy
    public void shutdown() {
        this.executorService.shutdownNow();
    }
}