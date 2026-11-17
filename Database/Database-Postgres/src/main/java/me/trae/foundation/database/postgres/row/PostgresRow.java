package me.trae.foundation.database.postgres.row;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.converter.ValueConverter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.entity.EntityInstantiator;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.postgres.write.PostgresWrite;
import org.jooq.impl.DSL;

import java.util.List;

@AllArgsConstructor
@Getter
public final class PostgresRow<EntityType extends Entity> {

    private final EntityInstantiator<EntityType> instantiator;
    private final List<Property<?, ?>> properties;

    public EntityType map(final org.jooq.Record record) {
        final EntityType entity = this.instantiator.instantiate(record.get(PostgresWrite.ID));

        this.properties.stream()
                .filter(Property::isPersistent)
                .forEach(property -> set(property, entity, record));

        return entity;
    }

    private static <Owner, Value> void set(final Property<Owner, Value> property, final Object entity, final org.jooq.Record record) {
        final ValueConverter<Value, ?> converter = property.getConverter();

        if (converter == null) {
            property.set(property.getEntityType().cast(entity), record.get(DSL.field(DSL.name(property.getColumn()), property.getType())));
            return;
        }

        property.set(property.getEntityType().cast(entity), convert(record, property.getColumn(), converter));
    }

    private static <Value, Stored> Value convert(final org.jooq.Record record, final String column, final ValueConverter<Value, Stored> converter) {
        final Stored stored = record.get(DSL.field(DSL.name(column), converter.getStoredType()));

        return stored == null ? null : converter.deserialize(stored);
    }
}