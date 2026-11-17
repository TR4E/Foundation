package me.trae.foundation.database.api.query.condition;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public final class Condition {

    private final String column;
    private final Operator operator;
    private final Object value;
}