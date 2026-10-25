package me.trae.foundation.database.core;

import me.trae.foundation.database.api.exception.ConnectionException;
import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.core.driver.PostgresSettings;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class PostgresDriverTest {

    @Test
    void createsDatabaseWhenMissing() {
        DatabaseTestEnvironment.requirePostgres();

        final PostgresSettings settings = new PostgresSettings(System.getProperty("postgres.host", "localhost"), Integer.getInteger("postgres.port", 5432), System.getProperty("postgres.database", "foundation_test"), System.getProperty("postgres.username", "postgres"), System.getProperty("postgres.password", "postgres"), 2);
        final PostgresDriver existingDatabaseDriver = new PostgresDriver(settings);

        try {
            existingDatabaseDriver.connect();
        } catch (final ConnectionException exception) {
            Assumptions.abort("PostgreSQL is not reachable: %s".formatted(exception.getMessage()));
        }

        final String databaseName = "foundation_create_%s".formatted(UUID.randomUUID().toString().replace("-", ""));
        final PostgresDriver newDatabaseDriver = new PostgresDriver(new PostgresSettings(settings.getHost(), settings.getPort(), databaseName, settings.getUsername(), settings.getPassword(), 2));

        try {
            newDatabaseDriver.connect();
            assertTrue(newDatabaseDriver.isConnected());
        } finally {
            newDatabaseDriver.disconnect();
            existingDatabaseDriver.getDslContext().execute("DROP DATABASE IF EXISTS %s".formatted(databaseName));
            existingDatabaseDriver.disconnect();
        }
    }
}
