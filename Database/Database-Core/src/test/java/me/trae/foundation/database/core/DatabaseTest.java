package me.trae.foundation.database.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.entity.RevisionedEntity;
import me.trae.foundation.database.api.exception.ConnectionException;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.exception.OptimisticLockException;
import me.trae.foundation.database.api.exception.QueryException;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.exception.UniqueValueTakenException;
import me.trae.foundation.database.api.holder.InstanceMode;
import me.trae.foundation.database.api.holder.LookupTier;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.property.EntityPropertyRegistry;
import me.trae.foundation.database.api.query.Direction;
import me.trae.foundation.database.api.query.Operator;
import me.trae.foundation.database.api.query.Query;
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
import me.trae.foundation.database.core.holder.change.ChangeTracker;
import me.trae.foundation.database.core.holder.claim.ClaimScripts;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.core.repository.QueryRenderer;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.lookup.index.RedisPropertyIndex;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.driver.RedisSettings;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DatabaseTest {

    private static final EntityProperty<Account, String> NAME = EntityProperty.register(Account.class, "name", Account::getName, Account::setName, String.class, true);
    private static final EntityProperty<Account, Long> COINS = EntityProperty.register(Account.class, "coins", Account::getCoins, Account::setCoins, Long.class, true);
    private static final EntityProperty<Account, Rank> RANK = EntityProperty.register(Account.class, "rank", Account::getRank, Account::setRank, Rank.class, true);
    private static final EntityProperty<Account, List<String>> TAGS = EntityProperty.registerWithConverter(Account.class, "tags", Account::getTags, Account::setTags, JsonValueConverter.ofList(String.class), true);
    private static final EntityProperty<Account, Instant> CREATED_AT = EntityProperty.register(Account.class, "createdAt", Account::getCreatedAt, Account::setCreatedAt, Instant.class, true);
    private static final EntityProperty<Account, String> SESSION = EntityProperty.register(Account.class, "session", Account::getSession, Account::setSession, String.class, false);
    private static final EntityProperty<Reserved, String> RESERVED_ID = EntityProperty.register(Reserved.class, "id", Reserved::getLabel, Reserved::setLabel, String.class, true);

    private final String table = "test_%s".formatted(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    private final List<CoreDatabase> databaseList = new ArrayList<>();

    private PostgresDriver postgresDriver;
    private RedisDriver redisDriver;

    @Test
    void pendingWritesCoalescePerEntity() {
        final TableSchema<Account> tableSchema = new TableSchema<>(Account.class, "accounts", TenantScope.NONE, EntityPropertyRegistry.getProperties(Account.class));
        final PendingWriteStore pendingWriteStore = new PendingWriteStore();
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();

        pendingWriteStore.queueUpsert(tableSchema, null, first, Map.<Field<?>, Object>of(tableSchema.getField(NAME), "old", tableSchema.getField(COINS), 1L), List.of());
        pendingWriteStore.queueUpsert(tableSchema, null, second, Map.<Field<?>, Object>of(tableSchema.getField(NAME), "other"), List.of());
        pendingWriteStore.queueUpsert(tableSchema, null, first, Map.<Field<?>, Object>of(tableSchema.getField(NAME), "new"), List.of());
        pendingWriteStore.queueDelete(tableSchema, null, second);

        final List<PendingWrite> writeList = pendingWriteStore.drain();
        assertEquals(2, writeList.size());
        assertEquals(Map.of(tableSchema.getField(NAME), "new", tableSchema.getField(COINS), 1L), writeList.getFirst().getValueMap());
        assertEquals(WriteType.DELETE, writeList.getLast().getWriteType());
        assertTrue(pendingWriteStore.isEmpty());

        pendingWriteStore.queueUpsert(tableSchema, null, first, Map.<Field<?>, Object>of(tableSchema.getField(NAME), "newest"), List.of());
        pendingWriteStore.requeue(writeList.getFirst());
        assertEquals("newest", pendingWriteStore.drain().getFirst().getValueMap().get(tableSchema.getField(NAME)));
    }

    @Test
    void pendingWriteStoreRejectsOverflowAndCountsInFlightWrites() {
        final PendingWriteStore pendingWriteStore = new PendingWriteStore(1);
        final TableSchema<Account> tableSchema = new TableSchema<>(Account.class, "accounts", TenantScope.NONE, EntityPropertyRegistry.getProperties(Account.class));
        final UUID first = UUID.randomUUID();

        pendingWriteStore.queueUpsert(tableSchema, null, first, Map.<Field<?>, Object>of(tableSchema.getField(NAME), "first"), List.of());
        pendingWriteStore.queueUpsert(tableSchema, null, first, Map.<Field<?>, Object>of(tableSchema.getField(NAME), "updated"), List.of());

        final PendingWrite pendingWrite = pendingWriteStore.drain().getFirst();
        assertThrows(DatabaseException.class, () -> pendingWriteStore.queueUpsert(tableSchema, null, UUID.randomUUID(), Map.<Field<?>, Object>of(tableSchema.getField(NAME), "overflow"), List.of()));

        pendingWriteStore.complete(pendingWrite);
        pendingWriteStore.queueUpsert(tableSchema, null, UUID.randomUUID(), Map.<Field<?>, Object>of(tableSchema.getField(NAME), "accepted"), List.of());
        assertEquals(1, pendingWriteStore.size());
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
        assertEquals(1L, current.getRevision());

        current.setName("current");
        repository.save(current);
        database.getBatchQueue().flush();
        assertEquals(2L, current.getRevision());
        assertEquals(2L, repository.findById(initial.getId()).orElseThrow().getRevision());
        assertEquals(1L, stale.getRevision());

        repository.save(current);
        database.getBatchQueue().flush();

        assertEquals(2L, current.getRevision());
        assertEquals(2L, repository.findById(initial.getId()).orElseThrow().getRevision());

        stale.setName("stale");
        repository.save(stale);
        database.getBatchQueue().flush();

        final Account stored = repository.findById(initial.getId()).orElseThrow();
        assertEquals("current", stored.getName());
        assertEquals(2L, stored.getRevision());
        assertEquals(2L, current.getRevision());
        assertTrue(failureList.stream().anyMatch(OptimisticLockException.class::isInstance));
    }

    @Test
    void changeTrackerFindsOnlyChangedProperties() {
        final ChangeTracker<Account> changeTracker = new ChangeTracker<>(Account.class, null);
        final Account account = this.createAccount("trae");

        assertFalse(changeTracker.isTracked(account.getId()));
        changeTracker.snapshot(account);
        assertTrue(changeTracker.diff(account).isEmpty());

        account.setName("temporary");
        account.setName("trae");
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

        final Account account = this.createAccount("trae");
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
    void unchangedEntityDoesNotInvalidateRedisCache() {
        final CoreDatabase database = this.openDatabase(null);
        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);
        final AccountHolder holder = new AccountHolder(repository);
        final AccountHolder reader = new AccountHolder(repository, InstanceMode.MULTI_INSTANCE);
        database.start();

        final Account account = this.createAccount("trae");
        holder.save(account);
        database.getBatchQueue().flush();

        reader.getById(account.getId());
        final String cacheKey = RedisNamespace.of(this.table).getKey(account.getId());
        assertTrue(this.redisDriver.getCommands().exists(cacheKey) > 0L);

        holder.save(account);
        database.getBatchQueue().flush();

        assertTrue(this.redisDriver.getCommands().exists(cacheKey) > 0L);

        account.setSession("transient");
        holder.save(account);
        database.getBatchQueue().flush();

        assertTrue(this.redisDriver.getCommands().exists(cacheKey) > 0L);
    }

    @Test
    void committedWriteRemainsInOutboxUntilRedisRecovers() throws InterruptedException {
        final CoreDatabase database = this.openDatabase(null);
        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);
        final AccountHolder holder = new AccountHolder(repository);
        final List<DatabaseException> failureList = new CopyOnWriteArrayList<>();
        database.setFailureHandler(failureList::add);
        database.start();

        final Account account = this.createAccount("trae");
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

        final Account account = this.createAccount("trae");
        holder.save(account);
        this.postgresDriver.getDslContext().dropTableIfExists(DSL.name("foundation_cache_invalidation_outbox")).execute();
        database.getBatchQueue().flush();

        assertTrue(repository.findById(account.getId()).isEmpty());
        assertFalse(failureList.isEmpty());

        database.getBatchQueue().synchronizeOutbox();
    }

    @Test
    void entityLookupsFallBackToPostgresWhenRedisIsUnavailable() {
        final CoreDatabase database = this.openDatabase(null);
        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);
        final AccountHolder writer = new AccountHolder(repository);
        database.start();

        final Account account = this.createAccount("trae");
        writer.save(account);
        database.getBatchQueue().flush();
        this.redisDriver.disconnect();

        try {
            final AccountHolder idReader = new AccountHolder(repository, InstanceMode.MULTI_INSTANCE);
            assertEquals("trae", idReader.getById(account.getId()).orElseThrow().getName());

            final AccountHolder propertyReader = new AccountHolder(repository, InstanceMode.MULTI_INSTANCE);
            assertEquals(account.getId(), propertyReader.getByProperty(NAME, "trae").orElseThrow().getId());
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

        final Account account = this.createAccount("trae");
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
    void uniqueValueHeldByAnotherInstanceIsRejected() {
        final CoreDatabase first = this.openDatabase(null);
        final AccountHolder firstHolder = new AccountHolder(new AccountRepository(first, this.table, TenantScope.NONE));
        first.start();

        final CoreDatabase second = this.openDatabase(null);
        final AccountHolder secondHolder = new AccountHolder(new AccountRepository(second, this.table, TenantScope.NONE));
        second.start();

        firstHolder.save(this.createAccount("trae"));
        first.getBatchQueue().flush();

        assertThrows(UniqueValueTakenException.class, () -> secondHolder.save(this.createAccount("trae")));
    }

    @Test
    void claimInProgressOnAnotherInstanceIsNotStolen() {
        final CoreDatabase database = this.openDatabase(null);
        final AccountHolder holder = new AccountHolder(new AccountRepository(database, this.table, TenantScope.NONE));
        database.start();

        final String key = new RedisPropertyIndex<>(this.redisDriver, RedisNamespace.of(this.table), NAME).getKey("trae");
        ClaimScripts.claim(this.redisDriver, key, UUID.randomUUID().toString(), Duration.ofSeconds(30));

        assertThrows(UniqueValueTakenException.class, () -> holder.save(this.createAccount("trae")));
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

        lobbyOneHolder.save(this.createAccount("trae"));
        lobbyTwoHolder.save(this.createAccount("trae"));
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

        final Account account = this.createAccount("trae");
        holder.save(account);
        database.getBatchQueue().flush();

        holder.delete(account);
        database.getBatchQueue().flush();

        assertTrue(repository.findById(account.getId()).isEmpty());
        assertTrue(holder.getById(account.getId()).isEmpty());
        holder.save(this.createAccount("trae"));
    }

    @Test
    void findsByUniquePropertyFromAnotherInstance() {
        final CoreDatabase first = this.openDatabase(null);
        final AccountHolder firstHolder = new AccountHolder(new AccountRepository(first, this.table, TenantScope.NONE));
        first.start();

        final CoreDatabase second = this.openDatabase(null);
        final AccountHolder secondHolder = new AccountHolder(new AccountRepository(second, this.table, TenantScope.NONE));
        second.start();

        final Account account = this.createAccount("trae");
        firstHolder.save(account);
        first.getBatchQueue().flush();

        assertEquals(account.getId(), secondHolder.getByProperty(NAME, "trae").orElseThrow().getId());
        assertTrue(secondHolder.getByProperty(NAME, "nobody").isEmpty());
    }

    @Test
    void flushWritesChangesMadeToCachedEntities() {
        final CoreDatabase database = this.openDatabase(null);
        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);
        final AccountHolder holder = new AccountHolder(repository);
        database.start();

        final Account account = this.createAccount("trae");
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

        final Account account = this.createAccount("trae");
        holder.save(account);
        holder.pin(account);
        assertEquals(List.of(account), holder.getPinned());

        account.setCoins(999L);
        holder.unpin(account);
        database.getBatchQueue().flush();

        assertTrue(holder.getPinned().isEmpty());
        assertEquals(999L, repository.findById(account.getId()).orElseThrow().getCoins());
    }

    @Test
    void multiInstanceHolderCachesCopiesLocallyAndCannotPinThem() {
        final CoreDatabase database = this.openDatabase(null);
        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);
        final AccountHolder writer = new AccountHolder(repository);
        final AccountHolder reader = new AccountHolder(repository, InstanceMode.MULTI_INSTANCE);
        database.start();

        final Account account = this.createAccount("trae");
        writer.save(account);
        database.getBatchQueue().flush();

        final Account first = reader.getById(account.getId()).orElseThrow();
        assertSame(first, reader.getById(account.getId()).orElseThrow());
        assertTrue(reader.getPinned().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> reader.pin(first));
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

        final CoreDatabase database = new CoreDatabase(this.postgresDriver, null, null, Duration.ofHours(1), 500);
        this.databaseList.add(database);

        final List<DatabaseException> failureList = new CopyOnWriteArrayList<>();
        database.setFailureHandler(failureList::add);

        final AccountRepository repository = new AccountRepository(database, this.table, TenantScope.NONE);
        final AccountHolder holder = new AccountHolder(repository);
        database.start();

        holder.save(this.createAccount("trae"));
        holder.save(this.createAccount("trae"));
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
        final AccountHolder holder = new AccountHolder(repository, InstanceMode.SINGLETON, Duration.ZERO);
        database.start();

        final Account account = this.createAccount("trae");
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

        final Account account = this.createAccount("trae");
        holder.save(account);
        database.getBatchQueue().flush();

        assertEquals("trae", repository.findById(account.getId()).orElseThrow().getName());
    }

    @AfterEach
    void cleanUp() {
        this.databaseList.forEach(CoreDatabase::stop);

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

        final CoreDatabase coreDatabase = new CoreDatabase(this.postgresDriver, this.redisDriver, tenant, Duration.ofHours(1), 500);
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
    private static final class Account implements RevisionedEntity {

        private final UUID id;
        private String name;
        private Long coins;
        private Rank rank;
        private List<String> tags;
        private Instant createdAt;
        private String session;
        private long revision;
    }

    @RequiredArgsConstructor
    @Getter
    @Setter
    private static final class Reserved implements Entity {

        private final UUID id;
        private String label;
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

    private static final class AccountHolder extends AbstractEntityHolder<Account> {

        private AccountHolder(final AccountRepository repository, final InstanceMode instanceMode, final Duration localExpiry) {
            super(repository, instanceMode, localExpiry, Duration.ofHours(1));
        }

        private AccountHolder(final AccountRepository repository, final InstanceMode instanceMode) {
            this(repository, instanceMode, Duration.ofMinutes(30));
        }

        private AccountHolder(final AccountRepository repository) {
            this(repository, InstanceMode.SINGLETON);
        }
    }
}