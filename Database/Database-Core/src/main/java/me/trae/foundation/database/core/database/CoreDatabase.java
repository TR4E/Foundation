package me.trae.foundation.database.core.database;

import lombok.CustomLog;
import lombok.Getter;
import me.trae.foundation.database.api.Database;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.holder.Holder;
import me.trae.foundation.database.api.marker.SharedMarker;
import me.trae.foundation.database.api.repository.Repository;
import me.trae.foundation.database.api.storage.StorageProvider;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.api.write.WriteGate;
import me.trae.foundation.database.batch.BatchQueue;
import me.trae.foundation.database.batch.BatchScheduler;
import me.trae.foundation.database.core.context.ContextFactory;
import me.trae.foundation.database.core.namespace.NamespaceFactory;
import me.trae.foundation.database.core.registry.EntityRegistry;
import me.trae.foundation.database.core.settings.DatabaseSettings;
import me.trae.foundation.database.holder.entity.EntityHolder;
import me.trae.foundation.database.local.LocalMarker;
import me.trae.foundation.database.local.LocalStorageProvider;
import me.trae.foundation.database.postgres.driver.PostgresDriver;
import me.trae.foundation.database.postgres.repository.PostgresRepository;
import me.trae.foundation.database.postgres.schema.PostgresSynchronizer;
import me.trae.foundation.database.redis.driver.RedisDriver;
import me.trae.foundation.database.redis.marker.RedisMarker;
import me.trae.foundation.database.redis.storage.RedisStorageProvider;
import me.trae.foundation.database.tenant.Tenant;
import me.trae.foundation.database.tenant.TenantScope;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@CustomLog
@Getter
public final class CoreDatabase implements Database {

    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private final EntityRegistry registry = new EntityRegistry();
    private final List<StorageProvider> providers = new ArrayList<>();
    private final WriteGate writeGate = new WriteGate();
    private final AtomicBoolean stopped = new AtomicBoolean();

    private final DatabaseSettings settings;
    private final PostgresDriver postgresDriver;
    private final RedisDriver redisDriver;
    private final TenantScope tenantScope;
    private final BatchQueue batchQueue;
    private final BatchScheduler batchScheduler;
    private final ContextFactory contextFactory;

    public CoreDatabase(final DatabaseSettings settings, final PostgresDriver postgresDriver, final RedisDriver redisDriver) {
        this.settings = settings;
        this.postgresDriver = postgresDriver;
        this.redisDriver = redisDriver;
        this.tenantScope = new TenantScope(settings.getTenant() == null || settings.getTenant().isBlank() ? null : Tenant.of(settings.getTenant()));

        if (settings.getTiers().contains(Tier.LOCAL)) {
            this.providers.add(new LocalStorageProvider());
        }

        if (settings.getTiers().contains(Tier.REDIS) && redisDriver != null) {
            this.providers.add(new RedisStorageProvider(redisDriver));
        }

        final SharedMarker marker = redisDriver == null ? new LocalMarker() : new RedisMarker(redisDriver);

        this.batchQueue = new BatchQueue(settings.getBatchLimit());
        this.batchScheduler = new BatchScheduler(this.batchQueue, this.registry::apply, settings.getBatchInterval());
        this.contextFactory = new ContextFactory(settings, this.tenantScope, new NamespaceFactory(settings, this.tenantScope), this.providers, marker, this.batchQueue, this.writeGate, redisDriver, this.registry);
    }

    @Override
    public void start() {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Starting database with tiers {} and tenant {}", this.settings.getTiers(), this.settings.getTenant());
        }

        this.postgresDriver.connect();

        if (this.redisDriver != null) {
            this.redisDriver.connect();
        }

        final List<String> statements = this.statements();

        if (statements.isEmpty() && LOGGER.isWarnEnabled()) {
            LOGGER.warn("No repositories were registered before start, any holder created after this point gets no table or indexes");
        }

        new PostgresSynchronizer(this.postgresDriver).synchronize(statements);

        this.batchScheduler.start();

        this.registry.getHolders().forEach(holder -> holder.getRefresher().start());
    }

    @Override
    public void stop() {
        if (!this.stopped.compareAndSet(false, true)) {
            return;
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Stopping database with tiers {} and tenant {}, closing {} holders", this.settings.getTiers(), this.settings.getTenant(), this.registry.getHolders().size());
        }

        if (!this.writeGate.drain(DRAIN_TIMEOUT) && LOGGER.isWarnEnabled()) {
            LOGGER.warn("Writes were still in flight after {} seconds, continuing with shutdown", DRAIN_TIMEOUT.toSeconds());
        }

        this.registry.getHolders().forEach(EntityHolder::close);

        this.batchScheduler.stop();

        if (this.redisDriver != null) {
            this.redisDriver.disconnect();
        }

        this.postgresDriver.disconnect();
    }

    @Override
    public boolean isReady() {
        if (!this.postgresDriver.isConnected()) {
            return false;
        }

        if (this.redisDriver != null && !this.redisDriver.isConnected()) {
            return false;
        }

        return true;
    }

    @Override
    public <EntityType extends Entity> Holder<EntityType> getHolder(final Class<EntityType> entityType) {
        return this.registry.getHolder(entityType);
    }

    @Override
    public <EntityType extends Entity> Repository<EntityType> getRepository(final Class<EntityType> entityType) {
        return this.registry.getRepository(entityType);
    }

    private List<String> statements() {
        final List<String> statements = new ArrayList<>();

        this.registry.getRepositoryMap().values().forEach(repository -> statements(statements, repository));

        return statements;
    }

    private static void statements(final List<String> statements, final Repository<?> repository) {
        if (repository instanceof final PostgresRepository<?> postgresRepository) {
            statements.addAll(postgresRepository.getStatements());
        }
    }
}