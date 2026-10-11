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

@AllArgsConstructor
public final class RepositoryWriter<E extends Entity> {

    private final PendingWriteStore pendingWriteStore;
    private final TableSchema<E> tableSchema;
    private final String tenantId;
    private final RedisNamespace redisNamespace;
    private final String redisInstanceId;
    private final EntityRevisionStore entityRevisionStore;

    public void insert(final E entity, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final boolean redisEnabled) {
        final Map<Field<?>, Object> valueMap = new LinkedHashMap<>();

        for (final EntityProperty<?, ?> entityProperty : this.tableSchema.getPersistentProperties()) {
            valueMap.put(this.tableSchema.getField(entityProperty), ColumnValueMapper.readStored(entityProperty, entity));
        }

        if (this.tableSchema.isOptimisticLocking()) {
            final RevisionState revisionState = this.entityRevisionStore.getState(entity);
            valueMap.put(TableSchema.REVISION_FIELD, revisionState.getRevision());
        }

        this.pendingWriteStore.queueInsert(this.tableSchema, this.tenantId, entity.getId(), valueMap, new ArrayList<>(commitCallbackList), new ArrayList<>(failureCallbackList), this.createInvalidation(entity.getId(), redisEnabled));
    }

    public void update(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final boolean redisEnabled) {
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

        this.pendingWriteStore.queueUpdate(this.tableSchema, this.tenantId, entity.getId(), valueMap, new ArrayList<>(commitCallbackList), new ArrayList<>(failureCallbackList), this.createInvalidation(entity.getId(), redisEnabled), expectedRevision, revisionCallback, revisionState);
    }

    public void delete(final E entity, final boolean redisEnabled) {
        this.delete(entity, List.of(), redisEnabled);
    }

    public void delete(final E entity, final List<Runnable> commitCallbackList, final boolean redisEnabled) {
        final RevisionState revisionState = this.tableSchema.isOptimisticLocking() ? this.entityRevisionStore.getState(entity) : null;

        final Long expectedRevision = revisionState == null ? null : revisionState.getRevision();
        this.pendingWriteStore.queueDelete(this.tableSchema, this.tenantId, entity.getId(), commitCallbackList, this.createInvalidation(entity.getId(), redisEnabled), expectedRevision, revisionState);
    }

    private RedisInvalidation createInvalidation(final java.util.UUID id, final boolean redisEnabled) {
        return !redisEnabled || this.redisNamespace == null ? null : this.redisNamespace.getInvalidation(id, this.redisInstanceId);
    }

}