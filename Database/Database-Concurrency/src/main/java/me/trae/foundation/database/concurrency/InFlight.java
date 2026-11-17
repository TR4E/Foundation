package me.trae.foundation.database.concurrency;

import me.trae.foundation.database.concurrency.exception.CoalescingException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class InFlight<Key, Value> {

    private final ConcurrentHashMap<Key, CompletableFuture<Value>> loads = new ConcurrentHashMap<>();

    public Value get(final Key key, final Supplier<Value> loader) {
        final CompletableFuture<Value> future = new CompletableFuture<>();
        final CompletableFuture<Value> existing = this.loads.putIfAbsent(key, future);

        if (existing != null) {
            return this.await(existing);
        }

        try {
            final Value value = loader.get();

            future.complete(value);

            return value;
        } catch (final RuntimeException exception) {
            future.completeExceptionally(exception);

            throw exception;
        } finally {
            if (!future.isDone()) {
                future.completeExceptionally(new CoalescingException("Load did not complete for %s".formatted(key)));
            }

            this.loads.remove(key, future);
        }
    }

    private Value await(final CompletableFuture<Value> future) {
        try {
            return future.join();
        } catch (final CompletionException exception) {
            throw exception.getCause() instanceof final RuntimeException cause ? cause : new CoalescingException("Load failed", exception.getCause());
        }
    }
}