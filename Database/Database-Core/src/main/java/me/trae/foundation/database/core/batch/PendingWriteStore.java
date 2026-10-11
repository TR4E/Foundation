package me.trae.foundation.database.core.batch;

import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.storage.redis.RedisInvalidation;
import me.trae.foundation.database.storage.revision.RevisionState;
import org.jooq.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class PendingWriteStore {

    public static final int DEFAULT_MAX_PENDING_WRITES = 10_000;
    public static final int DEFAULT_MAX_PENDING_CALLBACKS = 100_000;

    private final Map<String, PendingWrite> pendingMap = new HashMap<>();
    private final Map<String, PendingWrite> inFlightMap = new HashMap<>();
    private final int maximumPendingWrites;
    private final int maximumPendingCallbacks;

    private final AtomicLong sequence = new AtomicLong();
    private int pendingCallbackCount;

    public PendingWriteStore() {
        this(DEFAULT_MAX_PENDING_WRITES, DEFAULT_MAX_PENDING_CALLBACKS);
    }

    public PendingWriteStore(final int maximumPendingWrites) {
        this(maximumPendingWrites, DEFAULT_MAX_PENDING_CALLBACKS);
    }

    PendingWriteStore(final int maximumPendingWrites, final int maximumPendingCallbacks) {
        if (maximumPendingWrites < 1) {
            throw new IllegalArgumentException("maximumPendingWrites must be greater than zero");
        }

        if (maximumPendingCallbacks < 1) {
            throw new IllegalArgumentException("maximumPendingCallbacks must be greater than zero");
        }

        this.maximumPendingWrites = maximumPendingWrites;
        this.maximumPendingCallbacks = maximumPendingCallbacks;
    }

    public void queueInsert(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final RedisInvalidation invalidation) {
        this.queue(new PendingWrite(tableSchema, tenantId, id, WriteType.INSERT, valueMap, commitCallbackList, failureCallbackList, this.sequence.incrementAndGet(), invalidation, null, null, null));
    }

    public void queueUpdate(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final RedisInvalidation invalidation, final Long expectedRevision, final Runnable revisionCallback, final RevisionState revisionState) {
        this.queue(new PendingWrite(tableSchema, tenantId, id, WriteType.UPDATE, valueMap, commitCallbackList, failureCallbackList, this.sequence.incrementAndGet(), invalidation, expectedRevision, revisionCallback, revisionState));
    }

    public void queueUpdate(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList) {
        this.queueUpdate(tableSchema, tenantId, id, valueMap, commitCallbackList, List.of(), null, null, null, null);
    }

    public void queueUpdate(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList, final RedisInvalidation invalidation) {
        this.queueUpdate(tableSchema, tenantId, id, valueMap, commitCallbackList, List.of(), invalidation, null, null, null);
    }

    public void queueUpdate(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList, final RedisInvalidation invalidation, final Long expectedRevision, final Runnable revisionCallback) {
        this.queueUpdate(tableSchema, tenantId, id, valueMap, commitCallbackList, List.of(), invalidation, expectedRevision, revisionCallback, null);
    }

    public void queueUpdate(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList, final RedisInvalidation invalidation, final Long expectedRevision, final Runnable revisionCallback, final RevisionState revisionState) {
        this.queueUpdate(tableSchema, tenantId, id, valueMap, commitCallbackList, List.of(), invalidation, expectedRevision, revisionCallback, revisionState);
    }

    public void queueInsert(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList, final RedisInvalidation invalidation) {
        this.queueInsert(tableSchema, tenantId, id, valueMap, commitCallbackList, List.of(), invalidation);
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
        this.queueDelete(tableSchema, tenantId, id, commitCallbackList, invalidation, expectedRevision, null);
    }

    public void queueDelete(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final List<Runnable> commitCallbackList, final RedisInvalidation invalidation, final Long expectedRevision, final RevisionState revisionState) {
        this.queue(new PendingWrite(tableSchema, tenantId, id, WriteType.DELETE, Collections.emptyMap(), commitCallbackList, this.sequence.incrementAndGet(), invalidation, expectedRevision, null, revisionState));
    }

    public synchronized void requeue(final PendingWrite failed) {
        if (!this.inFlightMap.remove(failed.getKey(), failed)) {
            return;
        }

        this.pendingMap.merge(failed.getKey(), failed, (current, older) -> older.merge(current));
    }

    public synchronized void complete(final PendingWrite completed) {
        if (this.inFlightMap.remove(completed.getKey(), completed)) {
            this.pendingCallbackCount -= this.callbackCount(completed);
        }
    }

    public synchronized List<PendingWrite> fail(final PendingWrite failed, final DatabaseException exception) {
        final List<PendingWrite> failedWriteList = new ArrayList<>();

        final PendingWrite inFlight = this.inFlightMap.get(failed.getKey());

        if (inFlight != null && inFlight.getSequence() == failed.getSequence()) {
            this.inFlightMap.remove(failed.getKey());
            this.pendingCallbackCount -= this.callbackCount(inFlight);
            inFlight.setFailureException(exception);
            failedWriteList.add(inFlight);
        }

        final PendingWrite pending = this.pendingMap.remove(failed.getKey());
        if (pending != null) {
            this.pendingCallbackCount -= this.callbackCount(pending);
            pending.setFailureException(exception);
            failedWriteList.add(pending);
        }

        return failedWriteList;
    }

    public synchronized List<PendingWrite> drain() {
        final List<PendingWrite> writeList = new ArrayList<>();

        for (final Map.Entry<String, PendingWrite> entry : this.pendingMap.entrySet()) {
            if (this.inFlightMap.containsKey(entry.getKey())) {
                continue;
            }

            final PendingWrite pendingWrite = this.rebaseRevision(entry.getValue());

            writeList.add(pendingWrite);
            this.inFlightMap.put(entry.getKey(), pendingWrite);
        }

        writeList.forEach(pendingWrite -> this.pendingMap.remove(pendingWrite.getKey()));

        writeList.sort(Comparator.comparingLong(PendingWrite::getSequence));

        return writeList;
    }

    public synchronized boolean isEmpty() {
        return this.pendingMap.isEmpty();
    }

    public synchronized int size() {
        return this.pendingMap.size();
    }

    synchronized int getPendingCallbackCount() {
        return this.pendingCallbackCount;
    }

    private synchronized void queue(final PendingWrite pendingWrite) {
        final String key = pendingWrite.getKey();

        final int callbackCount = this.callbackCount(pendingWrite);

        if (!this.pendingMap.containsKey(key) && !this.inFlightMap.containsKey(key) && this.getOutstandingSize() >= this.maximumPendingWrites) {
            throw new DatabaseException("Pending write capacity of %s was reached; write for %s was rejected".formatted(this.maximumPendingWrites, key));
        }

        if (this.pendingCallbackCount + callbackCount > this.maximumPendingCallbacks) {
            throw new DatabaseException("Pending callback capacity of %s was reached; write for %s was rejected".formatted(this.maximumPendingCallbacks, key));
        }

        final PendingWrite inFlight = this.inFlightMap.get(key);

        final PendingWrite current = this.pendingMap.get(key);

        if (inFlight != null) {
            this.validateFollowingWrite(inFlight, pendingWrite);
        }

        final PendingWrite merged = current == null ? pendingWrite : current.merge(pendingWrite);
        this.pendingMap.put(key, merged);
        this.pendingCallbackCount += callbackCount;
    }

    private void validateFollowingWrite(final PendingWrite inFlight, final PendingWrite newer) {
        if (inFlight.getWriteType() == WriteType.INSERT && newer.getWriteType() == WriteType.UPDATE) {
            return;
        }

        if (inFlight.getWriteType() == WriteType.UPDATE && (newer.getWriteType() == WriteType.UPDATE || newer.getWriteType() == WriteType.DELETE)) {
            return;
        }

        throw new DatabaseException("Cannot queue %s while %s is in flight for %s".formatted(newer.getWriteType(), inFlight.getWriteType(), newer.getId()));
    }

    private int callbackCount(final PendingWrite pendingWrite) {
        return pendingWrite.getCommitCallbackList().size() + pendingWrite.getFailureCallbackList().size();
    }

    private int getOutstandingSize() {
        final HashSet<String> keySet = new HashSet<>(this.inFlightMap.keySet());
        keySet.addAll(this.pendingMap.keySet());

        return keySet.size();
    }

    private PendingWrite rebaseRevision(final PendingWrite pendingWrite) {
        final Long expectedRevision = pendingWrite.getExpectedRevision();

        final RevisionState revisionState = pendingWrite.getRevisionState();

        if (expectedRevision == null || revisionState == null || expectedRevision == Long.MAX_VALUE - 1L) {
            return pendingWrite;
        }

        final long currentRevision = revisionState.getRevision();

        return currentRevision == expectedRevision + 1L ? pendingWrite.withExpectedRevision(currentRevision) : pendingWrite;
    }
}