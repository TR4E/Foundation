package me.trae.foundation.database.storage.driver;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

@AllArgsConstructor
@Getter
public class RedisSettings {

    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final Duration timeout;
}