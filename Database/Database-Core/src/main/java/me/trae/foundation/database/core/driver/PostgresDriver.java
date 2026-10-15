package me.trae.foundation.database.core.driver;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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

@RequiredArgsConstructor
public final class PostgresDriver {

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
                throw new ConnectionException("Failed to connect to PostgreSQL at %s".formatted(this.postgresSettings.getJdbcUrl()), exception);
            }

            try {
                this.createDatabaseIfMissing();
                this.connectToConfiguredDatabase();
            } catch (final RuntimeException creationException) {
                this.disconnect();
                throw new ConnectionException("Failed to create or connect to PostgreSQL database %s. The configured PostgreSQL user must have CREATEDB permission when the database does not exist".formatted(this.postgresSettings.getDatabase()), creationException);
            }
        }
    }

    public synchronized void disconnect() {
        this.dslContext = null;

        if (this.dataSource != null) {
            this.dataSource.close();
            this.dataSource = null;
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

        try (final Connection connection = DriverManager.getConnection(jdbcUrl, this.postgresSettings.getUsername(), this.postgresSettings.getPassword());
             final PreparedStatement preparedStatement = connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            preparedStatement.setString(1, this.postgresSettings.getDatabase());

            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return;
                }
            }

            try (final Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE %s".formatted(this.quoteIdentifier(this.postgresSettings.getDatabase())));
            } catch (final SQLException exception) {
                if (!"42P04".equals(exception.getSQLState())) {
                    throw exception;
                }
            }
        } catch (final SQLException exception) {
            throw new ConnectionException("Failed to create PostgreSQL database %s using the postgres maintenance database".formatted(this.postgresSettings.getDatabase()), exception);
        }
    }

    private boolean isDatabaseMissing(final Throwable throwable) {
        Throwable cause = throwable;

        while (cause != null) {
            if (cause instanceof SQLException sqlException && "3D000".equals(sqlException.getSQLState())) {
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