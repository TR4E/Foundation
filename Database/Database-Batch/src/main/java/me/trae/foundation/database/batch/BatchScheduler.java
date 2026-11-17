package me.trae.foundation.database.batch;

import lombok.AllArgsConstructor;
import lombok.CustomLog;
import lombok.Getter;
import me.trae.foundation.database.api.write.Write;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@CustomLog
@AllArgsConstructor
@Getter
public final class BatchScheduler {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "Database-Batch");

        thread.setDaemon(true);

        return thread;
    });

    private final BatchQueue queue;
    private final Consumer<Write> writer;
    private final Duration interval;

    public void start() {
        this.executor.scheduleWithFixedDelay(this::flush, this.interval.toMillis(), this.interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        this.executor.shutdownNow();

        this.flush();
    }

    public void flush() {
        final List<Write> writeList = this.queue.drain();

        writeList.forEach(this::write);

        if (!writeList.isEmpty()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Flushed {} queued writes", writeList.size());
            }
        }
    }

    private void write(final Write write) {
        try {
            this.writer.accept(write);
        } catch (final RuntimeException exception) {
            this.queue.add(write);
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Requeued write for {} on {}, {}", write.getKey().getId(), write.getKey().getTable(), exception.getMessage());
            }
        }
    }
}