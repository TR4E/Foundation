package me.trae.foundation.database.holder.refresh;

import lombok.AllArgsConstructor;
import lombok.CustomLog;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

@CustomLog
@AllArgsConstructor
@Getter
public final class HoldRefresher<EntityType extends Entity> {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "Database-Hold-Refresh");

        thread.setDaemon(true);

        return thread;
    });

    private final Supplier<List<EntityType>> held;
    private final Consumer<EntityType> refresher;
    private final Duration interval;

    public void start() {
        this.executor.scheduleWithFixedDelay(this::refresh, this.interval.toMillis(), this.interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        this.executor.shutdownNow();
    }

    private void refresh(final EntityType entity) {
        try {
            this.refresher.accept(entity);
        } catch (final RuntimeException exception) {
            LOGGER.warn("Failed to refresh {}, {}", entity.getId(), exception.getMessage());
        }
    }

    public void refresh() {
        try {
            this.held.get().forEach(this::refresh);
        } catch (final RuntimeException exception) {
            LOGGER.warn("Failed to collect held entities, {}", exception.getMessage());
        }
    }
}