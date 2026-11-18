package me.trae.foundation.database.postgres.driver;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.trae.foundation.database.api.exception.ConnectionException;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@CustomLog
@RequiredArgsConstructor
public final class PostgresDriver {

    private static final int CREATION_ATTEMPTS = 10;
    private static final long CREATION_RETRY_DELAY = 250L;

    static {
        System.setProperty("org.jooq.no-logo", "true");
        System.setProperty("org.jooq.no-tips", "true");
    }

    @Getter
    private final PostgresSettings postgresSettings;

    private volatile HikariDataSource dataSource;
    private volatile DSLContext dslContext;

    public synchronized void connect() {
        if (this.isConnected()) {
            return;
        }

        try {
            this.connectToConfiguredDatabase();
        } catch (final RuntimeException exception) {
            if (!this.isDatabaseMissing(exception)) {
                this.disconnect();
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("Failed to connect to PostgreSQL at {}", this.postgresSettings.getJdbcUrl(), exception);
                }
                throw new ConnectionException("Failed to connect to PostgreSQL at %s".formatted(this.postgresSettings.getJdbcUrl()), exception);
            }

            try {
                this.createDatabaseIfMissing();
                this.connectToConfiguredDatabase();
            } catch (final RuntimeException creationException) {
                this.disconnect();
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("Failed to create or connect to PostgreSQL at {}", this.postgresSettings.getJdbcUrl(), creationException);
                }
                throw new ConnectionException("Failed to create or connect to PostgreSQL database %s. The configured PostgreSQL user must have CREATEDB permission when the database does not exist".formatted(this.postgresSettings.getDatabase()), creationException);
            }
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Connected to PostgreSQL at {}", this.postgresSettings.getJdbcUrl());
        }
    }

    public synchronized void disconnect() {
        this.dslContext = null;

        if (this.dataSource != null) {
            this.dataSource.close();
            this.dataSource = null;
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Disconnected from PostgreSQL at {}", this.postgresSettings.getJdbcUrl());
            }
        }
    }

    public boolean isConnected() {
        final HikariDataSource current = this.dataSource;

        return current != null && !current.isClosed();
    }

    public DSLContext getDslContext() {
        final DSLContext current = this.dslContext;

        if (current == null) {
            throw new ConnectionException("PostgreSQL is not connected");
        }

        return current;
    }

    private void connectToConfiguredDatabase() {
        final HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setDriverClassName("org.postgresql.Driver");
        hikariConfig.setJdbcUrl(this.postgresSettings.getJdbcUrl());
        hikariConfig.setUsername(this.postgresSettings.getUsername());
        hikariConfig.setPassword(this.postgresSettings.getPassword());
        hikariConfig.setMaximumPoolSize(this.postgresSettings.getMaximumPoolSize());
        hikariConfig.setPoolName("foundation-database");

        this.dataSource = new HikariDataSource(hikariConfig);

        this.dslContext = DSL.using(this.dataSource, SQLDialect.POSTGRES);
    }

    private void createDatabaseIfMissing() {
        final String jdbcUrl = "jdbc:postgresql://%s:%s/postgres".formatted(this.postgresSettings.getHost(), this.postgresSettings.getPort());

        for (int attempt = 0; attempt < CREATION_ATTEMPTS; attempt++) {
            try (final Connection connection = DriverManager.getConnection(jdbcUrl, this.postgresSettings.getUsername(), this.postgresSettings.getPassword())) {
                if (!this.databaseExists(connection)) {
                    this.createDatabase(connection);
                }

                return;
            } catch (final SQLException exception) {
                if (!this.isRetryable(exception)) {
                    throw new ConnectionException("Failed to create PostgreSQL database %s using the postgres maintenance database".formatted(this.postgresSettings.getDatabase()), exception);
                }

                this.sleep();
            }
        }

        throw new ConnectionException("Failed to create PostgreSQL database %s after %s attempts, another instance is still copying the template".formatted(this.postgresSettings.getDatabase(), CREATION_ATTEMPTS));
    }

    private boolean databaseExists(final Connection connection) throws SQLException {
        try (final PreparedStatement preparedStatement = connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            preparedStatement.setString(1, this.postgresSettings.getDatabase());

            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void createDatabase(final Connection connection) throws SQLException {
        try (final Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE %s".formatted(this.quoteIdentifier(this.postgresSettings.getDatabase())));
        } catch (final SQLException exception) {
            if (!"42P04".equals(exception.getSQLState())) {
                throw exception;
            }
        }
    }

    private boolean isRetryable(final SQLException exception) {
        if ("55006".equals(exception.getSQLState())) {
            return true;
        }

        if ("23505".equals(exception.getSQLState())) {
            return true;
        }

        return false;
    }

    private void sleep() {
        try {
            Thread.sleep(CREATION_RETRY_DELAY);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ConnectionException("Interrupted while waiting to create PostgreSQL database %s".formatted(this.postgresSettings.getDatabase()), exception);
        }
    }

    private boolean isDatabaseMissing(final Throwable throwable) {
        Throwable cause = throwable;

        while (cause != null) {
            if (cause instanceof final SQLException sqlException && "3D000".equals(sqlException.getSQLState())) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

    private String quoteIdentifier(final String identifier) {
        return "\"%s\"".formatted(identifier.replace("\"", "\"\""));
    }
}