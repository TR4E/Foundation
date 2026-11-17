package me.trae.foundation.database.api.query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.query.condition.Condition;
import me.trae.foundation.database.api.query.condition.Operator;
import me.trae.foundation.database.api.query.order.Direction;
import me.trae.foundation.database.api.query.order.Order;

import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@Getter
public final class Query {

    private final List<Condition> conditions;
    private final List<Order> orders;
    private final int limit, offset;

    public static <Value> Query where(final Property<?, Value> property, final Value value) {
        return new Query(List.of(new Condition(property.getColumn(), Operator.EQUALS, value)), Collections.emptyList(), 0, 0);
    }

    public static Query ordered(final Property<?, ?> property, final Direction direction, final int limit) {
        return new Query(Collections.emptyList(), List.of(new Order(property.getColumn(), direction)), limit, 0);
    }

    public static Query all() {
        return new Query(Collections.emptyList(), Collections.emptyList(), 0, 0);
    }
}