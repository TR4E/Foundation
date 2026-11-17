package me.trae.foundation.database.postgres.schema;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.postgres.driver.PostgresDriver;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@AllArgsConstructor
@Getter
public final class PostgresSynchronizer {

    private static final long LOCK_KEY = 7_041_975L;

    private final PostgresDriver driver;

    public void synchronize(final List<String> statements) {
        this.driver.getDslContext().connection(connection -> {
            try (final Statement statement = connection.createStatement()) {
                statement.execute("SELECT pg_advisory_lock(%s)".formatted(LOCK_KEY));

                try {
                    statements.forEach(sql -> this.execute(statement, sql));
                } finally {
                    statement.execute("SELECT pg_advisory_unlock(%s)".formatted(LOCK_KEY));
                }
            }
        });
    }

    private void execute(final Statement statement, final String sql) {
        try {
            statement.execute(sql);
        } catch (final SQLException exception) {
            throw new SchemaException("Failed to apply %s".formatted(sql), exception);
        }
    }
}