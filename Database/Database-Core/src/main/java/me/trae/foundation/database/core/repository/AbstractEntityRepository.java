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
import me.trae.foundation.database.api.repository.index.EntityIndex;
import me.trae.foundation.database.api.repository.index.IndexType;
import me.trae.foundation.database.api.tenant.TenantScope;
import me.trae.foundation.database.core.CoreDatabase;
import me.trae.foundation.database.core.batch.CacheInvalidationOutbox;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.core.value.ColumnValueMapper;
import me.trae.foundation.database.storage.codec.EntityCodec;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.redis.RedisInvalidation;
import me.trae.foundation.database.storage.redis.RedisNamespace;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;

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

    public final List<EntityIndex<E>> getEntityIndexes() {
        final List<EntityIndex<E>> entityIndexList = new java.util.ArrayList<>();

        for (final EntityProperty<? super E, ?> entityProperty : this.getUniqueProperties()) {
            entityIndexList.add(EntityIndex.of(entityProperty.getName(), entityProperty));
        }

        entityIndexList.addAll(this.getCompositeIndexes());

        final java.util.Set<String> nameSet = new java.util.HashSet<>();

        for (final EntityIndex<E> entityIndex : entityIndexList) {
            if (!nameSet.add(entityIndex.getName())) {
                throw new SchemaException("Duplicate entity index name %s on %s".formatted(entityIndex.getName(), this.entityType.getName()));
            }

            for (final EntityProperty<? super E, ?> entityProperty : entityIndex.getProperties()) {
                if (!entityProperty.getEntityType().isAssignableFrom(this.entityType)) {
                    throw new SchemaException("Entity index %s contains a property from another entity type".formatted(entityIndex.getName()));
                }

                if (!entityProperty.isPersistent()) {
                    throw new SchemaException("Entity index %s contains nonpersistent property %s".formatted(entityIndex.getName(), entityProperty.getName()));
                }
            }
        }

        return List.copyOf(entityIndexList);
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

    public final <Value> void transferUniqueProperty(final E claimant, final E currentOwner, final EntityProperty<? super E, Value> entityProperty, final Value claimedValue, final Value replacementValue, final boolean redisEnabled) {
        if (claimant.getId().equals(currentOwner.getId())) {
            throw new IllegalArgumentException("Claimant and current owner must be different entities");
        }

        if (claimedValue == null) {
            throw new IllegalArgumentException("claimedValue must not be null");
        }

        final EntityProperty<? super E, ?> registeredProperty = this.getUniqueProperties().stream()
                .filter(property -> property.getName().equals(entityProperty.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Property %s is not registered as unique for %s".formatted(entityProperty.getName(), this.entityType.getSimpleName())));

        if (!registeredProperty.equals(entityProperty) || !entityProperty.isPersistent()) {
            throw new IllegalArgumentException("Property %s is not a persistent unique property for %s".formatted(entityProperty.getName(), this.entityType.getSimpleName()));
        }

        if (replacementValue != null && Objects.equals(entityProperty.normalizeIndexValue(claimedValue), entityProperty.normalizeIndexValue(replacementValue))) {
            throw new IllegalArgumentException("replacementValue must differ from claimedValue");
        }

        final Field<?> propertyField = this.tableSchema.getField(entityProperty);

        final RedisInvalidation claimantInvalidation = this.repositoryWriter.createInvalidation(claimant.getId(), redisEnabled);
        final RedisInvalidation ownerInvalidation = this.repositoryWriter.createInvalidation(currentOwner.getId(), redisEnabled);
        final Long claimantRevision = this.getExpectedRevision(claimant);
        final Long ownerRevision = this.getExpectedRevision(currentOwner);
        final long[] nextRevision = new long[2];

        this.coreDatabase.getBatchQueue().executeAtomically(dslContext -> {
            final Condition condition = TableSchema.ID_FIELD.in(claimant.getId(), currentOwner.getId());
            final Condition tenantCondition = this.tableSchema.isTenantScoped() ? condition.and(TableSchema.TENANT_FIELD.eq(this.tenantId)) : condition;
            final org.jooq.Result<? extends Record> recordList;

            if (this.tableSchema.isOptimisticLocking()) {
                recordList = dslContext.select(TableSchema.ID_FIELD, propertyField, TableSchema.REVISION_FIELD)
                        .from(this.tableSchema.getTable())
                        .where(tenantCondition)
                        .orderBy(TableSchema.ID_FIELD)
                        .forUpdate()
                        .fetch();
            } else {
                recordList = dslContext.select(TableSchema.ID_FIELD, propertyField)
                        .from(this.tableSchema.getTable())
                        .where(tenantCondition)
                        .orderBy(TableSchema.ID_FIELD)
                        .forUpdate()
                        .fetch();
            }
            final Record claimantRecord = recordList.stream()
                    .filter(record -> claimant.getId().equals(record.get(TableSchema.ID_FIELD)))
                    .findFirst()
                    .orElseThrow(() -> new DatabaseException("Cannot find claimant %s %s".formatted(this.entityType.getSimpleName(), claimant.getId())));
            final Record ownerRecord = recordList.stream()
                    .filter(record -> currentOwner.getId().equals(record.get(TableSchema.ID_FIELD)))
                    .findFirst()
                    .orElseThrow(() -> new DatabaseException("Cannot find current owner %s %s".formatted(this.entityType.getSimpleName(), currentOwner.getId())));
            final Value ownerValue = ColumnValueMapper.fromStored(entityProperty, ownerRecord.get(propertyField));

            if (!Objects.equals(entityProperty.normalizeIndexValue(ownerValue), entityProperty.normalizeIndexValue(claimedValue))) {
                throw new DatabaseException("Entity %s no longer owns %s %s".formatted(currentOwner.getId(), entityProperty.getName(), claimedValue));
            }

            final long claimantStoredRevision = this.tableSchema.isOptimisticLocking() ? claimantRecord.get(TableSchema.REVISION_FIELD) : 0L;
            final long ownerStoredRevision = this.tableSchema.isOptimisticLocking() ? ownerRecord.get(TableSchema.REVISION_FIELD) : 0L;

            if ((claimantRevision != null && claimantRevision != claimantStoredRevision) || (ownerRevision != null && ownerRevision != ownerStoredRevision)) {
                throw new OptimisticLockException("Cannot transfer %s because an entity revision is stale".formatted(entityProperty.getName()));
            }

            if (this.tableSchema.isOptimisticLocking() && (claimantStoredRevision < 0L || ownerStoredRevision < 0L || claimantStoredRevision == Long.MAX_VALUE || ownerStoredRevision == Long.MAX_VALUE)) {
                throw new DatabaseException("Cannot transfer %s because an entity revision cannot be advanced".formatted(entityProperty.getName()));
            }

            nextRevision[0] = claimantStoredRevision + 1L;
            nextRevision[1] = ownerStoredRevision + 1L;

            this.updateUniqueProperty(dslContext, currentOwner, entityProperty, replacementValue, ownerStoredRevision);
            this.updateUniqueProperty(dslContext, claimant, entityProperty, claimedValue, claimantStoredRevision);
            CacheInvalidationOutbox.insertInvalidations(dslContext, List.of(claimantInvalidation, ownerInvalidation));
        });

        entityProperty.setValue(currentOwner, replacementValue);
        entityProperty.setValue(claimant, claimedValue);

        if (this.tableSchema.isOptimisticLocking()) {
            this.coreDatabase.getEntityRevisionStore().setRevision(claimant, nextRevision[0]);
            this.coreDatabase.getEntityRevisionStore().setRevision(currentOwner, nextRevision[1]);
        }
    }

    private <Value> void updateUniqueProperty(final DSLContext dslContext, final E entity, final EntityProperty<? super E, Value> entityProperty, final Value value, final long expectedRevision) {
        final Field<Object> field = this.tableSchema.getField(entityProperty).coerce(Object.class);
        final org.jooq.UpdateSetMoreStep<Record> updateStep = dslContext.update(this.tableSchema.getTable()).set(field, ColumnValueMapper.toStoredObject(entityProperty, value));

        if (!this.tableSchema.isOptimisticLocking()) {
            if (updateStep.where(this.tableSchema.getKeyCondition(this.tenantId, entity.getId())).execute() != 1) {
                throw new DatabaseException("Cannot update missing %s %s".formatted(this.entityType.getSimpleName(), entity.getId()));
            }

            return;
        }

        final long nextRevision = expectedRevision + 1L;
        final Condition condition = this.tableSchema.getKeyCondition(this.tenantId, entity.getId()).and(TableSchema.REVISION_FIELD.eq(expectedRevision));

        if (updateStep.set(TableSchema.REVISION_FIELD, nextRevision).where(condition).execute() != 1) {
            throw new OptimisticLockException("Cannot transfer unique property for stale entity %s".formatted(entity.getId()));
        }
    }

    private Long getExpectedRevision(final E entity) {
        return !this.tableSchema.isOptimisticLocking() || !this.coreDatabase.getEntityRevisionStore().hasState(entity) ? null : this.coreDatabase.getEntityRevisionStore().getState(entity).getRevision();
    }
}