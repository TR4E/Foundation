package me.trae.foundation.database.batch;

import lombok.RequiredArgsConstructor;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

@RequiredArgsConstructor
public final class BatchQueue {

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(1);

    private final ConcurrentHashMap<EntityKey, Write> pendingMap = new ConcurrentHashMap<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantLock gate = new ReentrantLock();
    private final Condition drained = this.gate.newCondition();
    private final AtomicInteger waiting = new AtomicInteger();

    private final int limit;

    private volatile Consumer<Write> writer;

    public void add(final Write write) {
        this.await(write.getKey());

        this.lock.readLock().lock();

        try {
            if (this.writer != null) {
                this.writer.accept(write);
                return;
            }

            this.pendingMap.merge(write.getKey(), write, this::merge);
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public boolean requeue(final Write write) {
        this.pendingMap.merge(write.getKey(), write, (queued, retried) -> this.merge(retried, queued));

        return true;
    }

    public void close(final Consumer<Write> writer) {
        this.lock.writeLock().lock();

        try {
            this.writer = writer;
        } finally {
            this.lock.writeLock().unlock();
        }

        this.release();
    }

    public boolean isClosed() {
        return this.writer != null;
    }

    public Set<EntityKey> keys() {
        return Set.copyOf(this.pendingMap.keySet());
    }

    public Write remove(final EntityKey key) {
        final Write write = this.pendingMap.remove(key);

        this.signal();

        return write;
    }

    public List<Write> drain() {
        final List<Write> writes = new ArrayList<>();

        this.keys().forEach(key -> {
            final Write write = this.remove(key);

            if (write != null) {
                writes.add(write);
            }
        });

        return writes;
    }

    public int size() {
        return this.pendingMap.size();
    }

    private void await(final EntityKey key) {
        if (!this.blocked(key)) {
            return;
        }

        this.waiting.incrementAndGet();

        this.gate.lock();

        try {
            long nanos = WAIT_TIMEOUT.toNanos();

            while (nanos > 0L && this.blocked(key)) {
                nanos = this.drained.awaitNanos(nanos);
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            this.gate.unlock();

            this.waiting.decrementAndGet();
        }
    }

    private boolean blocked(final EntityKey key) {
        if (this.limit <= 0 || this.writer != null) {
            return false;
        }

        if (this.pendingMap.size() < this.limit) {
            return false;
        }

        return !this.pendingMap.containsKey(key);
    }

    private void signal() {
        if (this.waiting.get() == 0) {
            return;
        }

        this.release();
    }

    private void release() {
        this.gate.lock();

        try {
            this.drained.signalAll();
        } finally {
            this.gate.unlock();
        }
    }

    private Write merge(final Write existing, final Write incoming) {
        if (incoming.getKind() != WriteKind.UPDATE) {
            return incoming;
        }

        if (existing.getKind() == WriteKind.DELETE) {
            return existing;
        }

        return new Write(incoming.getKey(), existing.getKind(), ChangeMerge.merge(existing.getChanges(), incoming.getChanges()), incoming.getExpectedVersion());
    }
}