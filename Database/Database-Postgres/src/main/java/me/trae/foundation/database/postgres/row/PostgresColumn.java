package me.trae.foundation.database.postgres.row;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.property.Property;
import org.jooq.Field;

@AllArgsConstructor
@Getter
public final class PostgresColumn {

    private final Property<?, ?> property;
    private final Field<?> field;
}