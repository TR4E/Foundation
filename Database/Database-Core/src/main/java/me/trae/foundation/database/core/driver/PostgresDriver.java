package me.trae.foundation.database.core.driver;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.trae.foundation.database.api.exception.ConnectionException;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

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
            final HikariConfig hikariConfig = new HikariConfig();

            hikariConfig.setDriverClassName("org.postgresql.Driver");
            hikariConfig.setJdbcUrl(this.postgresSettings.getJdbcUrl());
            hikariConfig.setUsername(this.postgresSettings.getUsername());
            hikariConfig.setPassword(this.postgresSettings.getPassword());
            hikariConfig.setMaximumPoolSize(this.postgresSettings.getMaximumPoolSize());
            hikariConfig.setPoolName("foundation-database");

            this.dataSource = new HikariDataSource(hikariConfig);

            this.dslContext = DSL.using(this.dataSource, SQLDialect.POSTGRES);
        } catch (final RuntimeException exception) {
            this.disconnect();
            throw new ConnectionException("Failed to connect to PostgreSQL at %s".formatted(this.postgresSettings.getJdbcUrl()), exception);
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
}