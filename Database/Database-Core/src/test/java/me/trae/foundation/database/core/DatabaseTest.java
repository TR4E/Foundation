package me.trae.foundation.database.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.ConnectionException;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.exception.OptimisticLockException;
import me.trae.foundation.database.api.exception.QueryException;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.exception.UniqueValueTakenException;
import me.trae.foundation.database.api.holder.InstanceMode;
import me.trae.foundation.database.api.holder.LookupTier;
import me.trae.foundation.database.api.holder.RedisMode;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.property.EntityPropertyRegistry;
import me.trae.foundation.database.api.query.Direction;
import me.trae.foundation.database.api.query.Operator;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.repository.index.EntityIndex;
import me.trae.foundation.database.api.repository.index.IndexType;
import me.trae.foundation.database.api.tenant.Tenant;
import me.trae.foundation.database.api.tenant.TenantScope;
import me.trae.foundation.database.core.batch.PendingWrite;
import me.trae.foundation.database.core.batch.PendingWriteStore;
import me.trae.foundation.database.core.batch.WriteType;
import me.trae.foundation.database.core.converter.JsonValueConverter;
import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.core.driver.PostgresSettings;
import me.trae.foundation.database.core.holder.AbstractEntityHolder;
import me.trae.foundation.database.core.holder.LocalEntityIndex;
import me.trae.foundation.database.core.holder.change.ChangeTracker;
import me.trae.foundation.database.core.holder.claim.ClaimScripts;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.core.repository.QueryRenderer;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.lookup.index.RedisEntityIndex;
import me.trae.foundation.database.lookup.index.RedisPropertyIndex;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.driver.RedisSettings;
import me.trae.foundation.database.storage.local.LocalStorage;
import me.trae.foundation.database.storage.redis.RedisNamespace;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DatabaseTest {

    private static final EntityProperty<Account, String> NAME = EntityProperty.registerCaseInsensitive(Account.class, "name", Account::getName, Account::setName, true);
    private static final EntityProperty<Vehicle, String> VIN = EntityProperty.registerCaseInsensitive(Vehicle.class, "vin", Vehicle::getVin, Vehicle::setVin, true);
    private static final EntityProperty<Vehicle, VehicleState> VEHICLE_STATE = EntityProperty.register(Vehicle.class, "state", Vehicle::getState, Vehicle::setState, VehicleState.class, true);
    private static final EntityIndex<Vehicle> VIN_AND_STATE = EntityIndex.of("vin_state", VIN, VEHICLE_STATE);
    private static final EntityProperty<Account, Long> COINS = EntityProperty.register(Account.class, "coins", Account::getCoins, Account::setCoins, Long.class, true);
    private static final EntityProperty<Account, Rank> RANK = EntityProperty.register(Account.class, "rank", Account::getRank, Account::setRank, Rank.class, true);
    private static final EntityProperty<Account, List<String>> TAGS = EntityProperty.registerWithConverter(Account.class, "tags", Account::getTags, Account::setTags, JsonValueConverter.ofList(String.class), true);
    private static final EntityProperty<Account, Instant> CREATED_AT = EntityProperty.register(Account.class, "createdAt", Account::getCreatedAt, Account::setCreatedAt, Instant.class, true);
    private static final EntityProperty<Account, String> SESSION = EntityProperty.register(Account.class, "session", Account::getSession, Account::setSession, String.class, false);
    private static final EntityProperty<Reserved, String> RESERVED_ID = EntityProperty.register(Reserved.class, "id", Reserved::getLabel, Reserved::setLabel, String.class, true);

    private final String table = "test_%s".formatted(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    private final List<CoreDatabase> databaseList = new ArrayList<>();
    private final List<PostgresDriver> additionalPostgresDriverList = new ArrayList<>();

    private PostgresDriver postgresDriver;
    private RedisDriver redisDriver;

    @Test
    void pendingWritesCoalescePerEntity() {
        final TableSchema<Account> tableSchema = new TableSchema<>(Account.class, "accounts", TenantScope.NONE, EntityPropertyRegistry.getProperties(Account.class));

        final PendingWriteStore pendingWriteStore = new PendingWriteStore();

        final UUID first = UUID.randomUUID();

        final UUID second = UUID.randomUUID();

        pendingWriteStore.queueUpdate(tableSchema, null, first, Map.<Field<?>, Object>of(tableSchema.getField(NAME), "old", tableSchema.getField(COINS), 1L), List.of());
        pendingWriteStore.queueUpdate(tableSchema, null, second, Map.<Field<?>, Object>of(tableSchema.getField(NAME), "other"), List.of());
        pendingWriteStore.queueUpdate(tableSchema, null, first, Map.<Field<?>, Object>of(tableSchema.getField(NAME), "new"), List.of());
        pendingWriteStore.queueDelete(tableSchema, null, second);

        final List<PendingWrite> writeList = pendingWriteStore.drain();
        assertEquals(2, writeList.size());
        assertEquals(Map.of(tableSchema.getField(NAME), "new", tableSchema.getField(COINS), 1L), writeList.getFirst().getValueMap());
        assertEquals(WriteType.DELETE, writeList.getLast().getWriteType());
        assertTrue(pendingWriteStore.isEmpty());

        pendingWriteStore.queueUpdate(tableSchema, null, first, Map.<Field<?>, Object>of(tableSchema.getField(NAME), "newest"), List.of());
        pendingWriteStore.requeue(writeList.getFirst());
        assertEquals("newest", pendingWriteStore.drain().getFirst().getValueMap().get(tableSchema.getField(NAME)));
    }

    @Test
    void pendingWriteStoreRejectsOverflowAndCountsInFlightWrites() {
        final PendingWriteStore pendingWriteStore = new PendingWriteStore(1);

        final TableSchema<Account> tableSchema = new TableSchema<>(Account.class, "accounts", TenantScope.NONE, EntityPropertyRegistry.getProperties(Account.class));

        final UUID first = UUID.randomUUID();

        pendingWriteStore.queueUpdate(tableSchema, null, first, Map.<Field<?>, Object>of(tableSchema.getField(NAME), "first"), List.of());
        pendingWriteStore.queueUpdate(tableSchema, null, first, Map.<Field<?>, Object>of(tableSchema.getField(NAME), "updated"), List.of());

        final PendingWrite pendingWrite = pendingWriteStore.drain().getFirst();
        assertThrows(DatabaseException.class, () -> pendingWriteStore.queueUpdate(tableSchema, null, UUID.randomUUID(), Map.<Field<?>, Object>of(tableSchema.getField(NAME), "overflow"), List.of()));

        pendingWriteStore.complete(pendingWrite);
        pendingWriteStore.queueUpdate(tableSchema, null, UUID.randomUUID(), Map.<Field<?>, Object>of(tableSchema.getField(NAME), "accepted"), List.of());
        assertEquals(1, pendingWriteStore.size());
    }

    @Test
    void coalescedCommitCallbacksRunOnceInRegistrationOrder() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final List<Integer> callbackOrder = new ArrayList<>();
        database.start();

        final Account account = this.createAccount("initial");
        repository.save(account, List.of(() -> callbackOrder.add(1)));
        account.setName("updated");
        repository.update(account, repository.getProperties(), List.of(() -> callbackOrder.add(2)));
        account.setName("latest");
        repository.update(account, repository.getProperties(), List.of(() -> callbackOrder.add(3)));

        database.getBatchQueue().flush();

        assertEquals(List.of(1, 2, 3), callbackOrder);
        assertEquals("latest", repository.findById(account.getId()).orElseThrow().getName());
    }

    @Test
    void commitCallbacksRemainPendingAcrossPostgresReconnect() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AtomicInteger callbackCount = new AtomicInteger();
        database.start();

        final Account account = this.createAccount("reconnect");
        repository.save(account, List.of(callbackCount::incrementAndGet));
        this.postgresDriver.disconnect();

        database.getBatchQueue().flush();

        assertEquals(0, callbackCount.get());
        assertEquals(1, database.getBatchQueue().getPendingWriteStore().size());

        this.postgresDriver.connect();
        database.getBatchQueue().flush();

        assertEquals(1, callbackCount.get());
        assertEquals("reconnect", repository.findById(account.getId()).orElseThrow().getName());
    }

    @Test
    void optimisticLockingRejectsStaleEntityWrites() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, "revision_accounts_%s".formatted(UUID.randomUUID().toString().replace("-", "")), TenantScope.NONE, true);

        final List<DatabaseException> failureList = new ArrayList<>();
        database.setFailureHandler(failureList::add);
        database.start();

        final Account initial = this.createAccount("initial");
        repository.save(initial);
        database.getBatchQueue().flush();
        assertTrue(failureList.isEmpty());

        final Account stale = repository.findById(initial.getId()).orElseThrow();

        final Account current = repository.findById(initial.getId()).orElseThrow();
        assertEquals(0L, database.getEntityRevisionStore().getState(current).getRevision());

        current.setName("current");
        repository.update(current);
        database.getBatchQueue().flush();
        assertEquals(1L, database.getEntityRevisionStore().getState(current).getRevision());
        assertEquals(1L, database.getEntityRevisionStore().getState(repository.findById(initial.getId()).orElseThrow()).getRevision());
        assertEquals(0L, database.getEntityRevisionStore().getState(stale).getRevision());

        repository.update(current);
        database.getBatchQueue().flush();

        assertEquals(1L, database.getEntityRevisionStore().getState(current).getRevision());
        assertEquals(1L, database.getEntityRevisionStore().getState(repository.findById(initial.getId()).orElseThrow()).getRevision());

        stale.setName("stale");
        repository.update(stale);
        database.getBatchQueue().flush();

        final Account stored = repository.findById(initial.getId()).orElseThrow();
        assertEquals("current", stored.getName());
        assertEquals(1L, database.getEntityRevisionStore().getState(stored).getRevision());
        assertEquals(1L, database.getEntityRevisionStore().getState(current).getRevision());
        assertTrue(failureList.stream().anyMatch(OptimisticLockException.class::isInstance));
    }

    @Test
    void optimisticLockingDoesNotRecreateAnEntityDeletedAfterItWasRead() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, "deleted_revision_accounts_%s".formatted(UUID.randomUUID().toString().replace("-", "")), TenantScope.NONE, true);
        database.start();

        final Account initial = this.createAccount("initial");
        repository.save(initial);
        database.getBatchQueue().flush();

        final Account stale = repository.findById(initial.getId()).orElseThrow();
        repository.delete(initial);
        database.getBatchQueue().flush();

        stale.setName("stale");
        assertThrows(OptimisticLockException.class, () -> repository.update(stale));

        assertTrue(repository.findById(initial.getId()).isEmpty());
    }

    @Test
    void changeTrackerFindsOnlyChangedProperties() {
        final ChangeTracker<Account> changeTracker = new ChangeTracker<>(Account.class, null);

        final Account account = this.createAccount("sample-value");

        assertFalse(changeTracker.isTracked(account.getId()));
        changeTracker.snapshot(account);
        assertTrue(changeTracker.diff(account).isEmpty());

        account.setName("temporary");
        account.setName("sample-value");
        assertTrue(changeTracker.diff(account).isEmpty());

        account.setCoins(500L);
        account.setRank(Rank.MEMBER);
        account.setTags(new ArrayList<>(List.of("member")));
        account.getTags().add("mutable");

        final List<EntityProperty<?, ?>> changedList = changeTracker.diff(account);
        assertEquals(List.of(COINS, RANK, TAGS), changedList);

        changeTracker.commit(account, changedList);
        assertTrue(changeTracker.diff(account).isEmpty());
    }

    @Test
    void schemaAndQueriesRenderAsExpected() {
        final TableSchema<Account> tableSchema = new TableSchema<>(Account.class, "accounts", TenantScope.INSTANCE, EntityPropertyRegistry.getProperties(Account.class));

        final DSLContext dslContext = DSL.using(SQLDialect.POSTGRES);

        final String sql = dslContext.renderInlined(QueryRenderer.toCondition(tableSchema, Query.where(RANK, Operator.EQUALS, Rank.ADMIN).and(COINS, Operator.GREATER_THAN, 10L), "lobby-1"));
        assertTrue(sql.contains("\"tenant_id\" = 'lobby-1'"));
        assertTrue(sql.contains("\"rank\" = 'ADMIN'"));
        assertTrue(sql.contains("\"coins\" > 10"));

        final String caseInsensitiveSql = dslContext.renderInlined(QueryRenderer.toCondition(tableSchema, Query.where(NAME, Operator.EQUALS, "SAMPLE-VALUE"), null));
        assertTrue(caseInsensitiveSql.contains("lower(\"name\") = 'sample-value'"));

        final UUID excludedId = UUID.randomUUID();
        final String idExclusionSql = dslContext.renderInlined(QueryRenderer.toCondition(tableSchema, Query.where(NAME, Operator.EQUALS, "sample-value").andIdNotEquals(excludedId), null));
        assertTrue(idExclusionSql.contains("lower(\"name\") = 'sample-value'"));
        assertTrue(idExclusionSql.contains("\"id\" <>"));
        assertTrue(idExclusionSql.contains(excludedId.toString()));

        assertFalse(dslContext.renderInlined(QueryRenderer.toCondition(tableSchema, Query.of(Account.class).allTenants(), "lobby-1")).contains("tenant_id"));

        assertEquals(Instant.class, tableSchema.getField(CREATED_AT).getType());
        assertEquals(JSONB.class, tableSchema.getField(TAGS).getType());
        assertThrows(QueryException.class, () -> tableSchema.getField(SESSION));
        assertThrows(SchemaException.class, () -> new TableSchema<>(Reserved.class, "reserved", TenantScope.NONE, List.of(RESERVED_ID)));
    }

    @Test
    void savedEntitySurvivesRestart() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountHolder holder = new AccountHolder(new AccountRepository(database, this.table, TenantScope.NONE));
        database.start();

        final Account account = this.createAccount("sample-value");
        holder.save(account);
        database.stop();

        final CoreDatabase restarted = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(restarted, this.table, TenantScope.NONE);
        restarted.start();

        final Account loaded = repository.findById(account.getId()).orElseThrow();
        assertEquals(account.getName(), loaded.getName());
        assertEquals(account.getCoins(), loaded.getCoins());
        assertEquals(account.getRank(), loaded.getRank());
        assertEquals(account.getTags(), loaded.getTags());
        assertEquals(account.getCreatedAt(), loaded.getCreatedAt());
        assertNull(loaded.getSession());

        final AccountHolder restartedHolder = new AccountHolder(repository);

        final Account cached = restartedHolder.getById(account.getId()).orElseThrow();
        assertNull(cached.getSession());
        assertEquals(account.getTags(), cached.getTags());
        assertTrue(restartedHolder.getById(account.getId(), EnumSet.of(LookupTier.REDIS)).isPresent());
    }

    @Test
    void saveCreatesAndNeverOverwritesAnExistingUuid() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final List<DatabaseException> failureList = new CopyOnWriteArrayList<>();
        database.setFailureHandler(failureList::add);
        database.start();

        final Account original = this.createAccount("original");
        repository.save(original);
        database.getBatchQueue().flush();

        final Account duplicate = this.createAccount("replacement");

        final Account duplicateId = new Account(original.getId());
        duplicateId.setName(duplicate.getName());
        duplicateId.setCoins(duplicate.getCoins());
        duplicateId.setRank(duplicate.getRank());
        duplicateId.setTags(duplicate.getTags());
        duplicateId.setCreatedAt(duplicate.getCreatedAt());
        repository.save(duplicateId);
        database.getBatchQueue().flush();

        assertEquals("original", repository.findById(original.getId()).orElseThrow().getName());
        assertTrue(failureList.stream().anyMatch(exception -> exception.getMessage().contains("Cannot create duplicate")));
    }

    @Test
    void rejectedCreateIsRemovedFromTheLocalCache() {
        final CoreDatabase firstDatabase = this.openDatabase(null);

        final AccountRepository firstRepository = new AccountRepository(firstDatabase, this.table, TenantScope.NONE);

        final AccountHolder firstHolder = new AccountHolder(firstRepository);
        firstDatabase.start();

        final Account original = this.createAccount("original");
        firstHolder.save(original);
        firstDatabase.getBatchQueue().flush();

        final CoreDatabase secondDatabase = this.openDatabaseWithoutRedis();

        final AccountRepository secondRepository = new AccountRepository(secondDatabase, this.table, TenantScope.NONE);

        final AccountHolder secondHolder = new AccountHolder(secondRepository);
        secondDatabase.start();

        final Account duplicate = new Account(original.getId());
        duplicate.setName("replacement");
        duplicate.setCoins(200L);
        duplicate.setRank(Rank.MEMBER);
        duplicate.setTags(List.of());
        duplicate.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        secondHolder.save(duplicate);
        assertTrue(secondHolder.getById(duplicate.getId(), EnumSet.of(LookupTier.LOCAL)).isPresent());

        secondDatabase.getBatchQueue().flush();

        assertTrue(secondHolder.getById(duplicate.getId(), EnumSet.of(LookupTier.LOCAL)).isEmpty());
        assertEquals("original", firstRepository.findById(original.getId()).orElseThrow().getName());
    }

    @Test
    void holderCoalescesCreateAndUpdateBeforeTheInsertFlushes() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository);
        database.start();

        final Account account = this.createAccount("initial");
        holder.save(account);
        account.setName("updated");
        account.setCoins(250L);
        holder.update(account);

        final PendingWrite pendingWrite = database.getBatchQueue().getPendingWriteStore().drain().getFirst();
        assertEquals(WriteType.INSERT, pendingWrite.getWriteType());
        database.getBatchQueue().getPendingWriteStore().requeue(pendingWrite);
        database.getBatchQueue().flush();

        final Account stored = repository.findById(account.getId()).orElseThrow();
        assertEquals("updated", stored.getName());
        assertEquals(250L, stored.getCoins());
    }

    @Test
    void updateOnlyWritesChangedPersistentPropertiesAndRejectsMissingRows() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository);
        database.start();

        final Account account = this.createAccount("initial");
        holder.save(account);
        database.getBatchQueue().flush();

        holder.update(account);
        account.setSession("local-only");
        holder.update(account);
        assertEquals(0, database.getBatchQueue().getPendingWriteStore().size());

        account.setRank(Rank.MEMBER);
        holder.update(account);
        final PendingWrite pendingWrite = database.getBatchQueue().getPendingWriteStore().drain().getFirst();
        assertEquals(1, pendingWrite.getValueMap().size());
        assertEquals("MEMBER", pendingWrite.getValueMap().get(repository.getTableSchema().getField(RANK)));
        database.getBatchQueue().getPendingWriteStore().requeue(pendingWrite);
        database.getBatchQueue().flush();

        final Account detached = repository.findById(account.getId()).orElseThrow();
        repository.update(detached);
        assertEquals(0, database.getBatchQueue().getPendingWriteStore().size());

        detached.setName("repository-update");
        repository.update(detached);
        assertEquals(1, database.getBatchQueue().getPendingWriteStore().size());
        database.getBatchQueue().flush();
        assertEquals("repository-update", repository.findById(account.getId()).orElseThrow().getName());

        final Account missing = this.createAccount("missing");
        assertThrows(DatabaseException.class, () -> repository.update(missing));
    }

    @Test
    void holderCanUpdateAnEntityAfterItsTrackingSnapshotWasEvicted() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository);
        database.start();

        final Account account = this.createAccount("initial");
        holder.save(account);
        database.getBatchQueue().flush();
        holder.evict(account);

        account.setName("after-eviction");
        holder.update(account);
        database.getBatchQueue().flush();

        assertEquals("after-eviction", repository.findById(account.getId()).orElseThrow().getName());
    }

    @Test
    void multiInstanceHolderCanUpdateAnEntityLoadedFromRedis() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder writer = new AccountHolder(repository);

        final AccountHolder reader = new AccountHolder(repository, InstanceMode.MULTI_INSTANCE);
        database.start();

        final Account account = this.createAccount("initial");
        writer.save(account);
        database.getBatchQueue().flush();

        final Account cached = reader.getById(account.getId()).orElseThrow();
        cached.setRank(Rank.MEMBER);
        reader.update(cached);
        database.getBatchQueue().flush();

        assertEquals(Rank.MEMBER, repository.findById(account.getId()).orElseThrow().getRank());
    }

    @Test
    void unchangedEntityDoesNotInvalidateRedisCache() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository);

        final AccountHolder reader = new AccountHolder(repository, InstanceMode.MULTI_INSTANCE);
        database.start();

        final Account account = this.createAccount("sample-value");
        holder.save(account);
        database.getBatchQueue().flush();

        reader.getById(account.getId());
        final String cacheKey = RedisNamespace.of(this.table).getKey(account.getId());
        assertTrue(this.redisDriver.getCommands().exists(cacheKey) > 0L);

        account.setRank(Rank.MEMBER);
        account.setRank(Rank.ADMIN);
        holder.update(account);
        database.getBatchQueue().flush();

        assertTrue(this.redisDriver.getCommands().exists(cacheKey) > 0L);
        assertEquals(0, database.getBatchQueue().getPendingWriteStore().size());
        assertEquals(0, this.getOutboxCount(account.getId()));

        account.setSession("transient");
        holder.update(account);
        database.getBatchQueue().flush();

        assertTrue(this.redisDriver.getCommands().exists(cacheKey) > 0L);
    }

    @Test
    void singletonHolderUsesPostgresWithoutRedisDriver() {
        final CoreDatabase database = this.openDatabaseWithoutRedis();

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository);
        database.start();

        final Account account = this.createAccount("postgres-only");
        holder.save(account);
        database.getBatchQueue().flush();

        assertTrue(repository.findById(account.getId()).isPresent());

        final Account loaded = holder.getById(account.getId()).orElseThrow();
        loaded.setRank(Rank.MEMBER);
        holder.update(loaded);
        database.getBatchQueue().flush();

        assertEquals(Rank.MEMBER, repository.findById(account.getId()).orElseThrow().getRank());
    }

    @Test
    void singletonHolderCanDisableRedisOnASharedCoreDatabase() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository, InstanceMode.SINGLE_INSTANCE, RedisMode.DISABLED);
        database.start();

        final Account account = this.createAccount("holder-redis-disabled");
        holder.save(account);
        database.getBatchQueue().flush();

        assertTrue(repository.findById(account.getId()).isPresent());
        assertTrue(this.redisDriver.getCommands().keys("%s*".formatted(this.table)).isEmpty());
        assertEquals(0, this.getOutboxCount(account.getId()));

        final Account loaded = holder.getById(account.getId()).orElseThrow();
        loaded.setRank(Rank.MEMBER);
        holder.update(loaded);
        database.getBatchQueue().flush();

        assertEquals(Rank.MEMBER, repository.findById(account.getId()).orElseThrow().getRank());
        assertTrue(this.redisDriver.getCommands().keys("%s*".formatted(this.table)).isEmpty());
        assertEquals(0, this.getOutboxCount(account.getId()));
    }

    @Test
    void directRepositoryWritesDoNotAssumeHolderRedisPolicy() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);
        database.start();

        final Account account = this.createAccount("repository-redis-neutral");
        repository.save(account);
        database.getBatchQueue().flush();

        assertTrue(repository.findById(account.getId()).isPresent());
        assertTrue(this.redisDriver.getCommands().keys("%s*".formatted(this.table)).isEmpty());
        assertEquals(0, this.getOutboxCount(account.getId()));

        final Account loaded = repository.findById(account.getId()).orElseThrow();
        loaded.setRank(Rank.MEMBER);
        repository.update(loaded);
        database.getBatchQueue().flush();

        assertEquals(Rank.MEMBER, repository.findById(account.getId()).orElseThrow().getRank());
        assertEquals(0, this.getOutboxCount(account.getId()));

        repository.delete(loaded);
        database.getBatchQueue().flush();

        assertTrue(repository.findById(account.getId()).isEmpty());
        assertEquals(0, this.getOutboxCount(account.getId()));
    }

    @Test
    void multiInstanceHolderRequiresRedisDriver() {
        final CoreDatabase database = this.openDatabaseWithoutRedis();

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);
        assertThrows(IllegalArgumentException.class, () -> new AccountHolder(repository, InstanceMode.MULTI_INSTANCE));
    }

    @Test
    void multiInstanceHolderCannotDisableRedis() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);
        assertThrows(IllegalArgumentException.class, () -> new AccountHolder(repository, InstanceMode.MULTI_INSTANCE, RedisMode.DISABLED));
    }

    @Test
    void repositoriesStayBoundToTheirCoreDatabaseAndBatchQueue() {
        this.connect();
        final String globalTable = "%s_global".formatted(this.table);

        final String localTable = "%s_local".formatted(this.table);

        final PostgresDriver localPostgresDriver = new PostgresDriver(this.postgresDriver.getPostgresSettings());
        localPostgresDriver.connect();
        this.additionalPostgresDriverList.add(localPostgresDriver);

        try {
            final GlobalDatabase globalDatabase = new GlobalDatabase(this.postgresDriver, this.redisDriver, null, Duration.ofHours(1), 500, PendingWriteStore.DEFAULT_MAX_PENDING_WRITES);

            final LocalDatabase localDatabase = new LocalDatabase(localPostgresDriver, this.redisDriver, new Tenant("LIFESTEAL", 1), Duration.ofHours(1), 500, PendingWriteStore.DEFAULT_MAX_PENDING_WRITES);
            this.databaseList.add(localDatabase);

            final AccountRepository globalRepository = new AccountRepository(globalDatabase, globalTable, TenantScope.NONE);

            final AccountRepository localRepository = new AccountRepository(localDatabase, localTable, TenantScope.NONE);
            globalDatabase.start();
            localDatabase.start();

            final Account globalAccount = this.createAccount("global");

            final Account localAccount = this.createAccount("local");
            globalRepository.save(globalAccount);
            localRepository.save(localAccount);
            globalDatabase.getBatchQueue().flush();
            localDatabase.getBatchQueue().flush();

            assertTrue(globalRepository.findById(globalAccount.getId()).isPresent());
            assertTrue(localRepository.findById(localAccount.getId()).isPresent());
            assertEquals(0, globalDatabase.getBatchQueue().getPendingWriteStore().size());
            assertEquals(0, localDatabase.getBatchQueue().getPendingWriteStore().size());
            assertFalse(globalDatabase.getBatchQueue() == localDatabase.getBatchQueue());
        } finally {
            this.postgresDriver.getDslContext().dropTableIfExists(DSL.name(globalTable)).cascade().execute();
            this.postgresDriver.getDslContext().dropTableIfExists(DSL.name(localTable)).cascade().execute();
        }
    }

    @Test
    void committedWriteRemainsInOutboxUntilRedisRecovers() throws InterruptedException {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository);

        final List<DatabaseException> failureList = new CopyOnWriteArrayList<>();
        database.setFailureHandler(failureList::add);
        database.start();

        final Account account = this.createAccount("sample-value");
        holder.save(account);
        this.redisDriver.disconnect();
        database.getBatchQueue().flush();

        assertTrue(repository.findById(account.getId()).isPresent());
        assertEquals(1, this.getOutboxCount(account.getId()));
        assertFalse(failureList.isEmpty());

        this.redisDriver.connect();
        Thread.sleep(1_050L);
        database.getBatchQueue().flush();

        assertEquals(0, this.getOutboxCount(account.getId()));
    }

    @Test
    void outboxInsertFailureRollsBackEntityWrite() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository);

        final List<DatabaseException> failureList = new CopyOnWriteArrayList<>();
        database.setFailureHandler(failureList::add);
        database.start();

        final Account account = this.createAccount("sample-value");
        holder.save(account);
        this.postgresDriver.getDslContext().dropTableIfExists(DSL.name("foundation_cache_invalidation_outbox")).execute();
        database.getBatchQueue().flush();

        assertTrue(repository.findById(account.getId()).isEmpty());
        assertFalse(failureList.isEmpty());

        database.getBatchQueue().synchronizeOutbox();
    }

    @Test
    void emptyOutboxPollDoesNotContactRedis() {
        final CoreDatabase database = this.openDatabase(null);

        final List<DatabaseException> failureList = new CopyOnWriteArrayList<>();
        database.setFailureHandler(failureList::add);
        database.start();

        this.redisDriver.disconnect();

        try {
            database.getBatchQueue().flush();

            assertTrue(failureList.isEmpty());
        } finally {
            this.redisDriver.connect();
        }
    }

    @Test
    void entityLookupsFallBackToPostgresWhenRedisIsUnavailable() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder writer = new AccountHolder(repository);
        database.start();

        final Account account = this.createAccount("sample-value");
        writer.save(account);
        database.getBatchQueue().flush();
        this.redisDriver.disconnect();

        try {
            final AccountHolder idReader = new AccountHolder(repository, InstanceMode.MULTI_INSTANCE);
            assertEquals("sample-value", idReader.getById(account.getId()).orElseThrow().getName());

            final AccountHolder propertyReader = new AccountHolder(repository, InstanceMode.MULTI_INSTANCE);
            assertEquals(account.getId(), propertyReader.getByProperty(NAME, "sample-value").orElseThrow().getId());
        } finally {
            this.redisDriver.connect();
        }
    }

    @Test
    void incrementsLandingDuringFlushesAreNotLost() throws InterruptedException {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository);
        database.start();

        final Account account = this.createAccount("sample-value");
        account.setCoins(0L);
        holder.save(account);
        database.getBatchQueue().flush();

        final AtomicBoolean running = new AtomicBoolean(true);

        final Thread flusher = Thread.ofPlatform().start(() -> {
            while (running.get()) {
                database.getBatchQueue().flush();
            }
        });

        for (int index = 0; index < 500; index++) {
            holder.increment(account, COINS, 1);
        }

        running.set(false);
        flusher.join();
        database.getBatchQueue().flush();

        assertEquals(500L, repository.findById(account.getId()).orElseThrow().getCoins());
    }

    @Test
    void incrementsFromDifferentInstancesCannotOverwriteEachOther() {
        final CoreDatabase first = this.openDatabase(null);
        final AccountRepository firstRepository = new AccountRepository(first, this.table, TenantScope.NONE);
        final AccountHolder firstHolder = new AccountHolder(firstRepository);
        first.start();

        final CoreDatabase second = this.openDatabase(null);
        final AccountRepository secondRepository = new AccountRepository(second, this.table, TenantScope.NONE);
        final AccountHolder secondHolder = new AccountHolder(secondRepository);
        second.start();

        final Account account = this.createAccount("incremented");
        firstHolder.save(account);
        first.getBatchQueue().flush();

        final Account firstCopy = firstHolder.getById(account.getId()).orElseThrow();
        final Account secondCopy = secondHolder.getById(account.getId()).orElseThrow();

        assertEquals(101L, firstHolder.increment(firstCopy, COINS, 1L));
        assertEquals(102L, secondHolder.increment(secondCopy, COINS, 1L));

        second.getBatchQueue().flush();
        first.getBatchQueue().flush();

        assertEquals(102L, firstRepository.findById(account.getId()).orElseThrow().getCoins());
    }

    @Test
    void uniqueValueHeldByAnotherInstanceIsRejected() {
        final CoreDatabase first = this.openDatabase(null);

        final AccountHolder firstHolder = new AccountHolder(new AccountRepository(first, this.table, TenantScope.NONE));
        first.start();

        final CoreDatabase second = this.openDatabase(null);

        final AccountHolder secondHolder = new AccountHolder(new AccountRepository(second, this.table, TenantScope.NONE));
        second.start();

        firstHolder.save(this.createAccount("sample-value"));
        first.getBatchQueue().flush();

        assertThrows(UniqueValueTakenException.class, () -> secondHolder.save(this.createAccount("sample-value")));
    }

    @Test
    void duplicateEntityIdIsRejectedAcrossInstancesBeforeQueueing() {
        final CoreDatabase first = this.openDatabase(null);
        final AccountRepository firstRepository = new AccountRepository(first, this.table, TenantScope.NONE);
        final AccountHolder firstHolder = new AccountHolder(firstRepository);
        first.start();

        final CoreDatabase second = this.openDatabase(null);
        final AccountRepository secondRepository = new AccountRepository(second, this.table, TenantScope.NONE);
        final AccountHolder secondHolder = new AccountHolder(secondRepository);
        second.start();

        final Account original = this.createAccount("original");
        firstHolder.save(original);

        final Account duplicate = new Account(original.getId());
        duplicate.setName("duplicate");
        duplicate.setCoins(200L);
        duplicate.setRank(Rank.MEMBER);
        duplicate.setTags(List.of());
        duplicate.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));

        assertThrows(DatabaseException.class, () -> secondHolder.save(duplicate));
        assertEquals(0, second.getBatchQueue().getPendingWriteStore().size());

        first.getBatchQueue().flush();

        assertThrows(DatabaseException.class, () -> secondHolder.save(duplicate));
        assertThrows(UniqueValueTakenException.class, () -> secondHolder.save(this.createAccount("original")));
        assertEquals("original", firstRepository.findById(original.getId()).orElseThrow().getName());

        firstHolder.delete(original);
        first.getBatchQueue().flush();

        final Account recreated = new Account(original.getId());
        recreated.setName("recreated");
        recreated.setCoins(200L);
        recreated.setRank(Rank.MEMBER);
        recreated.setTags(List.of());
        recreated.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        secondHolder.save(recreated);
        second.getBatchQueue().flush();

        assertEquals("recreated", secondRepository.findById(recreated.getId()).orElseThrow().getName());
    }

    @Test
    void claimInProgressOnAnotherInstanceIsNotStolen() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountHolder holder = new AccountHolder(new AccountRepository(database, this.table, TenantScope.NONE));
        database.start();

        final String key = new RedisPropertyIndex<>(this.redisDriver, RedisNamespace.of(this.table), NAME).getClaimKey("sample-value");
        ClaimScripts.claim(this.redisDriver, key, UUID.randomUUID().toString(), Duration.ofSeconds(30));

        assertThrows(UniqueValueTakenException.class, () -> holder.save(this.createAccount("sample-value")));
    }

    @Test
    void localSecondaryIndexesSupportSingleAndCompositeKeysWithoutScanning() {
        final EntityIndex<Account> nameIndex = EntityIndex.of("account_name", NAME);
        final EntityIndex<Account> nameAndCoinsIndex = EntityIndex.of("name_coins", NAME, COINS);
        final LocalStorage<UUID, Account> localStorage = new LocalStorage<>(Duration.ofMinutes(1));
        final LocalEntityIndex<Account> localEntityIndex = new LocalEntityIndex<>(List.of(nameIndex, nameAndCoinsIndex));
        final Account account = this.createAccount("sample-value");

        localEntityIndex.put(account, localStorage);

        assertSame(account, localEntityIndex.get(nameIndex, new Object[]{"sample-value"}, localStorage).orElseThrow());
        assertSame(account, localEntityIndex.get(nameAndCoinsIndex, new Object[]{"SAMPLE-VALUE", 100L}, localStorage).orElseThrow());

        account.setName("Updated");
        localEntityIndex.put(account, localStorage);

        assertTrue(localEntityIndex.get(nameIndex, new Object[]{"sample-value"}, localStorage).isEmpty());
        assertSame(account, localEntityIndex.get(nameAndCoinsIndex, new Object[]{"updated", 100L}, localStorage).orElseThrow());

        localEntityIndex.remove(account.getId(), localStorage);

        assertTrue(localEntityIndex.get(nameIndex, new Object[]{"updated"}, localStorage).isEmpty());
    }

    @Test
    void idLookupPopulatesRegisteredRedisIndexes() {
        final CoreDatabase database = this.openDatabase(null);
        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);
        final AccountHolder writer = new AccountHolder(repository);
        database.start();

        final Account account = this.createAccount("MiXeD-Value");
        writer.save(account);
        database.getBatchQueue().flush();

        assertEquals("MiXeD-Value", repository.findById(account.getId()).orElseThrow().getName());

        final EntityIndex<Account> nameIndex = EntityIndex.of(NAME.getName(), NAME);
        final RedisEntityIndex<Account> redisEntityIndex = new RedisEntityIndex<>(this.redisDriver, RedisNamespace.of(this.table), nameIndex, Duration.ofHours(1));
        assertTrue(new AccountHolder(repository).getById(account.getId(), EnumSet.of(LookupTier.REDIS)).isPresent());
        assertEquals(account.getId(), redisEntityIndex.find(nameIndex.getValues(account)).orElseThrow());
        redisEntityIndex.remove(nameIndex.getValues(account), account.getId());

        final AccountHolder reader = new AccountHolder(repository);
        assertTrue(reader.getByProperty(NAME, "mixed-value", EnumSet.of(LookupTier.LOCAL)).isEmpty());

        final Account indexedAccount = reader.getByProperty(NAME, "mixed-value").orElseThrow();
        assertEquals(account.getId(), indexedAccount.getId());
        assertSame(indexedAccount, reader.getByProperty(NAME, "mixed-value", EnumSet.of(LookupTier.LOCAL)).orElseThrow());
        assertEquals("MiXeD-Value", reader.getById(account.getId()).orElseThrow().getName());
        assertEquals(account.getId(), redisEntityIndex.find(nameIndex.getValues(account)).orElseThrow());
    }

    @Test
    void transferUniquePropertyChangesBothRowsAndIndexesAtomically() {
        final CoreDatabase database = this.openDatabase(null);
        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE, true);
        final AccountHolder holder = new AccountHolder(repository);
        database.start();

        final Account claimant = this.createAccount("claimant-value");
        final Account currentOwner = this.createAccount("sample-value");
        holder.save(claimant);
        holder.save(currentOwner);
        database.getBatchQueue().flush();

        final String replacement = "replacement-%s".formatted(currentOwner.getId());
        holder.transferUniqueProperty(claimant.getId(), currentOwner.getId(), NAME, "SAMPLE-VALUE", replacement);

        assertEquals("SAMPLE-VALUE", repository.findById(claimant.getId()).orElseThrow().getName());
        assertEquals(replacement, repository.findById(currentOwner.getId()).orElseThrow().getName());
        assertEquals(claimant.getId(), holder.getByProperty(NAME, "sample-value").orElseThrow().getId());
        assertEquals(currentOwner.getId(), holder.getByProperty(NAME, replacement).orElseThrow().getId());
        assertEquals(1, this.getOutboxCount(claimant.getId()));
        assertEquals(1, this.getOutboxCount(currentOwner.getId()));
        assertEquals(1L, database.getEntityRevisionStore().getState(claimant).getRevision());
        assertEquals(1L, database.getEntityRevisionStore().getState(currentOwner).getRevision());
    }

    @Test
    void transferUniquePropertyRollsBackWhenReplacementIsAlreadyTaken() {
        final CoreDatabase database = this.openDatabase(null);
        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);
        final AccountHolder holder = new AccountHolder(repository);
        database.start();

        final Account claimant = this.createAccount("claimant-value");
        final Account currentOwner = this.createAccount("sample-value");
        final Account replacementOwner = this.createAccount("taken");
        holder.save(claimant);
        holder.save(currentOwner);
        holder.save(replacementOwner);
        database.getBatchQueue().flush();

        assertThrows(RuntimeException.class, () -> holder.transferUniqueProperty(claimant.getId(), currentOwner.getId(), NAME, "sample-value", "taken"));

        assertEquals("claimant-value", repository.findById(claimant.getId()).orElseThrow().getName());
        assertEquals("sample-value", repository.findById(currentOwner.getId()).orElseThrow().getName());
        assertEquals("taken", repository.findById(replacementOwner.getId()).orElseThrow().getName());
    }

    @Test
    void compositeIndexLookupUsesLocalRedisAndDatabaseAndTracksChanges() {
        final CoreDatabase database = this.openDatabase(null);
        final VehicleRepository repository = new VehicleRepository(database, this.table);
        final VehicleHolder holder = new VehicleHolder(repository, RedisMode.ENABLED);
        database.start();

        final Vehicle vehicle = new Vehicle(UUID.randomUUID());
        vehicle.setVin("1abc234");
        vehicle.setState(VehicleState.NSW);
        holder.save(vehicle);
        database.getBatchQueue().flush();

        assertEquals(vehicle.getId(), holder.getByIndex(VIN_AND_STATE, "1ABC234", VehicleState.NSW).orElseThrow().getId());

        final CoreDatabase secondDatabase = this.openDatabase(null);
        final VehicleHolder secondHolder = new VehicleHolder(new VehicleRepository(secondDatabase, this.table), RedisMode.ENABLED);
        secondDatabase.start();
        assertTrue(secondHolder.getByIndex(VIN_AND_STATE, EnumSet.of(LookupTier.LOCAL), "1AbC234", VehicleState.NSW).isEmpty());

        final Vehicle cachedVehicle = secondHolder.getByIndex(VIN_AND_STATE, "1AbC234", VehicleState.NSW).orElseThrow();
        assertEquals(vehicle.getId(), cachedVehicle.getId());
        assertSame(cachedVehicle, secondHolder.getByIndex(VIN_AND_STATE, EnumSet.of(LookupTier.LOCAL), "1AbC234", VehicleState.NSW).orElseThrow());

        secondHolder.evict(cachedVehicle);
        assertEquals(vehicle.getId(), secondHolder.getByIndex(VIN_AND_STATE, "1AbC234", VehicleState.NSW).orElseThrow().getId());

        final Vehicle duplicate = new Vehicle(UUID.randomUUID());
        duplicate.setVin("1ABC234");
        duplicate.setState(VehicleState.NSW);
        assertThrows(UniqueValueTakenException.class, () -> secondHolder.save(duplicate));

        vehicle.setState(VehicleState.VIC);
        holder.update(vehicle);
        database.getBatchQueue().flush();

        assertTrue(repository.findOne(Query.of(Vehicle.class).and(VIN, Operator.EQUALS, "1ABC234").and(VEHICLE_STATE, Operator.EQUALS, VehicleState.NSW)).isEmpty());
        assertEquals(vehicle.getId(), holder.getByIndex(VIN_AND_STATE, "1ABC234", VehicleState.VIC).orElseThrow().getId());
    }

    @Test
    void tenantsOnlySeeTheirOwnRows() {
        final CoreDatabase lobbyOne = this.openDatabase(new Tenant("lobby", 1));

        final AccountRepository lobbyOneRepository = new AccountRepository(lobbyOne, this.table, TenantScope.INSTANCE);

        final AccountHolder lobbyOneHolder = new AccountHolder(lobbyOneRepository);
        lobbyOne.start();

        final CoreDatabase lobbyTwo = this.openDatabase(new Tenant("lobby", 2));

        final AccountHolder lobbyTwoHolder = new AccountHolder(new AccountRepository(lobbyTwo, this.table, TenantScope.INSTANCE));
        lobbyTwo.start();

        lobbyOneHolder.save(this.createAccount("sample-value"));
        lobbyTwoHolder.save(this.createAccount("sample-value"));
        lobbyOne.getBatchQueue().flush();
        lobbyTwo.getBatchQueue().flush();

        assertEquals(1, lobbyOneRepository.findMany(Query.of(Account.class)).size());
        assertEquals(2, lobbyOneRepository.findMany(Query.of(Account.class).allTenants()).size());
    }

    @Test
    void deleteRemovesEverywhereAndFreesTheName() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository);
        database.start();

        final Account account = this.createAccount("sample-value");
        holder.save(account);
        database.getBatchQueue().flush();

        holder.delete(account);
        database.getBatchQueue().flush();

        assertTrue(repository.findById(account.getId()).isEmpty());
        assertTrue(holder.getById(account.getId()).isEmpty());
        holder.save(this.createAccount("sample-value"));
    }

    @Test
    void findsByUniquePropertyFromAnotherInstance() {
        final CoreDatabase first = this.openDatabase(null);

        final AccountHolder firstHolder = new AccountHolder(new AccountRepository(first, this.table, TenantScope.NONE));
        first.start();

        final CoreDatabase second = this.openDatabase(null);

        final AccountHolder secondHolder = new AccountHolder(new AccountRepository(second, this.table, TenantScope.NONE));
        second.start();

        final Account account = this.createAccount("sample-value");
        firstHolder.save(account);
        first.getBatchQueue().flush();

        assertEquals(account.getId(), secondHolder.getByProperty(NAME, "sample-value").orElseThrow().getId());
        assertTrue(secondHolder.getByProperty(NAME, "nobody").isEmpty());
    }

    @Test
    void flushWritesChangesMadeToCachedEntities() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository);
        database.start();

        final Account account = this.createAccount("sample-value");
        holder.save(account);
        database.getBatchQueue().flush();

        account.setCoins(250L);
        database.getBatchQueue().flush();

        assertEquals(250L, repository.findById(account.getId()).orElseThrow().getCoins());
    }

    @Test
    void pinKeepsEntityAndUnpinWritesItsChanges() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository);
        database.start();

        final Account account = this.createAccount("sample-value");
        holder.save(account);
        holder.pin(account);
        assertEquals(List.of(account), holder.getPinned());
        assertSame(account, holder.getByProperty(NAME, "SAMPLE-VALUE").orElseThrow());

        account.setCoins(999L);
        holder.unpin(account);
        database.getBatchQueue().flush();

        assertTrue(holder.getPinned().isEmpty());
        assertEquals(999L, repository.findById(account.getId()).orElseThrow().getCoins());
    }

    @Test
    void multiInstanceHolderCanPinItsLocalCopyAndUnpinWritesChanges() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder writer = new AccountHolder(repository);

        final AccountHolder reader = new AccountHolder(repository, InstanceMode.MULTI_INSTANCE);
        database.start();

        final Account account = this.createAccount("sample-value");
        writer.save(account);
        database.getBatchQueue().flush();

        final Account first = reader.getById(account.getId()).orElseThrow();
        assertSame(first, reader.getById(account.getId()).orElseThrow());
        reader.pin(first);

        assertEquals(List.of(first), reader.getPinned());
        assertSame(first, reader.getById(account.getId()).orElseThrow());

        first.setCoins(999L);
        reader.unpin(first);
        database.getBatchQueue().flush();

        assertTrue(reader.getPinned().isEmpty());
        assertEquals(999L, repository.findById(account.getId()).orElseThrow().getCoins());
    }

    @Test
    void multiInstanceInvalidationEvictsPinnedLocalCopies() throws InterruptedException {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder writer = new AccountHolder(repository);
        final AccountHolder reader = new AccountHolder(repository, InstanceMode.MULTI_INSTANCE);
        database.start();

        final Account account = this.createAccount("sample-value");
        writer.save(account);
        database.getBatchQueue().flush();

        final Account localAccount = reader.getById(account.getId()).orElseThrow();
        reader.pin(localAccount);

        account.setName("updated-value");
        writer.update(account);
        database.getBatchQueue().flush();

        final long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();

        while (!reader.getPinned().isEmpty() && System.nanoTime() < deadline) {
            database.getBatchQueue().flush();
            Thread.sleep(10L);
        }

        assertTrue(reader.getPinned().isEmpty());
        final Account refreshedAccount = reader.getById(account.getId()).orElseThrow();

        assertEquals("updated-value", refreshedAccount.getName());
        assertEquals(List.of(refreshedAccount), reader.getPinned());

        localAccount.setCoins(999L);
        reader.unpin(localAccount);
        database.getBatchQueue().flush();

        assertEquals(100L, repository.findById(account.getId()).orElseThrow().getCoins());
    }

    @Test
    void queriesFilterOrderPageAndCount() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository);
        database.start();

        for (final String name : List.of("a", "b", "c", "d")) {
            final Account account = this.createAccount(name);
            account.setCoins((long) (name.charAt(0) - 'a' + 1) * 10);
            holder.save(account);
        }

        database.getBatchQueue().flush();

        assertEquals(List.of("d", "c"), repository.findMany(Query.where(COINS, Operator.GREATER_THAN, 15L).orderBy(COINS, Direction.DESCENDING).limit(2)).stream().map(Account::getName).toList());
        assertEquals(List.of("b", "c"), repository.findMany(Query.where(COINS, Operator.GREATER_THAN, 15L).orderBy(COINS, Direction.ASCENDING).limit(2).offset(0)).stream().map(Account::getName).toList());
        assertEquals(3, repository.count(Query.where(COINS, Operator.GREATER_THAN, 15L)));
        assertEquals(2, repository.findMany(Query.whereIn(NAME, List.of("a", "c"))).size());
        assertFalse(repository.exists(Query.where(NAME, Operator.EQUALS, "zzz")));
        assertThrows(QueryException.class, () -> Query.where(NAME, Operator.IN, "a"));
    }

    @Test
    void duplicateWritesWithoutRedisAreDroppedAndReported() {
        this.connect();

        final CoreDatabase database = new CoreDatabase(this.postgresDriver, null, null, Duration.ofHours(1), 500, PendingWriteStore.DEFAULT_MAX_PENDING_WRITES);
        this.databaseList.add(database);

        final List<DatabaseException> failureList = new CopyOnWriteArrayList<>();
        database.setFailureHandler(failureList::add);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository);
        database.start();

        holder.save(this.createAccount("sample-value"));
        holder.save(this.createAccount("sample-value"));
        database.getBatchQueue().flush();

        assertEquals(1, repository.count(Query.of(Account.class)));
        assertEquals(1, failureList.size());
    }

    @Test
    void badWriteIsDroppedWithoutBlockingTheRest() {
        final CoreDatabase database = this.openDatabase(null);

        final List<DatabaseException> failureList = new CopyOnWriteArrayList<>();
        database.setFailureHandler(failureList::add);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository);
        database.start();

        final Account good = this.createAccount("good");

        final Account bad = this.createAccount("bad");
        bad.setTags(List.of("bad\u0000tag"));

        holder.save(good);
        holder.save(bad);
        database.getBatchQueue().flush();

        assertTrue(repository.findById(good.getId()).isPresent());
        assertTrue(repository.findById(bad.getId()).isEmpty());
        assertEquals(1, failureList.size());
        assertTrue(database.getBatchQueue().getPendingWriteStore().isEmpty());
    }

    @Test
    void expiredCacheEntriesStillGetTheirChangesWritten() {
        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository, InstanceMode.SINGLE_INSTANCE, Duration.ZERO);
        database.start();

        final Account account = this.createAccount("sample-value");
        holder.save(account);
        account.setCoins(777L);
        database.getBatchQueue().flush();

        assertEquals(777L, repository.findById(account.getId()).orElseThrow().getCoins());
        assertTrue(holder.getById(account.getId(), EnumSet.of(LookupTier.LOCAL)).isEmpty());
    }

    @Test
    void startAddsMissingColumnsToExistingTable() {
        this.connect();
        this.postgresDriver.getDslContext().createTable(DSL.name(this.table)).column(TableSchema.ID_FIELD).constraint(DSL.primaryKey(TableSchema.ID_FIELD)).execute();

        final CoreDatabase database = this.openDatabase(null);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);

        final AccountHolder holder = new AccountHolder(repository);
        database.start();

        final Account account = this.createAccount("sample-value");
        holder.save(account);
        database.getBatchQueue().flush();

        assertEquals("sample-value", repository.findById(account.getId()).orElseThrow().getName());
    }

    @AfterEach
    void cleanUp() {
        this.databaseList.forEach(CoreDatabase::stop);
        this.additionalPostgresDriverList.forEach(PostgresDriver::disconnect);

        if (this.postgresDriver != null && this.postgresDriver.isConnected()) {
            this.postgresDriver.getDslContext().dropTableIfExists(DSL.name(this.table)).cascade().execute();
            this.postgresDriver.disconnect();
        }

        if (this.redisDriver != null && this.redisDriver.isConnected()) {
            final List<String> keyList = this.redisDriver.getCommands().keys("%s*".formatted(this.table));
            if (!keyList.isEmpty()) {
                this.redisDriver.getCommands().del(keyList.toArray(String[]::new));
            }

            this.redisDriver.disconnect();
        }
    }

    private void connect() {
        if (this.postgresDriver == null) {
            this.postgresDriver = new PostgresDriver(new PostgresSettings(System.getProperty("postgres.host", "localhost"), Integer.getInteger("postgres.port", 5432), System.getProperty("postgres.database", "foundation_test"), System.getProperty("postgres.username", "postgres"), System.getProperty("postgres.password", "postgres"), 4));
            this.redisDriver = new RedisDriver(new RedisSettings(System.getProperty("redis.host", "localhost"), Integer.getInteger("redis.port", 6379), System.getProperty("redis.password"), Integer.getInteger("redis.database", 15), Duration.ofSeconds(2)));

            try {
                this.postgresDriver.connect();
                this.redisDriver.connect();
            } catch (final ConnectionException exception) {
                Assumptions.abort("PostgreSQL or Redis is not reachable: %s".formatted(exception.getMessage()));
            }
        }
    }

    private CoreDatabase openDatabase(final Tenant tenant) {
        this.connect();

        final CoreDatabase coreDatabase = new CoreDatabase(this.postgresDriver, this.redisDriver, tenant, Duration.ofHours(1), 500, PendingWriteStore.DEFAULT_MAX_PENDING_WRITES);
        this.databaseList.add(coreDatabase);
        return coreDatabase;
    }

    private CoreDatabase openDatabaseWithoutRedis() {
        this.connect();

        final CoreDatabase coreDatabase = new CoreDatabase(this.postgresDriver, null, null, Duration.ofHours(1), 500, PendingWriteStore.DEFAULT_MAX_PENDING_WRITES);
        this.databaseList.add(coreDatabase);
        return coreDatabase;
    }

    private Account createAccount(final String name) {
        final Account account = new Account(UUID.randomUUID());
        account.setName(name);
        account.setCoins(100L);
        account.setRank(Rank.ADMIN);
        account.setTags(List.of("founder", "staff"));
        account.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        account.setSession("session");
        return account;
    }

    private int getOutboxCount(final UUID id) {
        return this.postgresDriver.getDslContext().fetchCount(
                DSL.table(DSL.name("foundation_cache_invalidation_outbox")),
                DSL.field(DSL.name("entity_id"), UUID.class).eq(id)
        );
    }

    public enum Rank {
        MEMBER, ADMIN
    }

    @RequiredArgsConstructor
    @Getter
    @Setter
    private static final class Account implements Entity {

        private final UUID id;
        private String name;
        private Long coins;
        private Rank rank;
        private List<String> tags;
        private Instant createdAt;
        private String session;
    }

    @RequiredArgsConstructor
    @Getter
    @Setter
    private static final class Reserved implements Entity {

        private final UUID id;
        private String label;
    }

    @RequiredArgsConstructor
    @Getter
    @Setter
    private static final class Vehicle implements Entity {

        private final UUID id;
        private String vin;
        private VehicleState state;
    }

    private enum VehicleState {
        VIC,
        NSW,
        QLD
    }

    private static final class AccountRepository extends AbstractEntityRepository<Account> {

        private AccountRepository(final CoreDatabase coreDatabase, final String table, final TenantScope tenantScope) {
            this(coreDatabase, table, tenantScope, false);
        }

        private AccountRepository(final CoreDatabase coreDatabase, final String table, final TenantScope tenantScope, final boolean optimisticLocking) {
            super(coreDatabase, Account.class, table, tenantScope, optimisticLocking);
        }

        @Override
        public Map<EntityProperty<? super Account, ?>, IndexType> getIndexes() {
            return Map.of(NAME, IndexType.UNIQUE);
        }
    }

    private static final class VehicleRepository extends AbstractEntityRepository<Vehicle> {

        private VehicleRepository(final CoreDatabase coreDatabase, final String table) {
            super(coreDatabase, Vehicle.class, table, TenantScope.NONE, false);
        }

        @Override
        public List<EntityIndex<Vehicle>> getCompositeIndexes() {
            return List.of(VIN_AND_STATE);
        }
    }

    private static final class AccountHolder extends AbstractEntityHolder<Account, AccountRepository> {

        private AccountHolder(final AccountRepository repository, final InstanceMode instanceMode, final Duration localExpiry) {
            super(repository, instanceMode, RedisMode.ENABLED, localExpiry, Duration.ofHours(1));
        }

        private AccountHolder(final AccountRepository repository, final InstanceMode instanceMode, final RedisMode redisMode) {
            super(repository, instanceMode, redisMode, Duration.ofMinutes(30), Duration.ofHours(1));
        }

        private AccountHolder(final AccountRepository repository, final InstanceMode instanceMode) {
            this(repository, instanceMode, Duration.ofMinutes(30));
        }

        private AccountHolder(final AccountRepository repository) {
            this(repository, InstanceMode.SINGLE_INSTANCE);
        }
    }

    private static final class VehicleHolder extends AbstractEntityHolder<Vehicle, VehicleRepository> {

        private VehicleHolder(final VehicleRepository repository, final RedisMode redisMode) {
            super(repository, InstanceMode.SINGLE_INSTANCE, redisMode, Duration.ofMinutes(30), Duration.ofHours(1));
        }
    }
}