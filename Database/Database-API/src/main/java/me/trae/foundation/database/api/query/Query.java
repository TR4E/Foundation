package me.trae.foundation.database.api.query;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.property.EntityProperty;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class Query<E extends Entity> {

    private final List<Condition<E>> conditionList = new ArrayList<>();
    private final List<Order<E>> orderList = new ArrayList<>();

    private int limit, offset;
    private TenantSelection tenantSelection = TenantSelection.OWN;
    private String tenantId;

    public static <E extends Entity> Query<E> of(final Class<E> entityType) {
        return new Query<>();
    }

    public static <E extends Entity, Value> Query<E> where(final EntityProperty<? super E, Value> entityProperty, final Operator operator, final Value value) {
        return new Query<E>().and(entityProperty, operator, value);
    }

    public <Value> Query<E> and(final EntityProperty<? super E, Value> entityProperty, final Operator operator, final Value value) {
        this.conditionList.add(new Condition<>(entityProperty, operator, value));
        return this;
    }

    public Query<E> orderBy(final EntityProperty<? super E, ?> entityProperty, final Direction direction) {
        this.orderList.add(new Order<>(entityProperty, direction));
        return this;
    }

    public Query<E> limit(final int limit) {
        this.limit = limit;
        return this;
    }

    public Query<E> offset(final int offset) {
        this.offset = offset;
        return this;
    }

    public Query<E> tenant(final String tenantId) {
        this.tenantSelection = TenantSelection.SPECIFIC;
        this.tenantId = tenantId;
        return this;
    }

    public Query<E> allTenants() {
        this.tenantSelection = TenantSelection.ALL;
        this.tenantId = null;
        return this;
    }
}