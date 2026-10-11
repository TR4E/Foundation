package me.trae.foundation.database.core.batch;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.core.driver.PostgresSettings;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.driver.RedisSettings;

import java.time.Duration;

@UtilityClass
public class OutboxDispatchWorker {

    public static void main(final String[] argumentList) {
        final PostgresDriver postgresDriver = new PostgresDriver(new PostgresSettings(argumentList[0], Integer.parseInt(argumentList[1]), argumentList[2], argumentList[3], argumentList[4], 2));
        final RedisDriver redisDriver = new RedisDriver(new RedisSettings(argumentList[5], Integer.parseInt(argumentList[6]), argumentList[7].isEmpty() ? null : argumentList[7], Integer.parseInt(argumentList[8]), Duration.ofSeconds(2)));

        postgresDriver.connect();
        redisDriver.connect();

        try {
            new CacheInvalidationOutbox(postgresDriver, redisDriver).dispatch();
        } finally {
            redisDriver.disconnect();
            postgresDriver.disconnect();
        }
    }
}
