package me.trae.foundation.database.postgres.query;

import me.trae.foundation.database.api.converter.ValueConverter;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.query.condition.Condition;
import me.trae.foundation.database.api.query.order.Direction;
import me.trae.foundation.database.api.query.order.Order;
import me.trae.foundation.database.postgres.value.PostgresValues;
import me.trae.foundation.database.postgres.write.PostgresWrite;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public final class PostgresQuery {

    public static List<org.jooq.Condition> conditions(final Query query, final String tenant, final List<Property<?, ?>> properties) {
        final List<org.jooq.Condition> conditions = query.getConditions().stream()
                .map(condition -> condition(condition, properties))
                .collect(Collectors.toCollection(ArrayList::new));

        if (tenant != null) {
            conditions.add(PostgresWrite.TENANT.eq(tenant));
        }

        return conditions;
    }

    public static List<OrderField<?>> orders(final Query query) {
        return query.getOrders().stream()
                .map(PostgresQuery::order)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static org.jooq.Condition condition(final Condition condition, final List<Property<?, ?>> properties) {
        final Field<Object> field = DSL.field(DSL.name(condition.getColumn()));

        final Object value = stored(condition.getValue(), properties, condition.getColumn());

        return switch (condition.getOperator()) {
            case EQUALS -> field.eq(value);
            case NOT_EQUALS -> field.ne(value);
            case GREATER_THAN -> field.gt(value);
            case LESS_THAN -> field.lt(value);
            case IN -> field.in(Collection.class.cast(value));
            case CONTAINS -> field.contains(value);
        };
    }

    private static Object stored(final Object value, final List<Property<?, ?>> properties, final String column) {
        if (value == null) {
            return null;
        }

        return properties.stream()
                .filter(property -> property.getColumn().equals(column))
                .findFirst()
                .map(property -> serialize(property, value))
                .orElseGet(() -> PostgresValues.write(value));
    }

    private static <Owner, Value> Object serialize(final Property<Owner, Value> property, final Object value) {
        final ValueConverter<Value, ?> converter = property.getConverter();

        if (converter == null) {
            return PostgresValues.write(value);
        }

        return PostgresValues.write(converter.serialize(property.getType().cast(value)));
    }

    private static OrderField<?> order(final Order order) {
        final Field<Object> field = DSL.field(DSL.name(order.getColumn()));

        return order.getDirection() == Direction.ASCENDING ? field.asc() : field.desc();
    }
}