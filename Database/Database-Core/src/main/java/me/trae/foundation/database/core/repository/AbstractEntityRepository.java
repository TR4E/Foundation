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
import org.jooq.Result;
import org.jooq.UpdateSetMoreStep;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
        final List<EntityIndex<E>> entityIndexList = new ArrayList<>();

        for (final EntityProperty<? super E, ?> entityProperty : this.getUniqueProperties()) {
            entityIndexList.add(EntityIndex.of(entityProperty.getName(), entityProperty));
        }

        entityIndexList.addAll(this.getCompositeIndexes());

        final Set<String> nameSet = new HashSet<>();

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

    public final void save(final E entity, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final boolean redisEnabled, final String sourceId) {
        this.repositoryWriter.insert(entity, commitCallbackList, failureCallbackList, redisEnabled, sourceId);
    }

    public final void save(final E entity, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final boolean redisEnabled) {
        this.repositoryWriter.insert(entity, commitCallbackList, failureCallbackList, redisEnabled);
    }

    public final void save(final E entity, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList) {
        this.repositoryWriter.insert(entity, commitCallbackList, failureCallbackList, false);
    }

    public final void save(final E entity, final List<Runnable> commitCallbackList) {
        this.repositoryWriter.insert(entity, commitCallbackList, List.of(), false);
    }

    @Override
    public final void save(final E entity) {
        this.save(entity, Collections.emptyList());
    }

    public final void update(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final boolean redisEnabled, final String sourceId) {
        this.repositoryWriter.update(entity, entityProperties, commitCallbackList, failureCallbackList, redisEnabled, sourceId);
    }

    public final void update(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final boolean redisEnabled) {
        this.repositoryWriter.update(entity, entityProperties, commitCallbackList, failureCallbackList, redisEnabled);
    }

    public final void update(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList) {
        this.repositoryWriter.update(entity, entityProperties, commitCallbackList, failureCallbackList, false);
    }

    public final void update(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties, final List<Runnable> commitCallbackList) {
        this.repositoryWriter.update(entity, entityProperties, commitCallbackList, List.of(), false);
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

    public final void delete(final E entity, final List<Runnable> commitCallbackList, final List<Runnable> failureCallbackList, final boolean redisEnabled, final String sourceId) {
        this.repositoryWriter.delete(entity, commitCallbackList, failureCallbackList, redisEnabled, sourceId);
    }

    public final void delete(final E entity, final List<Runnable> commitCallbackList, final boolean redisEnabled, final String sourceId) {
        this.repositoryWriter.delete(entity, commitCallbackList, redisEnabled, sourceId);
    }

    public final void delete(final E entity, final List<Runnable> commitCallbackList, final boolean redisEnabled) {
        this.repositoryWriter.delete(entity, commitCallbackList, redisEnabled);
    }

    public final void delete(final E entity, final List<Runnable> commitCallbackList) {
        this.repositoryWriter.delete(entity, commitCallbackList, false);
    }

    @Override
    public final void delete(final E entity) {
        this.repositoryWriter.delete(entity, false);
    }

    public final void inheritRevision(final E entity, final E baseline) {
        if (this.tableSchema.isOptimisticLocking()) {
            this.coreDatabase.getEntityRevisionStore().setRevision(entity, this.coreDatabase.getEntityRevisionStore().getState(baseline).getRevision());
        }
    }

    public final long increment(final E entity, final EntityProperty<? super E, ? extends Number> entityProperty, final long delta, final boolean redisEnabled, final String sourceId) {
        if (!entityProperty.isPersistent() || entityProperty.getValueConverter().isPresent()) {
            throw new SchemaException("Property %s cannot be incremented because it is not a persistent primitive number".formatted(entityProperty.getName()));
        }

        if (this.getUniqueProperties().stream().anyMatch(property -> property.getName().equals(entityProperty.getName()))
                || this.getCompositeIndexes().stream().anyMatch(index -> index.getProperties().stream().anyMatch(property -> property.getName().equals(entityProperty.getName())))) {
            throw new SchemaException("Indexed property %s cannot be incremented".formatted(entityProperty.getName()));
        }

        final Class<?> valueType = entityProperty.getValueType();
        if (valueType != Long.class && valueType != Integer.class) {
            throw new SchemaException("Property %s must be a Long or Integer to be incremented".formatted(entityProperty.getName()));
        }

        final long[] result = new long[1];
        final Long[] expectedRevision = new Long[1];
        this.coreDatabase.getBatchQueue().executeAtomically(dslContext -> {
            expectedRevision[0] = this.tableSchema.isOptimisticLocking()
                    ? this.coreDatabase.getEntityRevisionStore().getState(entity).getRevision()
                    : null;
            if (expectedRevision[0] != null && (expectedRevision[0] < 0L || expectedRevision[0] == Long.MAX_VALUE)) {
                throw new IllegalStateException("Invalid entity revision %s for %s".formatted(expectedRevision[0], entity.getId()));
            }

            if (valueType == Integer.class) {
                final int increment = Math.toIntExact(delta);
                final Field<Integer> field = this.tableSchema.getField(entityProperty).coerce(Integer.class);
                result[0] = this.incrementValue(dslContext, entity, field, 0, increment, expectedRevision[0]).longValue();
            } else {
                final Field<Long> field = this.tableSchema.getField(entityProperty).coerce(Long.class);
                result[0] = this.incrementValue(dslContext, entity, field, 0L, delta, expectedRevision[0]);
            }

            CacheInvalidationOutbox.insertInvalidations(dslContext, List.of(this.repositoryWriter.createInvalidation(entity.getId(), redisEnabled, sourceId)));
        });

        if (expectedRevision[0] != null) {
            this.coreDatabase.getEntityRevisionStore().setRevision(entity, expectedRevision[0] + 1L);
        }

        return result[0];
    }

    public final long increment(final E entity, final EntityProperty<? super E, ? extends Number> entityProperty, final long delta, final boolean redisEnabled) {
        return this.increment(entity, entityProperty, delta, redisEnabled, null);
    }

    private <NumberType extends Number> NumberType incrementValue(final DSLContext dslContext, final E entity, final Field<NumberType> field, final NumberType zero, final NumberType increment, final Long expectedRevision) {
        final Field<NumberType> valueField = DSL.coalesce(field, DSL.val(zero, field)).add(increment);
        UpdateSetMoreStep<Record> updateStep = dslContext.update(this.tableSchema.getTable()).set(field, valueField);
        Condition condition = this.tableSchema.getKeyCondition(this.tenantId, entity.getId());

        if (expectedRevision != null) {
            updateStep = updateStep.set(TableSchema.REVISION_FIELD, expectedRevision + 1L);
            condition = condition.and(TableSchema.REVISION_FIELD.eq(expectedRevision));
        }

        final Record record = updateStep.where(condition).returning(field).fetchOne();
        if (record == null) {
            if (expectedRevision != null) {
                throw new OptimisticLockException("Cannot increment stale entity %s".formatted(entity.getId()));
            }

            throw new DatabaseException("Cannot increment missing %s %s".formatted(this.entityType.getSimpleName(), entity.getId()));
        }

        return record.get(field);
    }

    public final <Value> void transferUniqueProperty(final E claimant, final E currentOwner, final EntityProperty<? super E, Value> entityProperty, final Value claimedValue, final Value replacementValue, final boolean redisEnabled, final String sourceId) {
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

        final RedisInvalidation claimantInvalidation = this.repositoryWriter.createInvalidation(claimant.getId(), redisEnabled, sourceId);
        final RedisInvalidation ownerInvalidation = this.repositoryWriter.createInvalidation(currentOwner.getId(), redisEnabled, sourceId);
        final Long claimantRevision = this.getExpectedRevision(claimant);
        final Long ownerRevision = this.getExpectedRevision(currentOwner);
        final long[] nextRevision = new long[2];

        this.coreDatabase.getBatchQueue().executeAtomically(dslContext -> {
            final Condition condition = TableSchema.ID_FIELD.in(claimant.getId(), currentOwner.getId());
            final Condition tenantCondition = this.tableSchema.isTenantScoped() ? condition.and(TableSchema.TENANT_FIELD.eq(this.tenantId)) : condition;
            final Result<? extends Record> recordList;

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

    public final <Value> void transferUniqueProperty(final E claimant, final E currentOwner, final EntityProperty<? super E, Value> entityProperty, final Value claimedValue, final Value replacementValue, final boolean redisEnabled) {
        this.transferUniqueProperty(claimant, currentOwner, entityProperty, claimedValue, replacementValue, redisEnabled, null);
    }

    private <Value> void updateUniqueProperty(final DSLContext dslContext, final E entity, final EntityProperty<? super E, Value> entityProperty, final Value value, final long expectedRevision) {
        final Field<Object> field = this.tableSchema.getField(entityProperty).coerce(Object.class);
        final UpdateSetMoreStep<Record> updateStep = dslContext.update(this.tableSchema.getTable()).set(field, ColumnValueMapper.toStoredObject(entityProperty, value));

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