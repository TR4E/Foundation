package me.trae.foundation.database.core.repository;

import lombok.AllArgsConstructor;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.core.batch.PendingWriteStore;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.core.value.ColumnValueMapper;
import org.jooq.Field;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
public final class RepositoryWriter<E extends Entity> {

    private final PendingWriteStore pendingWriteStore;
    private final TableSchema<E> tableSchema;
    private final String tenantId;

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

        this.pendingWriteStore.queueUpsert(this.tableSchema, this.tenantId, entity.getId(), valueMap, commitCallbackList);
    }

    public void delete(final E entity) {
        this.pendingWriteStore.queueDelete(this.tableSchema, this.tenantId, entity.getId());
    }
}