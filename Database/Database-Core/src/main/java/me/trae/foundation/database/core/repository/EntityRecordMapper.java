package me.trae.foundation.database.core.repository;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.core.value.ColumnValueMapper;
import me.trae.foundation.database.storage.codec.EntityInstantiator;
import me.trae.foundation.database.storage.revision.EntityRevisionStore;
import org.jooq.Field;
import org.jooq.Record;

public final class EntityRecordMapper<E extends Entity> {

    private final TableSchema<E> tableSchema;
    private final EntityInstantiator<E> entityInstantiator;
    private final EntityRevisionStore entityRevisionStore;

    public EntityRecordMapper(final TableSchema<E> tableSchema, final EntityRevisionStore entityRevisionStore) {
        this.tableSchema = tableSchema;
        this.entityInstantiator = new EntityInstantiator<>(tableSchema.getEntityType());
        this.entityRevisionStore = entityRevisionStore;
    }

    public E map(final Record record) {
        final E entity = this.entityInstantiator.instantiate(record.get(TableSchema.ID_FIELD));

        for (final EntityProperty<?, ?> entityProperty : this.tableSchema.getPersistentProperties()) {
            final Field<?> field = this.tableSchema.getField(entityProperty);
            final Object stored = record.get(field, field.getType());

            if (stored != null) {
                ColumnValueMapper.writeStored(entityProperty, entity, stored);
            }
        }

        if (this.tableSchema.isOptimisticLocking()) {
            this.entityRevisionStore.setRevision(entity, record.get(TableSchema.REVISION_FIELD));
        }

        return entity;
    }
}
