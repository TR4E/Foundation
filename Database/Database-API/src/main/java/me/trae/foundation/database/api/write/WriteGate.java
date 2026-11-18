package me.trae.foundation.database.api.write;

import me.trae.foundation.database.api.exception.DatabaseException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public final class WriteGate {

    private final ThreadLocal<int[]> depth = ThreadLocal.withInitial(() -> new int[1]);

    private final AtomicInteger inFlight = new AtomicInteger();

    private volatile boolean closed;

    public void enter() {
        final int[] current = this.depth.get();

        current[0]++;

        if (current[0] > 1) {
            return;
        }

        this.inFlight.incrementAndGet();

        if (this.closed) {
            this.inFlight.decrementAndGet();
            current[0]--;

            throw new DatabaseException("Database is stopping, the write was not accepted");
        }
    }

    public void exit() {
        final int[] current = this.depth.get();

        current[0]--;

        if (current[0] > 0) {
            return;
        }

        this.inFlight.decrementAndGet();
    }

    public boolean drain(final Duration timeout) {
        this.closed = true;

        final long deadline = System.currentTimeMillis() + timeout.toMillis();

        while (this.inFlight.get() > 0 && System.currentTimeMillis() < deadline) {
            this.sleep();
        }

        return this.inFlight.get() == 0;
    }

    private void sleep() {
        try {
            Thread.sleep(1L);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}