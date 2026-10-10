package me.trae.foundation.database.core.repository;

import lombok.AllArgsConstructor;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.entity.RevisionedEntity;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.core.batch.PendingWriteStore;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.core.value.ColumnValueMapper;
import me.trae.foundation.database.storage.redis.RedisInvalidation;
import me.trae.foundation.database.storage.redis.RedisNamespace;
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

    public void update(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties, final List<Runnable> commitCallbackList) {
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

        final Long expectedRevision = this.tableSchema.isOptimisticLocking() ? ((RevisionedEntity) entity).getRevision() : null;
        Runnable revisionCallback = null;

        if (expectedRevision != null) {
            if (expectedRevision < 0L || expectedRevision == Long.MAX_VALUE) {
                throw new IllegalStateException("Invalid entity revision %s for %s".formatted(expectedRevision, entity.getId()));
            }

            valueMap.put(TableSchema.REVISION_FIELD, expectedRevision + 1L);
            revisionCallback = () -> ((RevisionedEntity) entity).setRevision(expectedRevision + 1L);
        }

        this.pendingWriteStore.queueUpsert(this.tableSchema, this.tenantId, entity.getId(), valueMap, new ArrayList<>(commitCallbackList), this.createInvalidation(entity.getId()), expectedRevision, revisionCallback);
    }

    public void delete(final E entity) {
        this.delete(entity, List.of());
    }

    public void delete(final E entity, final List<Runnable> commitCallbackList) {
        final Long expectedRevision = this.tableSchema.isOptimisticLocking() ? ((RevisionedEntity) entity).getRevision() : null;
        this.pendingWriteStore.queueDelete(this.tableSchema, this.tenantId, entity.getId(), commitCallbackList, this.createInvalidation(entity.getId()), expectedRevision);
    }

    private RedisInvalidation createInvalidation(final java.util.UUID id) {
        return this.redisNamespace == null ? null : this.redisNamespace.getInvalidation(id, this.redisInstanceId);
    }

}