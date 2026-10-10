package me.trae.foundation.database.core.batch;

import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.storage.driver.RedisDriver;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class BatchQueue {

    private static final int MAXIMUM_DRAIN_ATTEMPTS = 10;

    @Getter
    private final PendingWriteStore pendingWriteStore;

    private final List<Runnable> preFlushTaskList = new CopyOnWriteArrayList<>();

    private final BatchExecutor batchExecutor;
    private final CacheInvalidationOutbox cacheInvalidationOutbox;
    private final Duration flushInterval;
    private final int chunkSize;

    @Setter
    private volatile Consumer<DatabaseException> failureHandler = _ -> {};

    private ScheduledExecutorService scheduledExecutorService;

    public BatchQueue(final PostgresDriver postgresDriver, final RedisDriver redisDriver, final Duration flushInterval, final int chunkSize) {
        this(postgresDriver, redisDriver, flushInterval, chunkSize, PendingWriteStore.DEFAULT_MAX_PENDING_WRITES);
    }

    public BatchQueue(final PostgresDriver postgresDriver, final RedisDriver redisDriver, final Duration flushInterval, final int chunkSize, final int maximumPendingWrites) {
        this.pendingWriteStore = new PendingWriteStore(maximumPendingWrites);
        this.batchExecutor = new BatchExecutor(postgresDriver, this::reportFailure);
        this.cacheInvalidationOutbox = redisDriver == null ? null : new CacheInvalidationOutbox(postgresDriver, redisDriver);
        this.flushInterval = flushInterval;
        this.chunkSize = chunkSize;
    }

    public void synchronizeOutbox() {
        if (this.cacheInvalidationOutbox != null) {
            this.cacheInvalidationOutbox.synchronize();
        }
    }

    public synchronized void start() {
        if (this.scheduledExecutorService != null) {
            return;
        }

        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("database-batch-queue").factory());

        this.scheduledExecutorService.scheduleWithFixedDelay(this::flushSafely, this.flushInterval.toMillis(), this.flushInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        synchronized (this) {
            if (this.scheduledExecutorService == null) {
                return;
            }

            this.scheduledExecutorService.shutdownNow();
            this.scheduledExecutorService = null;
        }

        for (int attempt = 0; attempt < MAXIMUM_DRAIN_ATTEMPTS && !this.pendingWriteStore.isEmpty(); attempt++) {
            this.flush();
        }

        if (!this.pendingWriteStore.isEmpty()) {
            this.reportFailure(new DatabaseException("Shut down with %s writes still pending".formatted(this.pendingWriteStore.size())));
        }
    }

    public void addPreFlushTask(final Runnable task) {
        this.preFlushTaskList.add(task);
    }

    public synchronized void flush() {
        this.preFlushTaskList.forEach(this::runSafely);

        final List<PendingWrite> writeList = this.pendingWriteStore.drain();

        for (int index = 0; index < writeList.size(); index += this.chunkSize) {
            final List<PendingWrite> chunkList = writeList.subList(index, Math.min(index + this.chunkSize, writeList.size()));
            final List<PendingWrite> retryList = this.batchExecutor.execute(chunkList);

            for (final PendingWrite pendingWrite : chunkList) {
                if (retryList.contains(pendingWrite)) {
                    this.pendingWriteStore.requeue(pendingWrite);
                } else {
                    this.pendingWriteStore.complete(pendingWrite);
                }
            }
        }

        if (this.cacheInvalidationOutbox != null) {
            try {
                this.cacheInvalidationOutbox.dispatch();
            } catch (final RuntimeException exception) {
                this.reportFailure(new DatabaseException("Failed to dispatch cache invalidation outbox", exception));
            }
        }
    }

    public void reportFailure(final DatabaseException exception) {
        this.failureHandler.accept(exception);
    }

    private void runSafely(final Runnable task) {
        try {
            task.run();
        } catch (final RuntimeException exception) {
            this.reportFailure(new DatabaseException("Batch queue task failed", exception));
        }
    }

    private void flushSafely() {
        this.runSafely(this::flush);
    }
}