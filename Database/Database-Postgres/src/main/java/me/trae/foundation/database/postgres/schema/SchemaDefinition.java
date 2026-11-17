package me.trae.foundation.database.postgres.schema;

import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.postgres.index.PostgresIndexType;
import me.trae.foundation.utilities.UtilJava;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class SchemaDefinition {

    private static final Predicate<Property<?, ?>> COLUMN = property -> property.isPersistent() && !property.getColumn().equals("id") && !property.getColumn().equals("version") && !property.getColumn().equals("tenant");

    public static List<String> statements(final String table, final boolean tenanted, final List<Property<?, ?>> properties, final Map<Property<?, ?>, PostgresIndexType> indexes) {
        return UtilJava.createCollection(new ArrayList<>(), statements -> {
            statements.add(createTable(table, tenanted, properties));

            if (tenanted) {
                statements.add("ALTER TABLE %s ADD COLUMN IF NOT EXISTS tenant text NOT NULL DEFAULT ''".formatted(table));
            }

            properties.stream().filter(COLUMN).forEach(property -> statements.add(addColumn(table, property)));

            indexes.forEach((property, indexType) -> statements.add(createIndex(table, tenanted, property, indexType)));
        });
    }

    private static String createTable(final String table, final boolean tenanted, final List<Property<?, ?>> properties) {
        final String columns = properties.stream()
                .filter(COLUMN)
                .map(SchemaDefinition::column)
                .collect(Collectors.joining(", "));

        if (tenanted) {
            return "CREATE TABLE IF NOT EXISTS %s (id uuid NOT NULL, tenant text NOT NULL DEFAULT '', version bigint NOT NULL DEFAULT 0%s, PRIMARY KEY (tenant, id))".formatted(table, columns.isEmpty() ? "" : ", " + columns);
        }

        return "CREATE TABLE IF NOT EXISTS %s (id uuid PRIMARY KEY, version bigint NOT NULL DEFAULT 0%s)".formatted(table, columns.isEmpty() ? "" : ", " + columns);
    }

    private static String addColumn(final String table, final Property<?, ?> property) {
        return "ALTER TABLE %s ADD COLUMN IF NOT EXISTS %s".formatted(table, column(property));
    }

    private static String createIndex(final String table, final boolean tenanted, final Property<?, ?> property, final PostgresIndexType indexType) {
        final String unique = indexType == PostgresIndexType.UNIQUE ? "UNIQUE " : "";
        final String prefix = indexType == PostgresIndexType.UNIQUE ? "uq_" : "idx_";
        final String using = indexType == PostgresIndexType.BRIN ? " USING brin" : "";
        final String columns = tenanted ? "tenant, " + property.getColumn() : property.getColumn();

        return "CREATE %sINDEX IF NOT EXISTS %s%s_%s ON %s%s (%s)".formatted(unique, prefix, table, property.getColumn(), table, using, columns);
    }

    private static String column(final Property<?, ?> property) {
        return property.getColumn() + " " + ColumnTypes.get(property);
    }
}