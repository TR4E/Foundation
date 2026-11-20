package me.trae.foundation.database.verification;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.declaration.DeclarationLoader;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.QueryException;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.index.GroupedIndex;
import me.trae.foundation.database.api.index.UniqueIndex;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.property.PropertyRegistry;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.storage.StorageProvider;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.change.Change;
import me.trae.foundation.database.api.write.change.ChangeType;
import me.trae.foundation.database.batch.BatchQueue;
import me.trae.foundation.database.batch.BatchScheduler;
import me.trae.foundation.database.batch.ChangeMerge;
import me.trae.foundation.database.index.GroupedIndexStore;
import me.trae.foundation.database.index.IndexStores;
import me.trae.foundation.database.index.IndexUpdater;
import me.trae.foundation.database.index.UniqueIndexStore;
import me.trae.foundation.database.index.exception.IndexException;
import me.trae.foundation.database.local.LocalStorage;
import me.trae.foundation.database.local.LocalStorageProvider;
import me.trae.foundation.database.postgres.driver.PostgresDriver;
import me.trae.foundation.database.postgres.index.PostgresIndexType;
import me.trae.foundation.database.postgres.query.PostgresQuery;
import me.trae.foundation.database.postgres.repository.PostgresRepository;
import me.trae.foundation.database.postgres.schema.SchemaDefinition;
import me.trae.foundation.database.postgres.write.PostgresWrite;
import me.trae.foundation.database.tenant.Tenant;
import me.trae.foundation.database.tenant.TenantScope;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static me.trae.foundation.database.verification.DatabaseFixture.Account;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountDeclaration;
import static me.trae.foundation.database.verification.DatabaseFixture.account;
import static me.trae.foundation.database.verification.DatabaseFixture.increment;
import static me.trae.foundation.database.verification.DatabaseFixture.indexNamespace;
import static me.trae.foundation.database.verification.DatabaseFixture.namespace;
import static me.trae.foundation.database.verification.DatabaseFixture.set;
import static me.trae.foundation.database.verification.DatabaseFixture.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class ContractsTest {

    @BeforeAll
    static void declarations() {
        DeclarationLoader.load(AccountDeclaration.class);
    }

    @Test
    void propertiesAreRegisteredPerEntityType() {
        assertEquals(3, PropertyRegistry.getProperties(Account.class).size());
        assertTrue(PropertyRegistry.getProperties(Unregistered.class).isEmpty());
    }

    @Test
    void declarationLoaderForcesInitialization() {
        assertFalse(Initialization.INITIALIZED.get());
        DeclarationLoader.load(LazyDeclaration.class);
        assertTrue(Initialization.INITIALIZED.get());
    }

    @Test
    void repositoryRejectsZeroProperties() {
        assertThrows(SchemaException.class, () -> new EmptyRepository(mock(PostgresDriver.class)));
    }

    @Test
    void localNamespacesAreIsolated() {
        final LocalStorage<UUID, Account> firstStorage = new LocalStorage<>(namespace("first"), 0, Duration.ZERO);
        final LocalStorage<UUID, Account> secondStorage = new LocalStorage<>(namespace("second"), 0, Duration.ZERO);
        final Account account = account();

        firstStorage.put(account.getId(), account);

        assertTrue(secondStorage.get(account.getId()).isEmpty());
        assertEquals(List.of(account), List.copyOf(firstStorage.values()));
    }

    @Test
    void localRemoveRemovesValue() {
        final LocalStorage<UUID, Account> localStorage = new LocalStorage<>(namespace("remove"), 0, Duration.ZERO);
        final Account account = account();

        localStorage.put(account.getId(), account);
        localStorage.remove(account.getId());

        assertFalse(localStorage.contains(account.getId()));
        assertTrue(localStorage.values().isEmpty());
    }

    @Test
    void uniqueRenameReplacesOldKey() {
        final UniqueIndexStore uniqueIndexStore = new UniqueIndexStore(AccountDeclaration.NAME_INDEX, List.of(new LocalStorage<>(indexNamespace("name"), 0, Duration.ZERO)));
        final Account account = account();
        final String previous = account.getName();

        uniqueIndexStore.index(account);
        assertEquals(account.getId(), uniqueIndexStore.lookup(previous));

        account.setName("renamed");
        uniqueIndexStore.index(account);

        assertNull(uniqueIndexStore.lookup(previous));
        assertEquals(account.getId(), uniqueIndexStore.lookup("renamed"));
    }

    @Test
    void groupedAddAndRemove() {
        final GroupedIndexStore groupedIndexStore = new GroupedIndexStore(AccountDeclaration.GROUP_INDEX);
        final Account account = account();

        groupedIndexStore.index(account);
        assertEquals(List.of(account.getId()), groupedIndexStore.lookup("group"));

        account.setGroup("other");
        groupedIndexStore.index(account);

        assertTrue(groupedIndexStore.lookup("group").isEmpty());
        assertEquals(List.of(account.getId()), groupedIndexStore.lookup("other"));

        groupedIndexStore.unindex(account.getId());
        assertTrue(groupedIndexStore.lookup("other").isEmpty());
    }

    @Test
    void groupedRedisPlacementIsRejected() {
        GroupedIndex.of("unsupported", OtherDeclaration.NAME);

        assertThrows(
                IndexException.class,
                () -> new IndexStores(Other.class, Set.of(Tier.REDIS, Tier.POSTGRES), List.of(), index -> indexNamespace(index.getName()))
        );
    }

    @Test
    void indexTiersInheritHolderSetWithoutPostgres() {
        final StorageProvider localProvider = spy(new LocalStorageProvider());
        final StorageProvider redisProvider = mock(StorageProvider.class);
        final StorageProvider postgresProvider = mock(StorageProvider.class);

        when(redisProvider.getTier()).thenReturn(Tier.REDIS);
        when(redisProvider.create(any())).thenAnswer(invocation -> new LocalStorage<>(invocation.getArgument(0), 0, Duration.ZERO));
        when(postgresProvider.getTier()).thenReturn(Tier.POSTGRES);
        new IndexStores(Account.class, Tier.ALL, List.of(localProvider, redisProvider, postgresProvider), index -> indexNamespace(index.getName()));

        verify(localProvider).create(any());
        verify(redisProvider).create(any());
        verify(postgresProvider, never()).create(any());
    }

    @Test
    void derivedIndexOnlyDiffsDeclaredDependencies() {
        final AtomicInteger extractionCount = new AtomicInteger();
        final UniqueIndex<Derived, String> uniqueIndex = UniqueIndex.derived(
                "derived_name",
                Derived.class,
                String.class,
                derived -> {
                    extractionCount.incrementAndGet();

                    return derived.getName().toLowerCase(java.util.Locale.ROOT);
                },
                Set.of(DerivedDeclaration.NAME)
        );
        final IndexStores indexStores = new IndexStores(
                Derived.class,
                Set.of(Tier.LOCAL),
                List.of(new LocalStorageProvider()),
                index -> indexNamespace(index.getName())
        );
        final IndexUpdater indexUpdater = new IndexUpdater(indexStores);
        final Derived derived = new Derived(UUID.randomUUID());

        derived.setName("ALPHA");
        indexUpdater.index(derived);

        derived.setName("BETA");
        indexUpdater.index(derived, DerivedDeclaration.OTHER);

        assertEquals(1, extractionCount.get());
        assertNotNull(indexStores.unique(uniqueIndex).lookup("alpha"));

        indexUpdater.index(derived, DerivedDeclaration.NAME);

        assertEquals(2, extractionCount.get());
        assertNull(indexStores.unique(uniqueIndex).lookup("alpha"));
        assertEquals(derived.getId(), indexStores.unique(uniqueIndex).lookup("beta"));
    }

    @Test
    void btreeRendersAsOrdinaryIndex() {
        assertTrue(this.ddl(PostgresIndexType.BTREE, false).contains("CREATE INDEX IF NOT EXISTS idx_accounts_name ON accounts (name)"));
    }

    @Test
    void uniqueRendersUniqueIndex() {
        assertTrue(this.ddl(PostgresIndexType.UNIQUE, false).contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_accounts_name"));
    }

    @Test
    void brinRendersUsingBrin() {
        assertTrue(
                this.ddl(PostgresIndexType.BRIN, false).toLowerCase(java.util.Locale.ROOT).contains("using brin"),
                "BRIN declaration must render USING brin"
        );
    }

    @Test
    void tenantColumnAppearsOnlyWhenConfigured() {
        assertFalse(this.ddl(PostgresIndexType.BTREE, false).contains("tenant"));
        assertTrue(this.ddl(PostgresIndexType.UNIQUE, true).contains("tenant text"));
        assertTrue(this.ddl(PostgresIndexType.UNIQUE, true).contains("(tenant, name)"));
    }

    @Test
    void tenantPredicateAppearsOnlyWhenConfigured() {
        final List<Property<?, ?>> propertyList = PropertyRegistry.getProperties(Account.class);

        assertTrue(PostgresQuery.conditions(Query.all(), null, propertyList).isEmpty());
        assertEquals(1, PostgresQuery.conditions(Query.all(), "shop", propertyList).size());
        assertTrue(PostgresQuery.conditions(Query.all(), "shop", propertyList).getFirst().toString().contains("shop"));
    }

    @Test
    void nullDefaultTenantMustReturnEmpty() {
        assertTrue(new TenantScope(null).getTenant().isEmpty());
    }

    @Test
    void tenantScopedOverrideIsRestored() {
        final TenantScope tenantScope = new TenantScope(Tenant.of("first"));

        assertEquals("second", tenantScope.call(Tenant.of("second"), () -> tenantScope.getTenant().orElseThrow().getName()));
        assertEquals("first", tenantScope.getTenant().orElseThrow().getName());
    }

    @Test
    void postgresRejectsAdd() {
        assertThrows(
                QueryException.class,
                () -> PostgresWrite.apply(
                        DSL.using(SQLDialect.POSTGRES),
                        DSL.table("accounts"),
                        PropertyRegistry.getProperties(Account.class),
                        write(UUID.randomUUID(), WriteKind.UPDATE, 0L, new Change("name", ChangeType.ADD, "item"))
                )
        );
    }

    @Test
    void postgresRejectsRemove() {
        assertThrows(
                QueryException.class,
                () -> PostgresWrite.apply(
                        DSL.using(SQLDialect.POSTGRES),
                        DSL.table("accounts"),
                        PropertyRegistry.getProperties(Account.class),
                        write(UUID.randomUUID(), WriteKind.UPDATE, 0L, new Change("name", ChangeType.REMOVE, "item"))
                )
        );
    }

    @Test
    void batchSetOverwrites() {
        assertEquals("second", ChangeMerge.merge(List.of(set("name", "first")), List.of(set("name", "second"))).getFirst().getValue());
    }

    @Test
    void batchIncrementSums() {
        assertEquals(7L, ChangeMerge.merge(List.of(increment(3L)), List.of(increment(4L))).getFirst().getValue());
    }

    @Test
    void deleteWinsOverPriorUpdate() {
        final BatchQueue batchQueue = new BatchQueue(0);
        final UUID id = UUID.randomUUID();

        batchQueue.add(write(id, WriteKind.UPDATE, 0L, increment(1L)));
        batchQueue.add(write(id, WriteKind.DELETE, 0L));

        assertEquals(WriteKind.DELETE, batchQueue.drain().getFirst().getKind());
        assertEquals(0, batchQueue.size());
    }

    @Test
    void deleteMustNotBeResurrectedByLaterUpdate() {
        final BatchQueue batchQueue = new BatchQueue(0);
        final UUID id = UUID.randomUUID();

        batchQueue.add(write(id, WriteKind.DELETE, 0L));
        batchQueue.add(write(id, WriteKind.UPDATE, 0L, increment(1L)));

        assertEquals(WriteKind.DELETE, batchQueue.drain().getFirst().getKind(), "DELETE must win over a queued UPDATE");
    }

    @Test
    void queuedSaveMustSurviveSubsequentUpdate() {
        final BatchQueue batchQueue = new BatchQueue(0);
        final UUID id = UUID.randomUUID();

        batchQueue.add(write(id, WriteKind.SAVE, 0L, set("name", "created")));
        batchQueue.add(write(id, WriteKind.UPDATE, 0L, increment(1L)));

        assertEquals(WriteKind.SAVE, batchQueue.drain().getFirst().getKind(), "UPDATE must not discard a pending insert");
    }

    @Test
    void flushDrainsAndCallsWriter() {
        final BatchQueue batchQueue = new BatchQueue(0);
        final AtomicInteger writeCount = new AtomicInteger();
        final BatchScheduler batchScheduler = new BatchScheduler(batchQueue, writes -> writeCount.addAndGet(writes.size()), Duration.ofSeconds(1));

        batchQueue.add(write(UUID.randomUUID(), WriteKind.UPDATE, 0L, increment(1L)));

        try {
            batchScheduler.flush();

            assertEquals(1, writeCount.get());
            assertEquals(0, batchQueue.size());
        } finally {
            batchScheduler.stop();
        }
    }

    @Test
    void failedFlushRequeues() {
        final BatchQueue batchQueue = new BatchQueue(0);
        final BatchScheduler batchScheduler = new BatchScheduler(batchQueue, _ -> {
            throw new IllegalStateException("offline");
        }, Duration.ofSeconds(1));

        batchQueue.add(write(UUID.randomUUID(), WriteKind.UPDATE, 0L, increment(1L)));

        try {
            batchScheduler.flush();
            assertEquals(1, batchQueue.size());
        } finally {
            batchScheduler.stop();
        }
    }

    private String ddl(final PostgresIndexType postgresIndexType, final boolean tenanted) {
        return String.join(
                "\n",
                SchemaDefinition.statements(
                        "accounts",
                        tenanted,
                        PropertyRegistry.getProperties(Account.class),
                        Map.of(AccountDeclaration.NAME, postgresIndexType)
                )
        );
    }

    @UtilityClass
    private static final class Initialization {

        private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    }

    @UtilityClass
    private static final class LazyDeclaration {

        static {
            Initialization.INITIALIZED.set(true);
        }
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    private static final class Unregistered implements Entity {

        @EqualsAndHashCode.Include
        private final UUID id;
    }

    private static final class EmptyRepository extends PostgresRepository<Unregistered> {

        private EmptyRepository(final PostgresDriver postgresDriver) {
            super(Unregistered.class, "empty", postgresDriver, new TenantScope(null));
        }
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    @Setter
    private static final class Other implements Entity {

        @EqualsAndHashCode.Include
        private final UUID id;

        private String name;
    }

    @UtilityClass
    private static final class OtherDeclaration {

        private static final Property<Other, String> NAME = Property.persistent(Other.class, "name", String.class, Other::getName, Other::setName);
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    @Setter
    private static final class Derived implements Entity {

        @EqualsAndHashCode.Include
        private final UUID id;

        private String name, other;
    }

    @UtilityClass
    private static final class DerivedDeclaration {

        private static final Property<Derived, String> NAME = Property.persistent(Derived.class, "name", String.class, Derived::getName, Derived::setName);
        private static final Property<Derived, String> OTHER = Property.persistent(Derived.class, "other", String.class, Derived::getOther, Derived::setOther);
    }
}