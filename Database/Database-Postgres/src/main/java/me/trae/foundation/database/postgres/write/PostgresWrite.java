package me.trae.foundation.database.postgres.write;

import me.trae.foundation.database.api.exception.QueryException;
import me.trae.foundation.database.api.exception.VersionConflictException;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.change.Change;
import me.trae.foundation.database.api.write.change.ChangeType;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PostgresWrite {

    public static final Field<UUID> ID = DSL.field(DSL.name("id"), UUID.class);
    public static final Field<Long> VERSION = DSL.field(DSL.name("version"), Long.class);
    public static final Field<String> TENANT = DSL.field(DSL.name("tenant"), String.class);

    public static long apply(final DSLContext dslContext, final Table<?> table, final List<Property<?, ?>> properties, final Write write) {
        return switch (write.getKind()) {
            case SAVE -> save(dslContext, table, properties, write);
            case UPDATE -> update(dslContext, table, properties, write);
            case DELETE -> delete(dslContext, table, write);
        };
    }

    private static long save(final DSLContext dslContext, final Table<?> table, final List<Property<?, ?>> properties, final Write write) {
        final Map<Field<?>, Object> values = values(properties, write);

        values.put(ID, write.getKey().getId());
        values.put(VERSION, write.getExpectedVersion());

        if (write.getKey().getTenant() != null) {
            values.put(TENANT, write.getKey().getTenant());
        }

        return dslContext.insertInto(table)
                .set(values)
                .onConflict(ID)
                .doUpdate()
                .set(values)
                .returningResult(VERSION)
                .fetchOne(0, long.class);
    }

    private static long update(final DSLContext dslContext, final Table<?> table, final List<Property<?, ?>> properties, final Write write) {
        return Optional.ofNullable(dslContext.update(table)
                        .set(values(properties, write))
                        .set(VERSION, VERSION.add(1))
                        .where(conditions(write))
                        .returningResult(VERSION)
                        .fetchOne())
                .map(record -> record.get(0, long.class))
                .orElseThrow(() -> new VersionConflictException("Update of %s expected version %s".formatted(write.getKey().getId(), write.getExpectedVersion())));
    }

    private static long delete(final DSLContext dslContext, final Table<?> table, final Write write) {
        final int deleted = dslContext.deleteFrom(table)
                .where(conditions(write))
                .execute();

        if (deleted == 0 && write.getExpectedVersion() > 0) {
            throw new VersionConflictException("Delete of %s expected version %s".formatted(write.getKey().getId(), write.getExpectedVersion()));
        }

        return write.getExpectedVersion();
    }

    private static Map<Field<?>, Object> values(final List<Property<?, ?>> properties, final Write write) {
        final LinkedHashMap<Field<?>, Object> values = new LinkedHashMap<>();

        write.getChanges().forEach(change -> put(values, properties, change));

        return values;
    }

    private static void put(final LinkedHashMap<Field<?>, Object> values, final List<Property<?, ?>> properties, final Change change) {
        if (change.getType() == ChangeType.ADD || change.getType() == ChangeType.REMOVE) {
            throw new QueryException("%s on column %s has no Postgres form, use a child entity".formatted(change.getType(), change.getColumn()));
        }

        if (change.getType() == ChangeType.INCREMENT) {
            final Field<Long> field = DSL.field(DSL.name(change.getColumn()), Long.class);

            values.put(field, field.add(Long.class.cast(change.getValue())));
            return;
        }

        values.put(DSL.field(DSL.name(change.getColumn())), serialize(properties, change));
    }

    private static Object serialize(final List<Property<?, ?>> properties, final Change change) {
        return properties.stream()
                .filter(property -> property.getColumn().equals(change.getColumn()))
                .findFirst()
                .map(property -> serialize(property, change.getValue()))
                .orElse(change.getValue());
    }

    private static <Owner, Value> Object serialize(final Property<Owner, Value> property, final Object value) {
        return property.getConverter() == null ? value : property.getConverter().serialize(property.getType().cast(value));
    }

    private static List<org.jooq.Condition> conditions(final Write write) {
        final List<org.jooq.Condition> conditions = new ArrayList<>();

        conditions.add(ID.eq(write.getKey().getId()));

        if (write.getKey().getTenant() != null) {
            conditions.add(TENANT.eq(write.getKey().getTenant()));
        }

        if (write.getExpectedVersion() > 0) {
            conditions.add(VERSION.eq(write.getExpectedVersion()));
        }

        return conditions;
    }
}