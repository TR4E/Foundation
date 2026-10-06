package me.trae.foundation.database.core.repository;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.QueryException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.query.Direction;
import me.trae.foundation.database.api.query.Operator;
import me.trae.foundation.database.api.query.Order;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.query.TenantSelection;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.core.value.ColumnValueMapper;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.impl.DSL;

import java.util.Collection;
import java.util.List;

@UtilityClass
public class QueryRenderer {

    public <E extends Entity> Condition toCondition(final TableSchema<E> tableSchema, final Query<E> query, final String ownTenantId) {
        Condition condition = toTenantCondition(tableSchema, query.getTenantSelection(), query.getTenantId(), ownTenantId);

        for (final me.trae.foundation.database.api.query.Condition<E> queryCondition : query.getConditionList()) {
            condition = condition.and(render(tableSchema.getField(queryCondition.getEntityProperty()), queryCondition.getOperator(), toStoredValue(queryCondition.getEntityProperty(), queryCondition.getOperator(), queryCondition.getValue())));
        }

        return condition;
    }

    public <E extends Entity> List<SortField<?>> toSortFields(final TableSchema<E> tableSchema, final Query<E> query) {
        return query.getOrderList().stream()
                .<SortField<?>>map(order -> toSortField(tableSchema, order))
                .toList();
    }

    public Condition toTenantCondition(final TableSchema<?> tableSchema, final TenantSelection tenantSelection, final String specificTenantId, final String ownTenantId) {
        if (!tableSchema.isTenantScoped()) {
            return DSL.noCondition();
        }

        return switch (tenantSelection) {
            case OWN -> TableSchema.TENANT_FIELD.eq(ownTenantId);
            case SPECIFIC -> TableSchema.TENANT_FIELD.eq(specificTenantId);
            case ALL -> DSL.noCondition();
        };
    }

    private <E extends Entity> SortField<?> toSortField(final TableSchema<E> tableSchema, final Order<E> order) {
        final Field<?> field = tableSchema.getField(order.getEntityProperty());

        return order.getDirection() == Direction.ASCENDING ? field.asc() : field.desc();
    }

    private Object toStoredValue(final EntityProperty<?, ?> entityProperty, final Operator operator, final Object value) {
        return switch (operator) {
            case IN -> toCollection(value).stream().map(element -> ColumnValueMapper.toStoredObject(entityProperty, element)).toList();
            case LIKE, IS_NULL, IS_NOT_NULL -> value;
            default -> ColumnValueMapper.toStoredObject(entityProperty, value);
        };
    }

    private <Type> Condition render(final Field<Type> field, final Operator operator, final Object value) {
        return switch (operator) {
            case EQUALS -> field.eq(DSL.val(value, field));
            case NOT_EQUALS -> field.ne(DSL.val(value, field));
            case GREATER_THAN -> field.gt(DSL.val(value, field));
            case GREATER_THAN_OR_EQUALS -> field.ge(DSL.val(value, field));
            case LESS_THAN -> field.lt(DSL.val(value, field));
            case LESS_THAN_OR_EQUALS -> field.le(DSL.val(value, field));
            case IN -> field.in(toCollection(value).stream().map(element -> DSL.val(element, field)).toList());
            case LIKE -> field.like(String.valueOf(value));
            case IS_NULL -> field.isNull();
            case IS_NOT_NULL -> field.isNotNull();
        };
    }

    private Collection<?> toCollection(final Object value) {
        if (value instanceof final Collection<?> collection) {
            return collection;
        }

        throw new QueryException("IN requires a collection value");
    }
}