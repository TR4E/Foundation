package me.trae.foundation.database.core.driver;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public final class PostgresSettings {

    private final String host;
    private final int port;
    private final String database, username, password;
    private final int maximumPoolSize;

    public String getJdbcUrl() {
        return "jdbc:postgresql://%s:%s/%s".formatted(this.host, this.port, this.database);
    }
}