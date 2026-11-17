package me.trae.foundation.database.postgres.query;

import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.query.condition.Condition;
import me.trae.foundation.database.api.query.order.Direction;
import me.trae.foundation.database.api.query.order.Order;
import me.trae.foundation.database.postgres.write.PostgresWrite;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public final class PostgresQuery {

    public static List<org.jooq.Condition> conditions(final Query query, final String tenant) {
        final List<org.jooq.Condition> conditions = query.getConditions().stream()
                .map(PostgresQuery::condition)
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

    private static org.jooq.Condition condition(final Condition condition) {
        final Field<Object> field = DSL.field(DSL.name(condition.getColumn()));

        return switch (condition.getOperator()) {
            case EQUALS -> field.eq(condition.getValue());
            case NOT_EQUALS -> field.ne(condition.getValue());
            case GREATER_THAN -> field.gt(condition.getValue());
            case LESS_THAN -> field.lt(condition.getValue());
            case IN -> field.in(Collection.class.cast(condition.getValue()));
            case CONTAINS -> field.contains(condition.getValue());
        };
    }

    private static OrderField<?> order(final Order order) {
        final Field<Object> field = DSL.field(DSL.name(order.getColumn()));

        return order.getDirection() == Direction.ASCENDING ? field.asc() : field.desc();
    }
}