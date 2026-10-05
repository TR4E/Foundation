package me.trae.foundation.database.api.query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.property.EntityProperty;

@AllArgsConstructor
@Getter
public final class Condition<E extends Entity> {

    private final EntityProperty<E, ?> entityProperty;
    private final Operator operator;
    private final Object value;
}