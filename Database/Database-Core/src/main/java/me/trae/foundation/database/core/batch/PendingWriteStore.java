package me.trae.foundation.database.core.batch;

import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.storage.redis.RedisInvalidation;
import org.jooq.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class PendingWriteStore {

    public static final int DEFAULT_MAX_PENDING_WRITES = 10_000;

    private final Map<String, PendingWrite> pendingMap = new HashMap<>();
    private final Set<String> inFlightKeySet = new HashSet<>();
    private final int maximumPendingWrites;

    private final AtomicLong sequence = new AtomicLong();

    public PendingWriteStore() {
        this(DEFAULT_MAX_PENDING_WRITES);
    }

    public PendingWriteStore(final int maximumPendingWrites) {
        if (maximumPendingWrites < 1) {
            throw new IllegalArgumentException("maximumPendingWrites must be greater than zero");
        }

        this.maximumPendingWrites = maximumPendingWrites;
    }

    public void queueUpsert(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList) {
        this.queueUpsert(tableSchema, tenantId, id, valueMap, commitCallbackList, null);
    }

    public void queueUpsert(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList, final RedisInvalidation invalidation) {
        this.queue(new PendingWrite(tableSchema, tenantId, id, WriteType.UPSERT, valueMap, commitCallbackList, this.sequence.incrementAndGet(), invalidation));
    }

    public void queueUpsert(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList, final RedisInvalidation invalidation, final Long expectedRevision, final Runnable revisionCallback) {
        this.queue(new PendingWrite(tableSchema, tenantId, id, WriteType.UPSERT, valueMap, commitCallbackList, this.sequence.incrementAndGet(), invalidation, expectedRevision, revisionCallback));
    }

    public void queueDelete(final TableSchema<?> tableSchema, final String tenantId, final UUID id) {
        this.queueDelete(tableSchema, tenantId, id, Collections.emptyList());
    }

    public void queueDelete(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final List<Runnable> commitCallbackList) {
        this.queueDelete(tableSchema, tenantId, id, commitCallbackList, null);
    }

    public void queueDelete(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final List<Runnable> commitCallbackList, final RedisInvalidation invalidation) {
        this.queue(new PendingWrite(tableSchema, tenantId, id, WriteType.DELETE, Collections.emptyMap(), commitCallbackList, this.sequence.incrementAndGet(), invalidation));
    }

    public void queueDelete(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final List<Runnable> commitCallbackList, final RedisInvalidation invalidation, final Long expectedRevision) {
        this.queue(new PendingWrite(tableSchema, tenantId, id, WriteType.DELETE, Collections.emptyMap(), commitCallbackList, this.sequence.incrementAndGet(), invalidation, expectedRevision, null));
    }

    public synchronized void requeue(final PendingWrite failed) {
        this.pendingMap.merge(failed.getKey(), failed, (current, older) -> older.merge(current));
        this.inFlightKeySet.remove(failed.getKey());
    }

    public synchronized void complete(final PendingWrite completed) {
        this.inFlightKeySet.remove(completed.getKey());
    }

    public synchronized List<PendingWrite> drain() {
        final List<PendingWrite> writeList = new ArrayList<>();

        for (final Map.Entry<String, PendingWrite> entry : this.pendingMap.entrySet()) {
            writeList.add(entry.getValue());
            this.inFlightKeySet.add(entry.getKey());
        }

        this.pendingMap.clear();

        writeList.sort(Comparator.comparingLong(PendingWrite::getSequence));

        return writeList;
    }

    public synchronized boolean isEmpty() {
        return this.pendingMap.isEmpty();
    }

    public synchronized int size() {
        return this.pendingMap.size();
    }

    private synchronized void queue(final PendingWrite pendingWrite) {
        final String key = pendingWrite.getKey();

        if (!this.pendingMap.containsKey(key) && !this.inFlightKeySet.contains(key) && this.getOutstandingSize() >= this.maximumPendingWrites) {
            throw new DatabaseException("Pending write capacity of %s was reached; write for %s was rejected".formatted(this.maximumPendingWrites, key));
        }

        this.pendingMap.merge(pendingWrite.getKey(), pendingWrite, PendingWrite::merge);
    }

    private int getOutstandingSize() {
        final Set<String> keySet = new HashSet<>(this.inFlightKeySet);
        keySet.addAll(this.pendingMap.keySet());

        return keySet.size();
    }
}