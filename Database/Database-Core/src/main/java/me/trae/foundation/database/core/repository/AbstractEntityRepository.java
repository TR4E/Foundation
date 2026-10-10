package me.trae.foundation.database.core.repository;

import lombok.AccessLevel;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.property.EntityPropertyRegistry;
import me.trae.foundation.database.api.query.EntityPage;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.repository.EntityRepository;
import me.trae.foundation.database.api.repository.index.IndexType;
import me.trae.foundation.database.api.tenant.TenantScope;
import me.trae.foundation.database.core.CoreDatabase;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.redis.RedisNamespace;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Getter
public abstract class AbstractEntityRepository<E extends Entity> implements EntityRepository<E> {

    private final CoreDatabase coreDatabase;
    private final Class<E> entityType;
    private final TenantScope tenantScope;
    private final List<EntityProperty<?, ?>> properties;
    private final TableSchema<E> tableSchema;

    @Getter(AccessLevel.NONE)
    private final String tenantId;

    private final RepositoryReader<E> repositoryReader;
    private final RepositoryWriter<E> repositoryWriter;

    protected AbstractEntityRepository(final CoreDatabase coreDatabase, final Class<E> entityType, final String table, final TenantScope tenantScope, final Class<?>... propertyHolders) {
        this(coreDatabase, entityType, table, tenantScope, false, propertyHolders);
    }

    protected AbstractEntityRepository(final CoreDatabase coreDatabase, final Class<E> entityType, final String table, final TenantScope tenantScope, final boolean optimisticLocking, final Class<?>... propertyHolders) {
        PropertyHolderLoader.load(propertyHolders);

        this.coreDatabase = coreDatabase;
        this.entityType = entityType;
        this.tenantScope = tenantScope;
        this.properties = EntityPropertyRegistry.getProperties(entityType);

        if (this.properties.isEmpty()) {
            throw new SchemaException("No properties registered for %s, pass its property holder to the repository".formatted(entityType.getName()));
        }

        this.tableSchema = new TableSchema<>(entityType, table, tenantScope, this.properties, optimisticLocking);
        this.tenantId = coreDatabase.resolveTenantId(tenantScope).orElse(null);
        this.repositoryReader = new RepositoryReader<>(coreDatabase.getPostgresDriver(), this.tableSchema, this.tenantId);

        final RedisDriver redisDriver = coreDatabase.getRedisDriver().orElse(null);
        final RedisNamespace redisNamespace = redisDriver == null ? null : RedisNamespace.of(table, this.tenantId);

        this.repositoryWriter = new RepositoryWriter<>(coreDatabase.getBatchQueue().getPendingWriteStore(), this.tableSchema, this.tenantId, redisNamespace, redisDriver == null ? null : redisDriver.getInstanceId());

        coreDatabase.register(this);
    }

    @Override
    public String getTable() {
        return this.tableSchema.getTableName();
    }

    public Optional<String> getTenantId() {
        return Optional.ofNullable(this.tenantId);
    }

    public List<EntityProperty<? super E, ?>> getUniqueProperties() {
        return this.getIndexes().entrySet().stream()
                .filter(entry -> entry.getValue() == IndexType.UNIQUE)
                .<EntityProperty<? super E, ?>>map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public Optional<E> findById(final UUID id) {
        return this.repositoryReader.findById(id);
    }

    @Override
    public List<E> findManyById(final Collection<UUID> ids) {
        return this.repositoryReader.findManyById(ids);
    }

    @Override
    public Optional<E> findOne(final Query<E> query) {
        return this.repositoryReader.findOne(query);
    }

    @Override
    public List<E> findMany(final Query<E> query) {
        return this.repositoryReader.findMany(query);
    }

    @Override
    public long count(final Query<E> query) {
        return this.repositoryReader.count(query);
    }

    @Override
    public boolean exists(final Query<E> query) {
        return this.repositoryReader.exists(query);
    }

    @Override
    public EntityPage<E> findPage(final Query<E> query, final int page, final int size) {
        return this.repositoryReader.findPage(query, page, size);
    }

    public void save(final E entity, final List<Runnable> commitCallbackList) {
        this.repositoryWriter.update(entity, this.tableSchema.getPersistentProperties(), commitCallbackList);
    }

    @Override
    public void save(final E entity) {
        this.save(entity, Collections.emptyList());
    }

    public void update(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties, final List<Runnable> commitCallbackList) {
        this.repositoryWriter.update(entity, entityProperties, commitCallbackList);
    }

    @Override
    public void delete(final E entity) {
        this.repositoryWriter.delete(entity);
    }

    public void delete(final E entity, final List<Runnable> commitCallbackList) {
        this.repositoryWriter.delete(entity, commitCallbackList);
    }
}