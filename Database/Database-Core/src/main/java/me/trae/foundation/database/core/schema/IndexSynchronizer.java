package me.trae.foundation.database.core.schema;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.repository.index.EntityIndex;
import me.trae.foundation.database.api.repository.index.IndexType;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@UtilityClass
public class IndexSynchronizer {

    private final String PREFIX = "fdx_";
    private final int MAXIMUM_NAME_LENGTH = 63;

    public void synchronize(final DSLContext dslContext, final TableSchema<?> tableSchema, final Map<? extends EntityProperty<?, ?>, IndexType> indexMap) {
        synchronize(dslContext, tableSchema, indexMap, List.of());
    }

    public void synchronize(final DSLContext dslContext, final TableSchema<?> tableSchema, final Map<? extends EntityProperty<?, ?>, IndexType> indexMap, final List<? extends EntityIndex<?>> compositeIndexList) {
        if (indexMap.containsValue(IndexType.GIN_TRGM)) {
            dslContext.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        }

        final Set<String> declaredSet = new HashSet<>();

        indexMap.forEach((entityProperty, indexType) -> {
            final String name = getIndexName(tableSchema, entityProperty, indexType);

            declaredSet.add(name);

            create(dslContext, tableSchema, name, entityProperty, indexType);
        });

        for (final EntityIndex<?> entityIndex : compositeIndexList) {
            final String name = getIndexName(tableSchema, entityIndex);
            declaredSet.add(name);
            final List<Field<?>> fieldList = new ArrayList<>();

            if (tableSchema.isTenantScoped()) {
                fieldList.add(TableSchema.TENANT_FIELD);
            }

            entityIndex.getProperties().forEach(property -> fieldList.add(getIndexField(tableSchema, property)));
            dslContext.createUniqueIndexIfNotExists(DSL.name(name)).on(tableSchema.getTable(), fieldList).execute();
        }

        for (final String existing : getExistingIndexes(dslContext, tableSchema)) {
            if (!declaredSet.contains(existing)) {
                dslContext.dropIndexIfExists(DSL.name(existing)).execute();
            }
        }
    }

    private void create(final DSLContext dslContext, final TableSchema<?> tableSchema, final String name, final EntityProperty<?, ?> entityProperty, final IndexType indexType) {
        final Field<?> field = tableSchema.getField(entityProperty);

        switch (indexType) {
            case BTREE -> dslContext.createIndexIfNotExists(DSL.name(name)).on(tableSchema.getTable(), getIndexField(tableSchema, entityProperty)).execute();
            case UNIQUE -> dslContext.createUniqueIndexIfNotExists(DSL.name(name)).on(tableSchema.getTable(), tableSchema.isTenantScoped() ? List.of(TableSchema.TENANT_FIELD, getIndexField(tableSchema, entityProperty)) : List.of(getIndexField(tableSchema, entityProperty))).execute();
            case GIN_TRGM -> dslContext.execute("CREATE INDEX IF NOT EXISTS {0} ON {1} USING gin ({2} gin_trgm_ops)", DSL.name(name), tableSchema.getTable(), DSL.name(field.getName()));
            case BRIN -> dslContext.execute("CREATE INDEX IF NOT EXISTS {0} ON {1} USING brin ({2})", DSL.name(name), tableSchema.getTable(), DSL.name(field.getName()));
        }
    }

    private Field<?> getIndexField(final TableSchema<?> tableSchema, final EntityProperty<?, ?> entityProperty) {
        final Field<?> field = tableSchema.getField(entityProperty);

        return entityProperty.isCaseInsensitive() ? DSL.lower(field.coerce(String.class)) : field;
    }

    private List<String> getExistingIndexes(final DSLContext dslContext, final TableSchema<?> tableSchema) {
        return dslContext.resultQuery(
                "SELECT indexname FROM pg_indexes WHERE schemaname = current_schema() AND tablename = {0} AND indexname LIKE {1}",
                DSL.inline(tableSchema.getTableName()),
                DSL.inline(PREFIX + "%")
        ).fetch(0, String.class);
    }

    private String getIndexName(final TableSchema<?> tableSchema, final EntityProperty<?, ?> entityProperty, final IndexType indexType) {
        final String name = "%s%s_%s_%s".formatted(PREFIX, tableSchema.getTableName(), entityProperty.getName(), indexType.name().toLowerCase(Locale.ROOT));

        if (name.length() <= MAXIMUM_NAME_LENGTH) {
            return name;
        }

        return PREFIX + UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private String getIndexName(final TableSchema<?> tableSchema, final EntityIndex<?> entityIndex) {
        final String propertyNames = entityIndex.getProperties().stream().map(EntityProperty::getName).collect(java.util.stream.Collectors.joining("_"));
        final String name = "%s%s_%s_unique".formatted(PREFIX, tableSchema.getTableName(), propertyNames);

        if (name.length() <= MAXIMUM_NAME_LENGTH) {
            return name;
        }

        return PREFIX + UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }
}