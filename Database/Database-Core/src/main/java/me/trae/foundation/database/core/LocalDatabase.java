package me.trae.foundation.database.core;

import me.trae.foundation.database.api.tenant.Tenant;
import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.storage.driver.RedisDriver;

import java.time.Duration;

public final class LocalDatabase extends CoreDatabase {

    public LocalDatabase(final PostgresDriver postgresDriver, final Tenant tenant) {
        super(postgresDriver, null, tenant);
    }

    public LocalDatabase(final PostgresDriver postgresDriver, final RedisDriver redisDriver, final Tenant tenant) {
        super(postgresDriver, redisDriver, tenant);
    }

    public LocalDatabase(final PostgresDriver postgresDriver, final RedisDriver redisDriver, final Tenant tenant, final Duration flushInterval, final int chunkSize, final int maximumPendingWrites) {
        super(postgresDriver, redisDriver, tenant, flushInterval, chunkSize, maximumPendingWrites);
    }
}