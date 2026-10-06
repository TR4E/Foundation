package me.trae.foundation.database.core.repository;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.core.value.ColumnValueMapper;
import me.trae.foundation.database.storage.codec.EntityInstantiator;
import org.jooq.Record;

public final class EntityRecordMapper<E extends Entity> {

    private final TableSchema<E> tableSchema;
    private final EntityInstantiator<E> entityInstantiator;

    public EntityRecordMapper(final TableSchema<E> tableSchema) {
        this.tableSchema = tableSchema;
        this.entityInstantiator = new EntityInstantiator<>(tableSchema.getEntityType());
    }

    public E map(final Record record) {
        final E entity = this.entityInstantiator.instantiate(record.get(TableSchema.ID_FIELD));

        for (final EntityProperty<?, ?> entityProperty : this.tableSchema.getPersistentProperties()) {
            final Object stored = record.get(this.tableSchema.getField(entityProperty));

            if (stored != null) {
                ColumnValueMapper.writeStored(entityProperty, entity, stored);
            }
        }

        return entity;
    }
}