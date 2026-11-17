package me.trae.foundation.database.postgres.schema;

import lombok.AllArgsConstructor;
import lombok.CustomLog;
import lombok.Getter;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.postgres.driver.PostgresDriver;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@CustomLog
@AllArgsConstructor
@Getter
public final class PostgresSynchronizer {

    private static final long LOCK_KEY = 7_041_975L;

    private final PostgresDriver driver;

    public void synchronize(final List<String> statements) {
        final long start = System.nanoTime();

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

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Applied {} schema statements in {}ms", statements.size(), (System.nanoTime() - start) / 1_000_000L);
        }
    }

    private void execute(final Statement statement, final String sql) {
        try {
            statement.execute(sql);
        } catch (final SQLException exception) {
            throw new SchemaException("Failed to apply %s".formatted(sql), exception);
        }
    }
}