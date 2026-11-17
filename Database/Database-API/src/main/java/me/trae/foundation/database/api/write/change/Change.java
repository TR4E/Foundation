package me.trae.foundation.database.api.write.change;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public final class Change {

    private final String column;
    private final ChangeType type;
    private final Object value;
}