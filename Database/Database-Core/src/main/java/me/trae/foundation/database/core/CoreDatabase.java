package me.trae.foundation.database.core;

import lombok.Getter;
import me.trae.foundation.database.api.Database;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.repository.EntityRepository;
import me.trae.foundation.database.api.tenant.Tenant;
import me.trae.foundation.database.api.tenant.TenantScope;
import me.trae.foundation.database.core.batch.BatchQueue;
import me.trae.foundation.database.core.batch.PendingWriteStore;
import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.core.registry.RepositoryRegistry;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.storage.driver.RedisDriver;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class CoreDatabase implements Database {

    @Getter
    private final PostgresDriver postgresDriver;

    @Getter
    private final BatchQueue batchQueue;

    private final RedisDriver redisDriver;
    private final Tenant tenant;
    private final RepositoryRegistry repositoryRegistry;

    private volatile boolean started;

    public CoreDatabase(final PostgresDriver postgresDriver, final RedisDriver redisDriver, final Tenant tenant, final Duration flushInterval, final int chunkSize) {
        this(postgresDriver, redisDriver, tenant, flushInterval, chunkSize, PendingWriteStore.DEFAULT_MAX_PENDING_WRITES);
    }

    public CoreDatabase(final PostgresDriver postgresDriver, final RedisDriver redisDriver, final Tenant tenant, final Duration flushInterval, final int chunkSize, final int maximumPendingWrites) {
        this.postgresDriver = postgresDriver;
        this.redisDriver = redisDriver;
        this.tenant = tenant;
        this.batchQueue = new BatchQueue(postgresDriver, redisDriver, flushInterval, chunkSize, maximumPendingWrites);
        this.repositoryRegistry = new RepositoryRegistry(postgresDriver);
    }

    public CoreDatabase(final PostgresDriver postgresDriver, final RedisDriver redisDriver, final Tenant tenant) {
        this(postgresDriver, redisDriver, tenant, Duration.ofMillis(250), 500);
    }

    public CoreDatabase(final PostgresDriver postgresDriver) {
        this(postgresDriver, null, null);
    }

    public synchronized void start() {
        if (this.started) {
            return;
        }

        this.repositoryRegistry.synchronizeAll();
        this.batchQueue.synchronizeOutbox();

        this.batchQueue.start();

        this.started = true;
    }

    public synchronized void stop() {
        if (!this.started) {
            return;
        }

        this.started = false;
        this.batchQueue.stop();
    }

    public synchronized void register(final AbstractEntityRepository<?> repository) {
        this.repositoryRegistry.register(repository, this.started);
    }

    public Optional<String> resolveTenantId(final TenantScope tenantScope) {
        if (tenantScope == TenantScope.NONE) {
            return Optional.empty();
        }

        if (this.tenant == null) {
            throw new SchemaException("Tenant scope %s requires the database to be created with a Tenant".formatted(tenantScope));
        }

        return this.tenant.resolve(tenantScope);
    }

    public Optional<RedisDriver> getRedisDriver() {
        return Optional.ofNullable(this.redisDriver);
    }

    public Optional<Tenant> getTenant() {
        return Optional.ofNullable(this.tenant);
    }

    public void setFailureHandler(final Consumer<DatabaseException> failureHandler) {
        this.batchQueue.setFailureHandler(failureHandler);
    }

    @Override
    public boolean isReady() {
        return this.started && this.postgresDriver.isConnected();
    }

    @Override
    public List<EntityRepository<?>> getRepositories() {
        return this.repositoryRegistry.getRepositories();
    }

    @Override
    public <E extends Entity> Optional<EntityRepository<E>> getRepository(final Class<E> entityType) {
        return this.repositoryRegistry.getRepository(entityType);
    }
}