package me.trae.foundation.database.core;

import me.trae.foundation.database.api.tenant.TenantScope;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.storage.driver.RedisDriver;

import java.util.Optional;
import java.util.UUID;

public final class WorkerRepository extends AbstractEntityRepository<WorkerEntity> {

    private final RedisDriver redisDriver;
    private final String countKey;

    public WorkerRepository(final CoreDatabase coreDatabase, final String table) {
        this(coreDatabase, table, null, null);
    }

    public WorkerRepository(final CoreDatabase coreDatabase, final String table, final RedisDriver redisDriver, final String countKey) {
        super(coreDatabase, WorkerEntity.class, table, TenantScope.NONE, false, MultiJvmDatabaseWorker.class);
        this.redisDriver = redisDriver;
        this.countKey = countKey;
    }

    @Override
    public Optional<WorkerEntity> findById(final UUID id) {
        if (this.countKey != null) {
            this.redisDriver.getCommands().incr(this.countKey);
            try {
                Thread.sleep(100L);
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }

        return super.findById(id);
    }
}