package me.trae.foundation.database.storage.driver;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

@AllArgsConstructor
@Getter
public final class RedisSettings {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final int database;
    private final Duration timeout;

    public RedisSettings(final String host, final int port, final String password, final int database, final Duration timeout) {
        this(host, port, null, password, database, timeout);
    }
}