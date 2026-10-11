package me.trae.foundation.database.core.repository;

import lombok.AccessLevel;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.exception.OptimisticLockException;
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
import me.trae.foundation.database.storage.codec.EntityCodec;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.redis.RedisNamespace;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final EntityCodec<E> entityCodec;

    protected AbstractEntityRepository(final CoreDatabase coreDatabase, final Class<E> entityType, final String table, final TenantScope tenantScope, final boolean optimisticLocking, final Class<?>... propertyHolders) {
        PropertyHolderLoader.load(propertyHolders);

        this.coreDatabase = Objects.requireNonNull(coreDatabase, "coreDatabase");
        this.entityType = entityType;
        this.tenantScope = tenantScope;
        this.properties = EntityPropertyRegistry.getProperties(entityType);
        this.entityCodec = new EntityCodec<>(entityType);

        if (this.properties.isEmpty()) {
            throw new SchemaException("No properties registered for %s, pass its property holder to the repository".formatted(entityType.getName()));
        }

        this.tableSchema = new TableSchema<>(entityType, table, tenantScope, this.properties, optimisticLocking);
        this.tenantId = coreDatabase.resolveTenantId(tenantScope).orElse(null);
        this.repositoryReader = new RepositoryReader<>(coreDatabase.getPostgresDriver(), this.tableSchema, this.tenantId, coreDatabase.getEntityRevisionStore());

        final RedisDriver redisDriver = coreDatabase.getRedisDriver().orElse(null);

        final RedisNamespace redisNamespace = redisDriver == null ? null : RedisNamespace.of(table, this.tenantId);

        this.repositoryWriter = new RepositoryWriter<>(coreDatabase.getBatchQueue().getPendingWriteStore(), this.tableSchema, this.tenantId, redisNamespace, redisDriver == null ? null : redisDriver.getInstanceId(), coreDatabase.getEntityRevisionStore());

        coreDatabase.register(this);
    }

    @Override
    public final String getTable() {
        return this.tableSchema.getTableName();
    }

    public final Optional<String> getTenantId() {
        return Optional.ofNullable(this.tenantId);
    }

    public final List<EntityProperty<? super E, ?>> getUniqueProperties() {
        return this.getIndexes().entrySet().stream().filter(entry -> entry.getValue() == IndexType.UNIQUE).<EntityProperty<? super E, ?>>map(Map.Entry::getKey).toList();
    }

    @Override
    public Optional<E> findById(final UUID id) {
        return this.repositoryReader.findById(id);
    }

    @Override
    public final List<E> findManyById(final Collection<UUID> ids) {
        return this.repositoryReader.findManyById(ids);
    }

    @Override
    public final Optional<E> findOne(final Query<E> query) {
        return this.repositoryReader.findOne(query);
    }

    @Override
    public final List<E> findMany(final Query<E> query) {
        return this.repositoryReader.findMany(query);
    }

    @Override
    public final long count(final Query<E> query) {
        return this.repositoryReader.count(query);
    }

    @Override
    public final boolean exists(final Query<E> query) {
        return this.repositoryReader.exists(query);
    }

    @Override
    public final EntityPage<E> findPage(final Query<E> query, final int page, final int size) {
        return this.repositoryReader.findPage(query, page, size);
    }

    public final void save(final E entity, final List<Runnable> commitCallbackList) {
        this.repositoryWriter.insert(entity, commitCallbackList, List.of(), false);
    }

    public final void save(final E entity, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList) {
        this.repositoryWriter.insert(entity, commitCallbackList, failureCallbackList, false);
    }

    public final void save(final E entity, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final boolean redisEnabled) {
        this.repositoryWriter.insert(entity, commitCallbackList, failureCallbackList, redisEnabled);
    }

    @Override
    public final void save(final E entity) {
        this.save(entity, Collections.emptyList());
    }

    public final void update(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties, final List<Runnable> commitCallbackList) {
        this.repositoryWriter.update(entity, entityProperties, commitCallbackList, List.of(), false);
    }

    public final void update(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList) {
        this.repositoryWriter.update(entity, entityProperties, commitCallbackList, failureCallbackList, false);
    }

    public final void update(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final boolean redisEnabled) {
        this.repositoryWriter.update(entity, entityProperties, commitCallbackList, failureCallbackList, redisEnabled);
    }

    @Override
    public final void update(final E entity) {
        final E baseline = this.repositoryReader.findById(entity.getId()).orElseThrow(() -> {
            if (this.tableSchema.isOptimisticLocking() && this.coreDatabase.getEntityRevisionStore().hasState(entity)) {
                return new OptimisticLockException("Cannot update deleted %s %s".formatted(this.entityType.getSimpleName(), entity.getId()));
            }

            return new DatabaseException("Cannot update missing %s %s".formatted(this.entityType.getSimpleName(), entity.getId()));
        });

        if (!this.coreDatabase.getEntityRevisionStore().hasState(entity)) {
            this.inheritRevision(entity, baseline);
        }

        final Map<String, String> baselineMap = this.entityCodec.encode(baseline);

        final Map<String, String> entityMap = this.entityCodec.encode(entity);

        final List<EntityProperty<?, ?>> changedList = this.properties.stream().filter(EntityProperty::isPersistent).filter(property -> !Objects.equals(baselineMap.get(property.getName()), entityMap.get(property.getName()))).toList();

        if (!changedList.isEmpty()) {
            this.repositoryWriter.update(entity, changedList, List.of(), List.of(), false);
        }
    }

    @Override
    public final void delete(final E entity) {
        this.repositoryWriter.delete(entity, false);
    }

    public final void delete(final E entity, final List<Runnable> commitCallbackList) {
        this.repositoryWriter.delete(entity, commitCallbackList, false);
    }

    public final void delete(final E entity, final List<Runnable> commitCallbackList, final boolean redisEnabled) {
        this.repositoryWriter.delete(entity, commitCallbackList, redisEnabled);
    }

    public final void inheritRevision(final E entity, final E baseline) {
        if (this.tableSchema.isOptimisticLocking()) {
            this.coreDatabase.getEntityRevisionStore().setRevision(entity, this.coreDatabase.getEntityRevisionStore().getState(baseline).getRevision());
        }
    }
}