package me.trae.foundation.database.core.batch;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.storage.redis.RedisInvalidation;
import org.jooq.Field;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
@Getter
public final class PendingWrite {

    private final TableSchema<?> tableSchema;
    private final String tenantId;
    private final UUID id;
    private final WriteType writeType;
    private final Map<Field<?>, Object> valueMap;
    private final List<Runnable> commitCallbackList;
    private final long sequence;
    private final RedisInvalidation invalidation;
    private final Long expectedRevision;
    private final Runnable revisionCallback;

    public PendingWrite(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final WriteType writeType, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList, final long sequence) {
        this(tableSchema, tenantId, id, writeType, valueMap, commitCallbackList, sequence, null, null, null);
    }

    public PendingWrite(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final WriteType writeType, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList, final long sequence, final RedisInvalidation invalidation) {
        this(tableSchema, tenantId, id, writeType, valueMap, commitCallbackList, sequence, invalidation, null, null);
    }

    public String getKey() {
        return "%s|%s|%s".formatted(this.tableSchema.getTableName(), this.tenantId, this.id);
    }

    public Long getExpectedRevision() {
        return this.expectedRevision;
    }

    public PendingWrite merge(final PendingWrite newer) {
        final List<Runnable> callbackList = new ArrayList<>(this.commitCallbackList);

        callbackList.addAll(newer.getCommitCallbackList());

        if (newer.getWriteType() == WriteType.DELETE || this.writeType == WriteType.DELETE) {
            return new PendingWrite(
                    this.tableSchema,
                    this.tenantId,
                    this.id,
                    newer.getWriteType(),
                    newer.getValueMap(),
                    callbackList,
                    newer.getSequence(),
                    newer.getInvalidation() == null ? this.invalidation : newer.getInvalidation(),
                    newer.getExpectedRevision() == null ? this.expectedRevision : newer.getExpectedRevision(),
                    newer.getRevisionCallback() == null ? this.revisionCallback : newer.getRevisionCallback()
            );
        }

        final Map<Field<?>, Object> mergedMap = new LinkedHashMap<>(this.valueMap);

        mergedMap.putAll(newer.getValueMap());

        return new PendingWrite(this.tableSchema, this.tenantId, this.id, WriteType.UPSERT, mergedMap, callbackList, newer.getSequence(), newer.getInvalidation() == null ? this.invalidation : newer.getInvalidation(), newer.getExpectedRevision() == null ? this.expectedRevision : newer.getExpectedRevision(), newer.getRevisionCallback() == null ? this.revisionCallback : newer.getRevisionCallback());
    }
}