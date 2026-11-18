package me.trae.foundation.database.batch;

import lombok.AllArgsConstructor;
import lombok.CustomLog;
import lombok.Getter;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.write.Write;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@CustomLog
@AllArgsConstructor
@Getter
public final class BatchScheduler {

    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "Database-Batch");

        thread.setDaemon(true);

        return thread;
    });

    private final ReentrantLock persistLock = new ReentrantLock();
    private final AtomicBoolean stopped = new AtomicBoolean();

    private final BatchQueue queue;
    private final Consumer<Write> writer;
    private final Duration interval;

    public void start() {
        this.executor.scheduleWithFixedDelay(this::flush, this.interval.toMillis(), this.interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (!this.stopped.compareAndSet(false, true)) {
            return;
        }

        this.executor.shutdown();

        if (!this.await()) {
            this.executor.shutdownNow();

            this.await();
        }

        this.queue.close(this::persist);

        this.terminate();
    }

    public void flush() {
        this.persistLock.lock();

        try {
            this.drain(Long.MAX_VALUE);
        } finally {
            this.persistLock.unlock();
        }
    }

    private void terminate() {
        final long deadline = System.currentTimeMillis() + TERMINATION_TIMEOUT.toMillis();

        final Thread thread = new Thread(() -> this.flush(deadline), "Database-Batch-Final");

        thread.setDaemon(true);
        thread.start();

        try {
            thread.join(Math.max(deadline - System.currentTimeMillis(), 1L));
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        if (thread.isAlive() && LOGGER.isWarnEnabled()) {
            LOGGER.warn("Final flush did not finish within {} seconds, {} writes pending", TERMINATION_TIMEOUT.toSeconds(), this.queue.size());
        }
    }

    private void flush(final long deadline) {
        if (!this.acquire(deadline)) {
            return;
        }

        try {
            this.drain(deadline);
        } finally {
            this.persistLock.unlock();
        }
    }

    private void drain(final long deadline) {
        int flushed = 0;

        for (final EntityKey key : this.queue.keys()) {
            if (System.currentTimeMillis() >= deadline) {
                break;
            }

            final Write write = this.queue.remove(key);
            if (write == null) {
                continue;
            }

            this.write(write);

            flushed++;
        }

        if (flushed > 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("Flushed {} queued writes", flushed);
        }
    }

    private void write(final Write write) {
        try {
            this.persist(write);
        } catch (final RuntimeException exception) {
            if (!this.queue.requeue(write)) {
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("Dropped write for {} on {} during shutdown, {}", write.getKey().getId(), write.getKey().getTable(), exception.getMessage());
                }
                return;
            }

            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Requeued write for {} on {}, {}", write.getKey().getId(), write.getKey().getTable(), exception.getMessage());
            }
        }
    }

    private void persist(final Write write) {
        this.persistLock.lock();

        try {
            this.writer.accept(write);
        } finally {
            this.persistLock.unlock();
        }
    }

    private boolean acquire(final long deadline) {
        try {
            return this.persistLock.tryLock(Math.max(deadline - System.currentTimeMillis(), 0L), TimeUnit.MILLISECONDS);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean await() {
        try {
            return this.executor.awaitTermination(TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}