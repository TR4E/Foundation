package me.trae.foundation.database.core.schema;

import lombok.experimental.UtilityClass;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;

@UtilityClass
public class SchemaSynchronizer {

    public void synchronize(final DSLContext dslContext, final TableSchema<?> tableSchema) {
        dslContext.createTableIfNotExists(tableSchema.getTable())
                .columns(tableSchema.getKeyFields())
                .constraint(DSL.primaryKey(tableSchema.getKeyFields().toArray(Field[]::new)))
                .execute();

        for (final Field<?> field : tableSchema.getColumnFields()) {
            dslContext.alterTable(tableSchema.getTable()).addColumnIfNotExists(field).execute();
        }
    }
}