package me.trae.foundation.database.api.query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.property.EntityProperty;

@AllArgsConstructor
@Getter
public final class Order<E extends Entity> {

    private final EntityProperty<E, ?> entityProperty;
    private final Direction direction;
}