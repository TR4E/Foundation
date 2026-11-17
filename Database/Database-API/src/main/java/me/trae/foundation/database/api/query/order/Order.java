package me.trae.foundation.database.api.query.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public final class Order {

    private final String column;
    private final Direction direction;
}