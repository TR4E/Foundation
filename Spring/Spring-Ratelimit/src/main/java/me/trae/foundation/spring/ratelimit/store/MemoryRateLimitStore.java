package me.trae.foundation.spring.ratelimit.store;

import jakarta.annotation.PreDestroy;
import lombok.CustomLog;
import me.trae.foundation.spring.ratelimit.RateLimitData;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@CustomLog
public final class MemoryRateLimitStore implements RateLimitStore {

    private static final long SWEEP_INTERVAL_SECONDS = 60L;
    private static final long SWEEP_MIN_INTERVAL_MILLIS = 1000L;
    private static final int MAX_WINDOWS = 250_000;

    private final Object windowLock = new Object();

    private final ConcurrentHashMap<String, RateLimitWindow> windowMap = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepMillis = new AtomicLong();
    private final ScheduledExecutorService executorService;

    private final int maxWindows;

    public MemoryRateLimitStore() {
        this(MAX_WINDOWS);
    }

    MemoryRateLimitStore(final int maxWindows) {
        this.maxWindows = maxWindows;
        this.executorService = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "rate-limit-sweep");
            thread.setDaemon(true);

            return thread;
        });

        this.executorService.scheduleAtFixedRate(this::sweep, SWEEP_INTERVAL_SECONDS, SWEEP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public long tryConsume(final String key, final RateLimitData rateLimitData) {
        while (true) {
            if (!this.reserveWindow(key, rateLimitData)) {
                return rateLimitData.getDurationMillis();
            }

            final long now = System.currentTimeMillis();

            final AtomicLong remaining = new AtomicLong();

            final RateLimitWindow rateLimitWindow = this.windowMap.computeIfPresent(key, (_, window) -> {
                final RateLimitWindow current = window.isExpired(now) ? new RateLimitWindow(now + rateLimitData.getDurationMillis()) : window;

                if (current.getAttempts() >= rateLimitData.getAttempts()) {
                    remaining.set(current.getRemainingMillis(now));

                    return current;
                }

                current.addAttempt();

                return current;
            });

            if (rateLimitWindow != null) {
                return remaining.get();
            }
        }
    }

    private boolean reserveWindow(final String key, final RateLimitData rateLimitData) {
        if (this.windowMap.containsKey(key)) {
            return true;
        }

        synchronized (this.windowLock) {
            if (this.windowMap.containsKey(key)) {
                return true;
            }

            if (!this.hasCapacity()) {
                return false;
            }

            this.windowMap.put(key, new RateLimitWindow(System.currentTimeMillis() + rateLimitData.getDurationMillis()));
        }

        return true;
    }

    private boolean hasCapacity() {
        if (this.windowMap.size() < this.maxWindows) {
            return true;
        }

        this.sweepIfDue();

        if (this.windowMap.size() < this.maxWindows) {
            return true;
        }

        LOGGER.warn("Rate limit window capacity of {} reached, rejecting requests under new keys", this.maxWindows);

        return false;
    }

    private void sweepIfDue() {
        final long now = System.currentTimeMillis();
        final long last = this.lastSweepMillis.get();

        if (now - last < SWEEP_MIN_INTERVAL_MILLIS) {
            return;
        }

        if (!this.lastSweepMillis.compareAndSet(last, now)) {
            return;
        }

        this.sweep();
    }

    private void sweep() {
        synchronized (this.windowLock) {
            try {
                final long now = System.currentTimeMillis();

                this.lastSweepMillis.set(now);
                this.windowMap.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
            } catch (final Throwable ignored) {
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        this.executorService.shutdownNow();
    }
}