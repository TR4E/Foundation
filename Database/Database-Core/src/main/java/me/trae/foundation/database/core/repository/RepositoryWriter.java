package me.trae.foundation.database.core.repository;

import lombok.AllArgsConstructor;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.core.batch.PendingWriteStore;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.core.value.ColumnValueMapper;
import me.trae.foundation.database.storage.redis.RedisInvalidation;
import me.trae.foundation.database.storage.redis.RedisNamespace;
import me.trae.foundation.database.storage.revision.EntityRevisionStore;
import me.trae.foundation.database.storage.revision.RevisionState;
import org.jooq.Field;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
public final class RepositoryWriter<E extends Entity> {

    private final PendingWriteStore pendingWriteStore;
    private final TableSchema<E> tableSchema;
    private final String tenantId;
    private final RedisNamespace redisNamespace;
    private final String redisInstanceId;
    private final EntityRevisionStore entityRevisionStore;

    public void insert(final E entity, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final boolean redisEnabled, final String sourceId) {
        final Map<Field<?>, Object> valueMap = new LinkedHashMap<>();

        for (final EntityProperty<?, ?> entityProperty : this.tableSchema.getPersistentProperties()) {
            valueMap.put(this.tableSchema.getField(entityProperty), ColumnValueMapper.readStored(entityProperty, entity));
        }

        if (this.tableSchema.isOptimisticLocking()) {
            final RevisionState revisionState = this.entityRevisionStore.getState(entity);

            valueMap.put(TableSchema.REVISION_FIELD, revisionState.getRevision());
        }

        this.pendingWriteStore.queueInsert(
                this.tableSchema,
                this.tenantId,
                entity.getId(),
                valueMap,
                new ArrayList<>(commitCallbackList),
                new ArrayList<>(failureCallbackList),
                this.createInvalidation(entity.getId(), redisEnabled, false, sourceId)
        );
    }

    public void insert(final E entity, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final boolean redisEnabled) {
        this.insert(entity, commitCallbackList, failureCallbackList, redisEnabled, this.redisInstanceId);
    }

    public void update(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final boolean redisEnabled, final String sourceId) {
        final Map<Field<?>, Object> valueMap = new LinkedHashMap<>();

        for (final EntityProperty<?, ?> entityProperty : entityProperties) {
            if (entityProperty.isPersistent()) {
                valueMap.put(this.tableSchema.getField(entityProperty), ColumnValueMapper.readStored(entityProperty, entity));
            }
        }

        if (valueMap.isEmpty()) {
            commitCallbackList.forEach(Runnable::run);
            return;
        }

        final RevisionState revisionState = this.tableSchema.isOptimisticLocking() ? this.entityRevisionStore.getState(entity) : null;

        final Long expectedRevision = revisionState == null ? null : revisionState.getRevision();

        Runnable revisionCallback = null;

        if (expectedRevision != null) {
            if (expectedRevision < 0L || expectedRevision == Long.MAX_VALUE) {
                throw new IllegalStateException("Invalid entity revision %s for %s".formatted(expectedRevision, entity.getId()));
            }

            valueMap.put(TableSchema.REVISION_FIELD, expectedRevision + 1L);

            revisionCallback = () -> revisionState.setRevision(expectedRevision + 1L);
        }

        this.pendingWriteStore.queueUpdate(
                this.tableSchema,
                this.tenantId,
                entity.getId(),
                valueMap,
                new ArrayList<>(commitCallbackList),
                new ArrayList<>(failureCallbackList),
                this.createInvalidation(entity.getId(), redisEnabled, sourceId),
                expectedRevision,
                revisionCallback,
                revisionState
        );
    }

    public void update(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final boolean redisEnabled) {
        this.update(entity, entityProperties, commitCallbackList, failureCallbackList, redisEnabled, this.redisInstanceId);
    }

    public void delete(final E entity, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final boolean redisEnabled, final String sourceId) {
        final RevisionState revisionState = this.tableSchema.isOptimisticLocking() ? this.entityRevisionStore.getState(entity) : null;

        final Long expectedRevision = revisionState == null ? null : revisionState.getRevision();

        this.pendingWriteStore.queueDelete(
                this.tableSchema,
                this.tenantId,
                entity.getId(),
                commitCallbackList,
                failureCallbackList,
                this.createInvalidation(entity.getId(), redisEnabled, sourceId),
                expectedRevision,
                revisionState
        );
    }

    public void delete(final E entity, final List<Runnable> commitCallbackList, final boolean redisEnabled, final String sourceId) {
        this.delete(entity, commitCallbackList, List.of(), redisEnabled, sourceId);
    }

    public void delete(final E entity, final List<Runnable> commitCallbackList, final boolean redisEnabled) {
        this.delete(entity, commitCallbackList, redisEnabled, this.redisInstanceId);
    }

    public void delete(final E entity, final boolean redisEnabled) {
        this.delete(entity, List.of(), redisEnabled);
    }

    private RedisInvalidation createInvalidation(final UUID id, final boolean redisEnabled, final boolean invalidateCache, final String sourceId) {
        return !redisEnabled || this.redisNamespace == null ? null : this.redisNamespace.getInvalidation(id, sourceId, invalidateCache);
    }

    public RedisInvalidation createInvalidation(final UUID id, final boolean redisEnabled, final String sourceId) {
        return this.createInvalidation(id, redisEnabled, true, sourceId == null ? this.redisInstanceId : sourceId);
    }

    public RedisInvalidation createInvalidation(final UUID id, final boolean redisEnabled) {
        return this.createInvalidation(id, redisEnabled, this.redisInstanceId);
    }

}