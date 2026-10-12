package me.trae.foundation.database.core.batch;

import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.storage.redis.RedisInvalidation;
import me.trae.foundation.database.storage.revision.RevisionState;
import org.jooq.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
public final class PendingWrite {

    private final TableSchema<?> tableSchema;
    private final String tenantId;
    private final UUID id;
    private final WriteType writeType;
    private final Map<Field<?>, Object> valueMap;
    private final List<Runnable> commitCallbackList;
    private final List<Runnable> failureCallbackList;
    private final long sequence;
    private final RedisInvalidation invalidation;
    private final Long expectedRevision;
    private final Runnable revisionCallback;
    private final RevisionState revisionState;

    @Setter
    private volatile DatabaseException failureException;

    public PendingWrite(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final WriteType writeType, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList, final long sequence, final RedisInvalidation invalidation, final Long expectedRevision, final Runnable revisionCallback) {
        this(tableSchema, tenantId, id, writeType, valueMap, commitCallbackList, List.of(), sequence, invalidation, expectedRevision, revisionCallback, null);
    }

    public PendingWrite(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final WriteType writeType, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList, final long sequence, final RedisInvalidation invalidation, final Long expectedRevision, final Runnable revisionCallback, final RevisionState revisionState) {
        this(tableSchema, tenantId, id, writeType, valueMap, commitCallbackList, List.of(), sequence, invalidation, expectedRevision, revisionCallback, revisionState);
    }

    public PendingWrite(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final WriteType writeType, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final long sequence, final RedisInvalidation invalidation, final Long expectedRevision, final Runnable revisionCallback, final RevisionState revisionState) {
        this.tableSchema = tableSchema;
        this.tenantId = tenantId;
        this.id = id;
        this.writeType = writeType;
        this.valueMap = valueMap;
        this.commitCallbackList = List.copyOf(commitCallbackList);
        this.failureCallbackList = List.copyOf(failureCallbackList);
        this.sequence = sequence;
        this.invalidation = invalidation;
        this.expectedRevision = expectedRevision;
        this.revisionCallback = revisionCallback;
        this.revisionState = revisionState;
    }

    public PendingWrite(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final WriteType writeType, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList, final long sequence) {
        this(tableSchema, tenantId, id, writeType, valueMap, commitCallbackList, List.of(), sequence, null, null, null, null);
    }

    public PendingWrite(final TableSchema<?> tableSchema, final String tenantId, final UUID id, final WriteType writeType, final Map<Field<?>, Object> valueMap, final List<Runnable> commitCallbackList, final long sequence, final RedisInvalidation invalidation) {
        this(tableSchema, tenantId, id, writeType, valueMap, commitCallbackList, List.of(), sequence, invalidation, null, null, null);
    }

    public String getKey() {
        return "%s|%s|%s".formatted(this.tableSchema.getTableName(), this.tenantId, this.id);
    }

    public PendingWrite merge(final PendingWrite newer) {
        if (this.writeType == WriteType.DELETE || newer.getWriteType() == WriteType.INSERT || newer.getWriteType() == WriteType.DELETE && this.writeType == WriteType.INSERT) {
            if (this.writeType == WriteType.DELETE && newer.getWriteType() == WriteType.DELETE) {
                return this.mergeWrite(newer, WriteType.DELETE, Collections.emptyMap());
            }

            throw new DatabaseException("Cannot queue %s after %s for %s".formatted(newer.getWriteType(), this.writeType, this.id));
        }

        if (this.writeType == WriteType.INSERT && newer.getWriteType() == WriteType.UPDATE) {
            final Map<Field<?>, Object> mergedMap = new LinkedHashMap<>(this.valueMap);
            mergedMap.putAll(newer.getValueMap());
            return this.mergeWrite(newer, WriteType.INSERT, mergedMap);
        }

        if (this.writeType == WriteType.UPDATE && newer.getWriteType() == WriteType.DELETE) {
            return this.mergeWrite(newer, WriteType.DELETE, Collections.emptyMap());
        }

        if (this.writeType != WriteType.UPDATE || newer.getWriteType() != WriteType.UPDATE) {
            throw new DatabaseException("Cannot queue %s after %s for %s".formatted(newer.getWriteType(), this.writeType, this.id));
        }

        final Map<Field<?>, Object> mergedMap = new LinkedHashMap<>(this.valueMap);
        mergedMap.putAll(newer.getValueMap());
        return this.mergeWrite(newer, WriteType.UPDATE, mergedMap);
    }

    private PendingWrite mergeWrite(final PendingWrite newer, final WriteType writeType, final Map<Field<?>, Object> mergedMap) {
        final List<Runnable> callbackList = new ArrayList<>(this.commitCallbackList);
        callbackList.addAll(newer.getCommitCallbackList());
        final List<Runnable> failureCallbackList = new ArrayList<>(this.failureCallbackList);
        failureCallbackList.addAll(newer.getFailureCallbackList());
        final Runnable revisionCallback = writeType == WriteType.DELETE ? null : newer.getRevisionCallback() == null ? this.revisionCallback : newer.getRevisionCallback();

        return new PendingWrite(this.tableSchema, this.tenantId, this.id, writeType, mergedMap, callbackList, failureCallbackList, newer.getSequence(), newer.getInvalidation() == null ? this.invalidation : newer.getInvalidation(), newer.getExpectedRevision() == null ? this.expectedRevision : newer.getExpectedRevision(), revisionCallback, newer.getRevisionState() == null ? this.revisionState : newer.getRevisionState());
    }

    public PendingWrite withExpectedRevision(final long expectedRevision) {
        final Map<Field<?>, Object> revisedValueMap = new LinkedHashMap<>(this.valueMap);

        final long nextRevision = expectedRevision + 1L;
        Runnable revisedCallback = null;

        if (this.writeType != WriteType.DELETE) {
            revisedValueMap.put(TableSchema.REVISION_FIELD, nextRevision);
            revisedCallback = () -> this.revisionState.setRevision(nextRevision);
        }

        return new PendingWrite(this.tableSchema, this.tenantId, this.id, this.writeType, revisedValueMap, this.commitCallbackList, this.failureCallbackList, this.sequence, this.invalidation, expectedRevision, revisedCallback, this.revisionState);
    }
}