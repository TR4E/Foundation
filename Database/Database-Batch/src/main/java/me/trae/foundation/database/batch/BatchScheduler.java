package me.trae.foundation.database.batch;

import lombok.AllArgsConstructor;
import lombok.CustomLog;
import lombok.Getter;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.change.Change;
import me.trae.foundation.database.api.write.change.Decrement;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@CustomLog
@AllArgsConstructor
@Getter
public final class BatchScheduler {

    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);
    private static final int WORKERS = 8;
    private static final int CHUNK = 256;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "Database-Batch");

        thread.setDaemon(true);

        return thread;
    });

    private final ExecutorService workers = Executors.newFixedThreadPool(WORKERS, runnable -> {
        final Thread thread = new Thread(runnable, "Database-Batch-Worker");

        thread.setDaemon(true);

        return thread;
    });

    private final ConcurrentHashMap<EntityKey, Boolean> inFlightMap = new ConcurrentHashMap<>();
    private final ReentrantLock persistLock = new ReentrantLock();
    private final AtomicBoolean stopped = new AtomicBoolean();

    private final BatchQueue queue;
    private final Consumer<List<Write>> writer;
    private final Duration interval;

    public void start() {
        this.executor.scheduleWithFixedDelay(this::flush, this.interval.toMillis(), this.interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (!this.stopped.compareAndSet(false, true)) {
            return;
        }

        this.close(this.executor);

        this.queue.close(this::persist);

        this.close(this.workers);

        this.remainder();
    }

    public void flush() {
        this.persistLock.lock();

        try {
            this.drain();
        } finally {
            this.persistLock.unlock();
        }
    }

    public void persist(final Write write) {
        this.persistLock.lock();

        try {
            this.writer.accept(List.of(write));
        } finally {
            this.persistLock.unlock();
        }
    }

    private void close(final ExecutorService service) {
        service.shutdown();

        if (this.await(service)) {
            return;
        }

        service.shutdownNow();

        long waited = TERMINATION_TIMEOUT.toSeconds();

        while (!this.await(service)) {
            waited += TERMINATION_TIMEOUT.toSeconds();

            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Waiting on {} queued and {} in-flight writes after {} seconds of shutdown", this.queue.size(), this.inFlightMap.size(), waited);
            }
        }
    }

    private void remainder() {
        this.inFlightMap.clear();

        List<Write> writes = this.queue.drain();

        while (!writes.isEmpty()) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Writing {} remaining writes after shutdown", writes.size());
            }

            for (int index = 0; index < writes.size(); index += CHUNK) {
                this.sweep(writes.subList(index, Math.min(index + CHUNK, writes.size())));
            }

            writes = this.queue.drain();
        }
    }

    private void sweep(final List<Write> writes) {
        try {
            this.writer.accept(writes);
        } catch (final RuntimeException exception) {
            this.record(writes, exception);
        }
    }

    private void record(final List<Write> writes, final RuntimeException exception) {
        if (!LOGGER.isErrorEnabled()) {
            return;
        }

        LOGGER.error("Could not persist {} writes on {} during shutdown, recording each below for replay, {}", writes.size(), writes.getFirst().getKey().getTable(), exception.getMessage());

        writes.forEach(write -> LOGGER.error("database-unflushed {}", line(write)));
    }

    private static String line(final Write write) {
        return "%s\t%s\t%s\t%s\t%s\t%s".formatted(encode(write.getKey().getTable()), encode(write.getKey().getTenant()), write.getKey().getId(), write.getKind(), write.getExpectedVersion(), write.getChanges().stream()
                .map(BatchScheduler::field)
                .collect(Collectors.joining(";")));
    }

    private static String field(final Change change) {
        return "%s:%s:%s".formatted(encode(change.getColumn()), change.getType(), value(change.getValue()));
    }

    private static String value(final Object value) {
        if (value == null) {
            return "n:";
        }

        if (value instanceof final Decrement decrement) {
            return "g:" + encode(decrement.getDelta() + "/" + decrement.getFloor());
        }

        if (value instanceof final byte[] bytes) {
            return "b:" + Base64.getEncoder().encodeToString(bytes);
        }

        if (value instanceof final Enum<?> constant) {
            return "e:" + encode(constant.getDeclaringClass().getName()) + ":" + encode(constant.name());
        }

        return "v:" + encode(value.getClass().getName()) + ":" + encode(String.valueOf(value));
    }

    private static String encode(final String value) {
        if (value == null) {
            return "n";
        }

        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private void drain() {
        final List<Future<?>> futures = new ArrayList<>();

        List<EntityKey> chunk = new ArrayList<>();

        for (final EntityKey key : this.queue.keys()) {
            if (this.inFlightMap.putIfAbsent(key, Boolean.TRUE) != null) {
                continue;
            }

            chunk.add(key);

            if (chunk.size() < CHUNK) {
                continue;
            }

            futures.add(this.submit(chunk));

            chunk = new ArrayList<>();
        }

        if (!chunk.isEmpty()) {
            futures.add(this.submit(chunk));
        }

        futures.forEach(this::complete);
    }

    private Future<?> submit(final List<EntityKey> keys) {
        return this.workers.submit(() -> this.apply(keys));
    }

    private void apply(final List<EntityKey> keys) {
        final List<Write> writes = new ArrayList<>();

        try {
            keys.forEach(key -> this.claim(key, writes));

            if (writes.isEmpty()) {
                return;
            }

            this.writer.accept(writes);
        } catch (final RuntimeException exception) {
            this.retry(writes);

            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Retried {} writes on {}, {}", writes.size(), writes.getFirst().getKey().getTable(), exception.getMessage());
            }
        } finally {
            keys.forEach(this.inFlightMap::remove);
        }
    }

    private void retry(final List<Write> writes) {
        if (!this.stopped.get()) {
            writes.forEach(this.queue::requeue);
            return;
        }

        this.sweep(writes);
    }

    private void claim(final EntityKey key, final List<Write> writes) {
        final Write write = this.queue.remove(key);

        if (write == null) {
            return;
        }

        writes.add(write);
    }

    private void complete(final Future<?> future) {
        try {
            future.get();
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (final ExecutionException exception) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("A batch failed, {}", exception.getMessage());
            }
        }
    }

    private boolean await(final ExecutorService service) {
        try {
            return service.awaitTermination(TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}