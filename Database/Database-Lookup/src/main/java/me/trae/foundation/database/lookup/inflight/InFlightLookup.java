package me.trae.foundation.database.lookup.inflight;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

public final class InFlightLookup<Value> {

    private final ConcurrentMap<Object, FutureTask<Optional<Value>>> inFlightMap = new ConcurrentHashMap<>();

    public Optional<Value> compute(final Object key, final Supplier<Optional<Value>> supplier) {
        final FutureTask<Optional<Value>> task = new FutureTask<>(supplier::get);

        final FutureTask<Optional<Value>> existing = this.inFlightMap.putIfAbsent(key, task);
        if (existing != null) {
            return this.await(existing);
        }

        try {
            task.run();

            return this.await(task);
        } finally {
            this.inFlightMap.remove(key, task);
        }
    }

    public int getInFlightCount() {
        return this.inFlightMap.size();
    }

    private Optional<Value> await(final FutureTask<Optional<Value>> task) {
        try {
            return task.get();
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for an in-flight lookup", exception);
        } catch (final ExecutionException exception) {
            if (exception.getCause() instanceof final RuntimeException runtimeException) {
                throw runtimeException;
            }

            if (exception.getCause() instanceof final Error error) {
                throw error;
            }

            throw new IllegalStateException("In-flight lookup failed", exception.getCause());
        }
    }
}