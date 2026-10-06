package me.trae.foundation.database.lookup.inflight;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public final class InFlightLookup<Value> {

    private final ConcurrentMap<Object, CompletableFuture<Optional<Value>>> inFlightMap = new ConcurrentHashMap<>();

    public Optional<Value> compute(final Object key, final Supplier<Optional<Value>> supplier) {
        final CompletableFuture<Optional<Value>> future = new CompletableFuture<>();

        final CompletableFuture<Optional<Value>> existing = this.inFlightMap.putIfAbsent(key, future);
        if (existing != null) {
            return this.await(existing);
        }

        try {
            final Optional<Value> result = supplier.get();

            future.complete(result);

            return result;
        } catch (final Throwable throwable) {
            future.completeExceptionally(throwable);

            throw throwable;
        } finally {
            this.inFlightMap.remove(key, future);
        }
    }

    public int getInFlightCount() {
        return this.inFlightMap.size();
    }

    private Optional<Value> await(final CompletableFuture<Optional<Value>> future) {
        try {
            return future.join();
        } catch (final CompletionException exception) {
            if (exception.getCause() instanceof final RuntimeException runtimeException) {
                throw runtimeException;
            }

            if (exception.getCause() instanceof final Error error) {
                throw error;
            }

            throw exception;
        }
    }
}