package me.trae.foundation.database.batch;

import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public final class BatchQueue {

    private final ConcurrentHashMap<EntityKey, Write> pendingMap = new ConcurrentHashMap<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile Consumer<Write> writer;

    public void add(final Write write) {
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
        this.lock.readLock().lock();

        try {
            if (this.writer != null) {
                return false;
            }

            this.pendingMap.merge(write.getKey(), write, (queued, retried) -> this.merge(retried, queued));

            return true;
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public void close(final Consumer<Write> writer) {
        this.lock.writeLock().lock();

        try {
            this.writer = writer;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public boolean isClosed() {
        return this.writer != null;
    }

    public Set<EntityKey> keys() {
        return Set.copyOf(this.pendingMap.keySet());
    }

    public Write remove(final EntityKey key) {
        return this.pendingMap.remove(key);
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