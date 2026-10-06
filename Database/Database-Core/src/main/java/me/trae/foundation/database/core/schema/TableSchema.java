package me.trae.foundation.database.core.schema;

import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.QueryException;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.tenant.TenantScope;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
public class TableSchema<E extends Entity> {

    public static final Field<UUID> ID_FIELD = DSL.field(DSL.name("id"), SQLDataType.UUID.nullable(false));
    public static final Field<String> TENANT_FIELD = DSL.field(DSL.name("tenant_id"), SQLDataType.VARCHAR(64).nullable(false));

    private static final Set<String> RESERVED_NAMES = Set.of(ID_FIELD.getName(), TENANT_FIELD.getName());

    private final Class<E> entityType;
    private final Table<Record> table;
    private final TenantScope tenantScope;
    private final List<EntityProperty<?, ?>> persistentProperties;
    private final Map<String, Field<?>> fieldMap = new LinkedHashMap<>();

    public TableSchema(final Class<E> entityType, final String tableName, final TenantScope tenantScope, final List<EntityProperty<?, ?>> entityProperties) {
        this.entityType = entityType;
        this.table = DSL.table(DSL.name(tableName));
        this.tenantScope = tenantScope;
        this.persistentProperties = entityProperties.stream().filter(EntityProperty::isPersistent).toList();

        for (final EntityProperty<?, ?> entityProperty : this.persistentProperties) {
            if (RESERVED_NAMES.contains(entityProperty.getName())) {
                throw new SchemaException("Property %s on %s uses a reserved column name".formatted(entityProperty.getName(), entityType.getName()));
            }

            this.fieldMap.put(entityProperty.getName(), DSL.field(DSL.name(entityProperty.getName()), DataTypeMapper.getDataType(entityProperty)));
        }
    }

    public String getTableName() {
        return this.table.getName();
    }

    public boolean isTenantScoped() {
        return this.tenantScope != TenantScope.NONE;
    }

    public List<Field<?>> getKeyFields() {
        return this.isTenantScoped() ? List.of(TENANT_FIELD, ID_FIELD) : List.of(ID_FIELD);
    }

    public Collection<Field<?>> getColumnFields() {
        return this.fieldMap.values();
    }

    public Field<?> getField(final EntityProperty<?, ?> entityProperty) {
        final Field<?> field = this.fieldMap.get(entityProperty.getName());
        if (field == null) {
            throw new QueryException("Property %s is not persisted in %s".formatted(entityProperty.getName(), this.getTableName()));
        }

        return field;
    }

    public Condition getKeyCondition(final String tenantId, final UUID id) {
        final Condition condition = ID_FIELD.eq(id);

        return this.isTenantScoped() ? condition.and(TENANT_FIELD.eq(tenantId)) : condition;
    }
}