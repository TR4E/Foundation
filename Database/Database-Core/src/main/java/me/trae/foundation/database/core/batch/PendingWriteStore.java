package me.trae.foundation.database.core.batch;

import me.trae.foundation.database.core.schema.TableSchema;
import org.jooq.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PendingWriteStore {

    private final Map<String, PendingWrite> pendingMap = new ConcurrentHashMap<>();

    private final AtomicLong sequence = new AtomicLong();

    public void queueUpsert(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList) {
        this.queue(new PendingWrite(tableSchema, tenantId, id, WriteType.UPSERT, valueMap, commitCallbackList, this.sequence.incrementAndGet()));
    }

    public void queueDelete(final TableSchema<?> tableSchema, final String tenantId, final UUID id) {
        this.queue(new PendingWrite(tableSchema, tenantId, id, WriteType.DELETE, Collections.emptyMap(), Collections.emptyList(), this.sequence.incrementAndGet()));
    }

    public void requeue(final PendingWrite failed) {
        this.pendingMap.merge(failed.getKey(), failed, (current, older) -> older.merge(current));
    }

    public List<PendingWrite> drain() {
        final List<PendingWrite> writeList = new ArrayList<>();

        for (final String key : this.pendingMap.keySet()) {
            final PendingWrite pendingWrite = this.pendingMap.remove(key);

            if (pendingWrite != null) {
                writeList.add(pendingWrite);
            }
        }

        writeList.sort(Comparator.comparingLong(PendingWrite::getSequence));

        return writeList;
    }

    public boolean isEmpty() {
        return this.pendingMap.isEmpty();
    }

    public int size() {
        return this.pendingMap.size();
    }

    private void queue(final PendingWrite pendingWrite) {
        this.pendingMap.merge(pendingWrite.getKey(), pendingWrite, PendingWrite::merge);
    }
}