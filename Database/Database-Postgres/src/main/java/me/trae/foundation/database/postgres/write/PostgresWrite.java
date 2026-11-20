package me.trae.foundation.database.postgres.write;

import me.trae.foundation.database.api.exception.QueryException;
import me.trae.foundation.database.api.exception.VersionConflictException;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.WriteResult;
import me.trae.foundation.database.api.write.change.Change;
import me.trae.foundation.database.api.write.change.ChangeType;
import me.trae.foundation.database.api.write.change.Decrement;
import me.trae.foundation.database.postgres.value.PostgresValues;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertValuesStepN;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class PostgresWrite {

    public static final Field<UUID> ID = DSL.field(DSL.name("id"), UUID.class);
    public static final Field<Long> VERSION = DSL.field(DSL.name("version"), Long.class);
    public static final Field<String> TENANT = DSL.field(DSL.name("tenant"), String.class);

    private static final int ROWS = 256;

    public static WriteResult apply(final DSLContext dslContext, final Table<?> table, final List<Property<?, ?>> properties, final Write write) {
        return switch (write.getKind()) {
            case SAVE -> new WriteResult(save(dslContext, table, properties, write), null);
            case UPDATE -> update(dslContext, table, properties, write);
            case DELETE -> new WriteResult(delete(dslContext, table, write), null);
        };
    }

    public static void applyAll(final DSLContext dslContext, final Table<?> table, final List<Property<?, ?>> properties, final List<Write> writes) {
        dslContext.transaction(configuration -> batch(DSL.using(configuration), table, properties, writes));
    }

    private static void batch(final DSLContext dslContext, final Table<?> table, final List<Property<?, ?>> properties, final List<Write> writes) {
        List<Write> pending = writes;

        while (!pending.isEmpty()) {
            final LinkedHashMap<UUID, Write> pass = new LinkedHashMap<>();

            final List<Write> deferred = new ArrayList<>();

            pending.forEach(write -> partition(pass, deferred, write));

            execute(dslContext, table, properties, new ArrayList<>(pass.values()));

            pending = deferred;
        }
    }

    private static void partition(final LinkedHashMap<UUID, Write> pass, final List<Write> deferred, final Write write) {
        if (pass.containsKey(write.getKey().getId())) {
            deferred.add(write);
            return;
        }

        pass.put(write.getKey().getId(), write);
    }

    private static void execute(final DSLContext dslContext, final Table<?> table, final List<Property<?, ?>> properties, final List<Write> writes) {
        final LinkedHashMap<String, List<Write>> groupMap = new LinkedHashMap<>();

        writes.forEach(write -> groupMap.computeIfAbsent(signature(write), _ -> new ArrayList<>()).add(write));

        groupMap.values().forEach(group -> group(dslContext, table, properties, group));
    }

    private static void group(final DSLContext dslContext, final Table<?> table, final List<Property<?, ?>> properties, final List<Write> writes) {
        if (writes.getFirst().getKind() == WriteKind.DELETE) {
            deleteAll(dslContext, table, writes);
            return;
        }

        for (int index = 0; index < writes.size(); index += ROWS) {
            upsertAll(dslContext, table, properties, writes.subList(index, Math.min(index + ROWS, writes.size())));
        }
    }

    private static void deleteAll(final DSLContext dslContext, final Table<?> table, final List<Write> writes) {
        final List<UUID> ids = writes.stream().map(write -> write.getKey().getId()).distinct().collect(Collectors.toCollection(ArrayList::new));

        final String tenant = writes.getFirst().getKey().getTenant();

        if (tenant == null) {
            dslContext.deleteFrom(table).where(ID.in(ids)).execute();
            return;
        }

        dslContext.deleteFrom(table).where(TENANT.eq(tenant)).and(ID.in(ids)).execute();
    }

    private static void upsertAll(final DSLContext dslContext, final Table<?> table, final List<Property<?, ?>> properties, final List<Write> writes) {
        final Write first = writes.getFirst();

        final boolean tenanted = first.getKey().getTenant() != null;

        final List<Field<?>> fields = new ArrayList<>();

        fields.add(ID);

        if (tenanted) {
            fields.add(TENANT);
        }

        fields.add(VERSION);

        first.getChanges().forEach(change -> fields.add(column(change)));

        InsertValuesStepN<?> step = dslContext.insertInto(table, fields);

        for (final Write write : writes) {
            step = step.values(row(properties, write, tenanted));
        }

        step.onConflict(tenanted ? new Field<?>[]{TENANT, ID} : new Field<?>[]{ID})
                .doUpdate()
                .set(updates(table, first))
                .execute();
    }

    private static List<Object> row(final List<Property<?, ?>> properties, final Write write, final boolean tenanted) {
        final List<Object> values = new ArrayList<>();

        values.add(write.getKey().getId());

        if (tenanted) {
            values.add(write.getKey().getTenant());
        }

        values.add(1L);

        write.getChanges().forEach(change -> values.add(value(properties, change)));

        return values;
    }

    private static Map<Field<?>, Object> updates(final Table<?> table, final Write write) {
        final LinkedHashMap<Field<?>, Object> updates = new LinkedHashMap<>();

        updates.put(VERSION, DSL.field(DSL.name(table.getName(), "version"), Long.class).add(1));

        write.getChanges().forEach(change -> update(updates, table, change));

        return updates;
    }

    private static void update(final LinkedHashMap<Field<?>, Object> updates, final Table<?> table, final Change change) {
        if (change.getType() == ChangeType.INCREMENT) {
            final Field<Long> field = DSL.field(DSL.name(change.getColumn()), Long.class);

            updates.put(field, DSL.coalesce(DSL.field(DSL.name(table.getName(), change.getColumn()), Long.class), DSL.inline(0L)).add(DSL.excluded(field)));
            return;
        }

        final Field<Object> field = DSL.field(DSL.name(change.getColumn()));

        updates.put(field, DSL.excluded(field));
    }

    private static Field<?> column(final Change change) {
        if (change.getType() == ChangeType.INCREMENT) {
            return DSL.field(DSL.name(change.getColumn()), Long.class);
        }

        return DSL.field(DSL.name(change.getColumn()));
    }

    private static Object value(final List<Property<?, ?>> properties, final Change change) {
        if (change.getType() == ChangeType.ADD || change.getType() == ChangeType.REMOVE) {
            throw new QueryException("%s on column %s has no Postgres form, use a child entity".formatted(change.getType(), change.getColumn()));
        }

        if (change.getType() == ChangeType.INCREMENT) {
            return Long.class.cast(change.getValue());
        }

        return PostgresValues.write(serialize(properties, change));
    }

    private static String signature(final Write write) {
        return write.getKind().name() + "|" + write.getKey().getTenant() + "|" + write.getChanges().stream()
                .map(change -> change.getColumn() + "=" + change.getType().name())
                .collect(Collectors.joining(","));
    }

    private static long save(final DSLContext dslContext, final Table<?> table, final List<Property<?, ?>> properties, final Write write) {
        final Map<Field<?>, Object> values = values(properties, write);

        values.put(ID, write.getKey().getId());

        if (write.getKey().getTenant() != null) {
            values.put(TENANT, write.getKey().getTenant());
        }

        final LinkedHashMap<Field<?>, Object> inserted = new LinkedHashMap<>(values);

        final LinkedHashMap<Field<?>, Object> updated = new LinkedHashMap<>(values);

        inserted.put(VERSION, write.getExpectedVersion() + 1);

        updated.put(VERSION, DSL.field(DSL.name(table.getName(), "version"), Long.class).add(1));

        return dslContext.insertInto(table)
                .set(inserted)
                .onConflict(write.getKey().getTenant() == null ? new Field<?>[]{ID} : new Field<?>[]{TENANT, ID})
                .doUpdate()
                .set(updated)
                .returningResult(VERSION)
                .fetchOne(0, long.class);
    }

    private static WriteResult update(final DSLContext dslContext, final Table<?> table, final List<Property<?, ?>> properties, final Write write) {
        final Field<Long> counter = counter(write);

        final List<Field<?>> returning = new ArrayList<>();

        returning.add(VERSION);

        if (counter != null) {
            returning.add(counter);
        }

        final Record record = dslContext.update(table)
                .set(values(properties, write))
                .set(VERSION, VERSION.add(1))
                .where(conditions(write))
                .returningResult(returning)
                .fetchOne();

        if (record == null) {
            return new WriteResult(unmatched(write), null);
        }

        return new WriteResult(record.get(0, long.class), counter == null ? null : record.get(1, Long.class));
    }

    private static Field<Long> counter(final Write write) {
        if (write.getChanges().size() != 1) {
            return null;
        }

        final Change change = write.getChanges().getFirst();

        if (change.getType() != ChangeType.INCREMENT && !(change.getValue() instanceof Decrement)) {
            return null;
        }

        return DSL.field(DSL.name(change.getColumn()), Long.class);
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

    private static long unmatched(final Write write) {
        if (guards(write).isEmpty()) {
            throw new VersionConflictException("Update of %s expected version %s".formatted(write.getKey().getId(), write.getExpectedVersion()));
        }

        return Decrement.REFUSED;
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

        if (change.getValue() instanceof final Decrement decrement) {
            final Field<Long> field = DSL.field(DSL.name(change.getColumn()), Long.class);

            values.put(field, DSL.coalesce(field, 0L).sub(decrement.getDelta()));
            return;
        }

        if (change.getType() == ChangeType.INCREMENT) {
            final Field<Long> field = DSL.field(DSL.name(change.getColumn()), Long.class);

            values.put(field, DSL.coalesce(field, 0L).add(Long.class.cast(change.getValue())));
            return;
        }

        values.put(DSL.field(DSL.name(change.getColumn())), PostgresValues.write(serialize(properties, change)));
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

    private static List<Change> guards(final Write write) {
        return write.getChanges().stream()
                .filter(change -> change.getValue() instanceof Decrement)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<Condition> conditions(final Write write) {
        final List<Condition> conditions = new ArrayList<>();

        conditions.add(ID.eq(write.getKey().getId()));

        if (write.getKey().getTenant() != null) {
            conditions.add(TENANT.eq(write.getKey().getTenant()));
        }

        final List<Change> guards = guards(write);

        if (guards.isEmpty()) {
            if (write.getExpectedVersion() > 0 && !commutative(write)) {
                conditions.add(VERSION.eq(write.getExpectedVersion()));
            }

            return conditions;
        }

        guards.forEach(change -> conditions.add(guard(change)));

        return conditions;
    }

    private static boolean commutative(final Write write) {
        if (write.getChanges().isEmpty()) {
            return false;
        }

        return write.getChanges().stream().allMatch(change -> change.getType() == ChangeType.INCREMENT || change.getValue() instanceof Decrement);
    }

    private static Condition guard(final Change change) {
        final Field<Long> field = DSL.field(DSL.name(change.getColumn()), Long.class);

        final Decrement decrement = Decrement.class.cast(change.getValue());

        return DSL.coalesce(field, 0L).sub(decrement.getDelta()).ge(decrement.getFloor());
    }
}