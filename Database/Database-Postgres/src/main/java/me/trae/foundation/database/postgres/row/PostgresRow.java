package me.trae.foundation.database.postgres.row;

import lombok.Getter;
import me.trae.foundation.database.api.converter.ValueConverter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.entity.EntityInstantiator;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.postgres.value.PostgresValues;
import me.trae.foundation.database.postgres.write.PostgresWrite;
import org.jooq.Field;

import java.util.ArrayList;
import java.util.List;

@Getter
public final class PostgresRow<EntityType extends Entity> {

    private final List<PostgresColumn> columns = new ArrayList<>();
    private final List<Field<?>> fields = new ArrayList<>();

    private final EntityInstantiator<EntityType> instantiator;
    private final List<Property<?, ?>> properties;

    public PostgresRow(final EntityInstantiator<EntityType> instantiator, final List<Property<?, ?>> properties) {
        this.instantiator = instantiator;
        this.properties = properties;

        this.fields.add(PostgresWrite.ID);

        properties.stream()
                .filter(Property::isPersistent)
                .forEach(this::column);
    }

    public EntityType map(final org.jooq.Record record) {
        final EntityType entity = this.instantiator.instantiate(record.get(PostgresWrite.ID));

        this.columns.forEach(column -> set(column.getProperty(), column.getField(), entity, record));

        return entity;
    }

    private void column(final Property<?, ?> property) {
        final PostgresColumn column = new PostgresColumn(property, field(property));

        this.columns.add(column);
        this.fields.add(column.getField());
    }

    private static Field<?> field(final Property<?, ?> property) {
        final ValueConverter<?, ?> converter = property.getConverter();

        final Class<?> type = converter == null ? property.getType() : converter.getStoredType();

        return PostgresValues.field(property.getColumn(), type);
    }

    private static <Owner, Value> void set(final Property<Owner, Value> property, final Field<?> field, final Object entity, final org.jooq.Record record) {
        final ValueConverter<Value, ?> converter = property.getConverter();

        if (converter == null) {
            property.set(property.getEntityType().cast(entity), PostgresValues.read(record, field, property.getType()));
            return;
        }

        property.set(property.getEntityType().cast(entity), convert(record, field, converter));
    }

    private static <Value, Stored> Value convert(final org.jooq.Record record, final Field<?> field, final ValueConverter<Value, Stored> converter) {
        final Stored stored = PostgresValues.read(record, field, converter.getStoredType());

        return stored == null ? null : converter.deserialize(stored);
    }
}