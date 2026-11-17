package me.trae.foundation.database.verification;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.VersionConflictException;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.property.PropertyRegistry;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.change.Change;
import me.trae.foundation.database.holder.context.HolderContext;
import me.trae.foundation.database.holder.context.HolderContextFactory;
import me.trae.foundation.database.local.LocalStorage;
import me.trae.foundation.database.local.LocalStorageProvider;
import me.trae.foundation.database.postgres.schema.PostgresSynchronizer;
import me.trae.foundation.database.postgres.schema.SchemaDefinition;
import me.trae.foundation.database.redis.driver.RedisDriver;
import me.trae.foundation.database.redis.driver.RedisSettings;
import me.trae.foundation.database.redis.event.RedisEvents;
import me.trae.foundation.database.redis.marker.RedisMarker;
import me.trae.foundation.database.redis.storage.RedisEntityStorage;
import me.trae.foundation.database.redis.storage.RedisStorageProvider;
import me.trae.foundation.database.tenant.Tenant;
import me.trae.foundation.database.tenant.TenantScope;
import me.trae.foundation.injector.core.CoreInjector;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static me.trae.foundation.database.verification.DatabaseFixture.Account;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountDeclaration;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountRepository;
import static me.trae.foundation.database.verification.DatabaseFixture.AllBean;
import static me.trae.foundation.database.verification.DatabaseFixture.ContextFactory;
import static me.trae.foundation.database.verification.DatabaseFixture.LocalBean;
import static me.trae.foundation.database.verification.DatabaseFixture.RedisBean;
import static me.trae.foundation.database.verification.DatabaseFixture.TestApplication;
import static me.trae.foundation.database.verification.DatabaseFixture.account;
import static me.trae.foundation.database.verification.DatabaseFixture.context;
import static me.trae.foundation.database.verification.DatabaseFixture.increment;
import static me.trae.foundation.database.verification.DatabaseFixture.lookup;
import static me.trae.foundation.database.verification.DatabaseFixture.namespace;
import static me.trae.foundation.database.verification.DatabaseFixture.set;
import static me.trae.foundation.database.verification.DatabaseFixture.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public final class ServicesTest extends ServiceFixture {

    @Test
    void synchronizeTwiceIsIdempotent() {
        final PostgresSynchronizer postgresSynchronizer = new PostgresSynchronizer(postgresDriver);

        postgresSynchronizer.synchronize(accountRepository.getStatements());
        postgresSynchronizer.synchronize(accountRepository.getStatements());

        assertEquals(
                1,
                postgresDriver.getDslContext().fetchCount(
                        org.jooq.impl.DSL.table("information_schema.tables"),
                        org.jooq.impl.DSL.field("table_name").eq("accounts")
                )
        );
    }

    @Test
    void newPropertyAddsColumn() {
        final Property<Migration, String> initialProperty = Property.persistent(Migration.class, "name", String.class, Migration::getName, Migration::setName);
        final PostgresSynchronizer postgresSynchronizer = new PostgresSynchronizer(postgresDriver);

        postgresSynchronizer.synchronize(SchemaDefinition.statements("migration", false, List.of(initialProperty), Map.of()));

        final Property<Migration, String> addedProperty = Property.persistent(Migration.class, "added", String.class, Migration::getAdded, Migration::setAdded);

        postgresSynchronizer.synchronize(SchemaDefinition.statements("migration", false, List.of(initialProperty, addedProperty), Map.of()));

        assertEquals(
                1,
                postgresDriver.getDslContext().fetchCount(
                        org.jooq.impl.DSL.table("information_schema.columns"),
                        org.jooq.impl.DSL.field("table_name").eq("migration").and(org.jooq.impl.DSL.field("column_name").eq("added"))
                )
        );
    }

    @Test
    void updateBumpsVersion() {
        final Account account = this.persistedAccount();

        assertEquals(1L, accountRepository.apply(write(account.getId(), WriteKind.UPDATE, 0L, set("name", "updated"))));
        assertEquals(2L, accountRepository.apply(write(account.getId(), WriteKind.UPDATE, 1L, increment(2L))));
        assertEquals(2L, accountRepository.findById(account.getId()).orElseThrow().getCounter());
    }

    @Test
    void staleExpectedVersionThrows() {
        final Account account = this.persistedAccount();

        accountRepository.apply(write(account.getId(), WriteKind.UPDATE, 0L, set("name", "first")));
        accountRepository.apply(write(account.getId(), WriteKind.UPDATE, 1L, set("name", "second")));

        assertThrows(VersionConflictException.class, () -> accountRepository.apply(write(account.getId(), WriteKind.UPDATE, 1L, set("name", "stale"))));
    }

    @Test
    void saveUpsertsWithoutDuplicateRow() {
        final Account account = this.persistedAccount();

        accountRepository.apply(write(account.getId(), WriteKind.SAVE, 0L, set("name", "upserted")));

        assertEquals(1L, accountRepository.count(Query.all()));
        assertEquals("upserted", accountRepository.findById(account.getId()).orElseThrow().getName());
    }

    @Test
    void sqlUniqueIndexRejectsDuplicateValue() {
        final Account account = this.persistedAccount();

        assertThrows(
                org.jooq.exception.DataAccessException.class,
                () -> accountRepository.apply(write(UUID.randomUUID(), WriteKind.SAVE, 0L, set("name", account.getName())))
        );
    }

    @Test
    void tenantPredicateFiltersOnlyWhenTenantPresent() {
        new PostgresSynchronizer(postgresDriver).synchronize(SchemaDefinition.statements(
                "accounts",
                true,
                PropertyRegistry.getProperties(Account.class),
                Map.of()
        ));

        final UUID id = UUID.randomUUID();

        accountRepository.apply(new Write(new EntityKey("accounts", "alpha", id), WriteKind.SAVE, List.of(set("name", "tenant-account")), 0L));

        final AccountRepository alphaRepository = new AccountRepository(postgresDriver, new TenantScope(Tenant.of("alpha")));
        final AccountRepository betaRepository = new AccountRepository(postgresDriver, new TenantScope(Tenant.of("beta")));

        assertTrue(alphaRepository.findById(id).isPresent());
        assertTrue(betaRepository.findById(id).isEmpty());
        assertTrue(accountRepository.findById(id).isPresent());
    }

    @Test
    void redisHashHasOneFieldPerProperty() {
        final Account account = account();
        final RedisEntityStorage<Account> redisEntityStorage = this.entityStorage();

        redisEntityStorage.put(account.getId(), account);

        assertEquals(Set.of("name", "account_group", "counter"), redisDriver.getCommands().hgetall(redisEntityStorage.path(account.getId())).keySet());
        assertEquals(account.getName(), redisEntityStorage.get(account.getId()).orElseThrow().getName());
    }

    @Test
    void scriptIncrementsVersionAndSetsTtl() {
        final Account account = account();
        final RedisEntityStorage<Account> redisEntityStorage = this.entityStorage();

        redisEntityStorage.put(account.getId(), account);

        assertEquals(
                1L,
                this.entityWriter().apply(
                        redisEntityStorage.path(account.getId()),
                        redisEntityStorage.channel(account.getId()),
                        write(account.getId(), WriteKind.UPDATE, 0L, increment(1L)),
                        Duration.ofSeconds(10)
                )
        );
        assertEquals(
                2L,
                this.entityWriter().apply(
                        redisEntityStorage.path(account.getId()),
                        redisEntityStorage.channel(account.getId()),
                        write(account.getId(), WriteKind.UPDATE, 1L, increment(1L)),
                        Duration.ofSeconds(10)
                )
        );
        assertEquals("2", redisDriver.getCommands().hget(redisEntityStorage.path(account.getId()), "version"));
        assertTrue(redisDriver.getCommands().pttl(redisEntityStorage.path(account.getId())) > 8000L);
    }

    @Test
    void refreshRestoresTtl() {
        final Account account = account();
        final RedisEntityStorage<Account> redisEntityStorage = this.entityStorage();

        redisEntityStorage.put(account.getId(), account);
        redisDriver.getCommands().pexpire(redisEntityStorage.path(account.getId()), 1000L);
        redisEntityStorage.refresh(account.getId());

        assertTrue(redisDriver.getCommands().pttl(redisEntityStorage.path(account.getId())) > 8000L);
    }

    @Test
    void missingHashScriptReturnsZero() {
        final Account account = account();
        final RedisEntityStorage<Account> redisEntityStorage = this.entityStorage();

        assertEquals(
                0L,
                this.entityWriter().apply(
                        redisEntityStorage.path(account.getId()),
                        redisEntityStorage.channel(account.getId()),
                        write(account.getId(), WriteKind.UPDATE, 0L, increment(1L)),
                        Duration.ofSeconds(10)
                )
        );
        assertFalse(redisEntityStorage.contains(account.getId()));
    }

    @Test
    void redisMinusOneBecomesVersionConflict() {
        final Account account = account();
        final RedisEntityStorage<Account> redisEntityStorage = this.entityStorage();

        redisEntityStorage.put(account.getId(), account);
        redisDriver.getCommands().hset(redisEntityStorage.path(account.getId()), "version", "2");

        assertThrows(
                VersionConflictException.class,
                () -> this.entityWriter().apply(
                        redisEntityStorage.path(account.getId()),
                        redisEntityStorage.channel(account.getId()),
                        write(account.getId(), WriteKind.UPDATE, 1L, increment(1L)),
                        Duration.ofSeconds(10)
                )
        );
    }

    @Test
    void publishedPayloadDecodesToSameChanges() throws Exception {
        final Account account = account();
        final RedisEntityStorage<Account> redisEntityStorage = this.entityStorage();
        final CountDownLatch received = new CountDownLatch(1);
        final AtomicReference<String> payloadReference = new AtomicReference<>();

        redisEntityStorage.put(account.getId(), account);
        redisDriver.subscribe(redisEntityStorage.channel(account.getId()), payload -> {
            payloadReference.set(payload);
            received.countDown();
        });

        try {
            this.entityWriter().apply(
                    redisEntityStorage.path(account.getId()),
                    redisEntityStorage.channel(account.getId()),
                    write(account.getId(), WriteKind.UPDATE, 0L, set("name", "tab\tline\nvalue")),
                    Duration.ofSeconds(10)
            );
            assertTrue(received.await(5, TimeUnit.SECONDS));

            final var changeEvent = RedisEvents.decode(new EntityKey("accounts", null, account.getId()), payloadReference.get());

            assertEquals(1L, changeEvent.getVersion());
            assertEquals("name", changeEvent.getChanges().getFirst().getColumn());
            assertEquals(me.trae.foundation.database.api.write.change.ChangeType.SET, changeEvent.getChanges().getFirst().getType());
            assertEquals("tab\tline\nvalue", changeEvent.getChanges().getFirst().getValue());
        } finally {
            redisDriver.unsubscribe(redisEntityStorage.channel(account.getId()));
        }
    }

    @Test
    void emptyStringEventRoundTripsAsEmptyString() {
        final List<Change> changeList = List.of(set("name", ""));
        final var changeEvent = RedisEvents.decode(
                new EntityKey("accounts", null, UUID.randomUUID()),
                "1\nother-instance\n%s".formatted(RedisEvents.encode(changeList))
        );

        assertEquals("", changeEvent.getChanges().getFirst().getValue(), "An empty string SET must not become null");
    }

    @Test
    void twoThreadsIncrementOneCounterToExactSum() throws Exception {
        final Account account = account();
        final RedisEntityStorage<Account> redisEntityStorage = this.entityStorage();

        redisEntityStorage.put(account.getId(), account);

        try (final var executorService = Executors.newFixedThreadPool(2)) {
            final var firstFuture = executorService.submit(() -> java.util.stream.IntStream.range(
                    0,
                    500
            ).forEach(_ -> this.entityWriter().apply(
                    redisEntityStorage.path(account.getId()),
                    redisEntityStorage.channel(account.getId()),
                    write(account.getId(), WriteKind.UPDATE, 0L, increment(1L)),
                    Duration.ofSeconds(10)
            )));
            final var secondFuture = executorService.submit(() -> java.util.stream.IntStream.range(
                    0,
                    500
            ).forEach(_ -> this.entityWriter().apply(
                    redisEntityStorage.path(account.getId()),
                    redisEntityStorage.channel(account.getId()),
                    write(account.getId(), WriteKind.UPDATE, 0L, increment(1L)),
                    Duration.ofSeconds(10)
            )));

            firstFuture.get(20, TimeUnit.SECONDS);
            secondFuture.get(20, TimeUnit.SECONDS);
        }

        assertEquals("1000", redisDriver.getCommands().hget(redisEntityStorage.path(account.getId()), "counter"));
        assertEquals("1000", redisDriver.getCommands().hget(redisEntityStorage.path(account.getId()), "version"));
    }

    @Test
    void twoRedisBackedHoldersShareOnePostgresRead() throws Exception {
        final Account account = this.persistedAccount();
        final AccountRepository countedRepository = spy(accountRepository);
        final CountDownLatch waiting = new CountDownLatch(1);
        final RedisMarker redisMarker = spy(new RedisMarker(redisDriver));

        doAnswer(invocation -> {
            waiting.countDown();

            return invocation.callRealMethod();
        }).when(redisMarker).await(any(), any());
        doAnswer(invocation -> {
            assertTrue(waiting.await(5, TimeUnit.SECONDS));

            return invocation.callRealMethod();
        }).when(countedRepository).findById(account.getId());

        final LinkedHashMap<Tier, Storage<UUID, Account>> firstMap = new LinkedHashMap<>();
        final LinkedHashMap<Tier, Storage<UUID, Account>> secondMap = new LinkedHashMap<>();

        firstMap.put(Tier.REDIS, this.entityStorage());
        secondMap.put(Tier.REDIS, this.entityStorage());

        final ContextFactory firstFactory = new ContextFactory(tiers -> context(
                countedRepository,
                tiers,
                firstMap,
                redisMarker,
                countedRepository::apply,
                _ -> {},
                (_, _) -> {},
                _ -> {},
                List.of(new RedisStorageProvider(redisDriver))
        ));
        final ContextFactory secondFactory = new ContextFactory(tiers -> context(
                countedRepository,
                tiers,
                secondMap,
                redisMarker,
                countedRepository::apply,
                _ -> {},
                (_, _) -> {},
                _ -> {},
                List.of(new RedisStorageProvider(redisDriver))
        ));
        final CoreInjector firstInjector = new CoreInjector();
        final CoreInjector secondInjector = new CoreInjector();
        final TestApplication firstApplication = new TestApplication();
        final TestApplication secondApplication = new TestApplication();

        firstInjector.provide(AccountRepository.class, countedRepository);
        firstInjector.provide(HolderContextFactory.class, firstFactory);
        secondInjector.provide(AccountRepository.class, countedRepository);
        secondInjector.provide(HolderContextFactory.class, secondFactory);

        try {
            firstInjector.initialize(firstApplication, List.of(RedisBean.class));
            secondInjector.initialize(secondApplication, List.of(RedisBean.class));

            try (final var executorService = Executors.newFixedThreadPool(2)) {
                final var firstFuture = executorService.submit(() -> firstInjector.get(RedisBean.class).get(account.getId()));
                final var secondFuture = executorService.submit(() -> secondInjector.get(RedisBean.class).get(account.getId()));

                assertTrue(firstFuture.get(10, TimeUnit.SECONDS).isPresent());
                assertTrue(secondFuture.get(10, TimeUnit.SECONDS).isPresent());
            }

            verify(countedRepository, times(1)).findById(account.getId());
        } finally {
            firstInjector.shutdown(firstApplication);
            secondInjector.shutdown(secondApplication);
        }
    }

    @Test
    void realEmptyPayloadAvoidsSecondPostgresQuery() throws Exception {
        final UUID id = UUID.randomUUID();
        final AccountRepository countedRepository = spy(accountRepository);
        final RedisMarker redisMarker = spy(new RedisMarker(redisDriver));
        final CountDownLatch waiting = new CountDownLatch(1);

        doAnswer(invocation -> {
            waiting.countDown();

            return invocation.callRealMethod();
        }).when(redisMarker).await(any(), any());
        doAnswer(invocation -> {
            assertTrue(waiting.await(5, TimeUnit.SECONDS));

            return invocation.callRealMethod();
        }).when(countedRepository).findById(id);

        final var firstLookup = lookup(countedRepository, Set.of(Tier.POSTGRES), new LinkedHashMap<>(), redisMarker);
        final var secondLookup = lookup(countedRepository, Set.of(Tier.POSTGRES), new LinkedHashMap<>(), redisMarker);

        try (final var executorService = Executors.newFixedThreadPool(2)) {
            final var firstFuture = executorService.submit(() -> firstLookup.find(id));
            final var secondFuture = executorService.submit(() -> secondLookup.find(id));

            assertTrue(firstFuture.get(10, TimeUnit.SECONDS).isEmpty());
            assertTrue(secondFuture.get(10, TimeUnit.SECONDS).isEmpty());
        }

        verify(countedRepository, times(1)).findById(id);
    }

    @Test
    void allThreeDiTierConfigurationsRunAgainstSameServices() {
        final Account account = this.persistedAccount();
        final AccountRepository countedRepository = spy(accountRepository);
        final ContextFactory contextFactory = new ContextFactory(tiers -> {
            final LinkedHashMap<Tier, Storage<UUID, Account>> storageMap = new LinkedHashMap<>();
            final RedisEntityStorage<Account> redisEntityStorage = this.entityStorage();

            if (tiers.contains(Tier.LOCAL)) {
                storageMap.put(Tier.LOCAL, new LocalStorage<>(namespace("mixed-local")));
            }

            if (tiers.contains(Tier.REDIS)) {
                storageMap.put(Tier.REDIS, redisEntityStorage);
            }

            return context(
                    countedRepository,
                    tiers,
                    storageMap,
                    new RedisMarker(redisDriver),
                    tiers.contains(Tier.REDIS)
                            ? write -> this.entityWriter().apply(
                            redisEntityStorage.path(write.getKey().getId()),
                            redisEntityStorage.channel(write.getKey().getId()),
                            write,
                            Duration.ofSeconds(10)
                    )
                            : countedRepository::apply,
                    tiers.contains(Tier.REDIS) ? countedRepository::apply : _ -> {},
                    (_, _) -> {},
                    _ -> {},
                    List.of(new LocalStorageProvider(), new RedisStorageProvider(redisDriver))
            );
        });
        final CoreInjector coreInjector = new CoreInjector();
        final TestApplication testApplication = new TestApplication();

        coreInjector.provide(AccountRepository.class, countedRepository);
        coreInjector.provide(HolderContextFactory.class, contextFactory);

        try {
            coreInjector.initialize(testApplication, List.of(LocalBean.class, RedisBean.class, AllBean.class));

            final LocalBean localBean = coreInjector.get(LocalBean.class);
            final RedisBean redisBean = coreInjector.get(RedisBean.class);
            final AllBean allBean = coreInjector.get(AllBean.class);

            localBean.hold(account.getId());
            redisBean.hold(account.getId());
            allBean.hold(account.getId());

            assertTrue(localBean.holds(account.getId()));
            assertFalse(redisBean.holds(account.getId()));
            assertTrue(allBean.holds(account.getId()));
            assertFalse(localBean.getContext().getStorageMap().containsKey(Tier.REDIS));
            assertFalse(redisBean.getContext().getStorageMap().containsKey(Tier.LOCAL));
            assertEquals(Set.of(Tier.LOCAL, Tier.REDIS), allBean.getContext().getStorageMap().keySet());

            clearInvocations(countedRepository);
            localBean.set(localBean.get(account.getId()).orElseThrow(), AccountDeclaration.NAME, "local-change");

            verify(countedRepository, times(1)).apply(any());
            assertEquals(account.getName(), allBean.get(account.getId()).orElseThrow().getName());
        } finally {
            coreInjector.shutdown(testApplication);
        }
    }

    @Test
    void twoDiHoldersObserveWritesAndGapRefetchRepairsMissedEvent() throws Exception {
        final Account account = this.persistedAccount();
        final RedisDriver secondDriver = new RedisDriver(new RedisSettings(REDIS.getHost(), REDIS.getMappedPort(6379), null, 0, Duration.ofSeconds(5)));

        secondDriver.connect();

        try (final Node firstNode = new Node(redisDriver); final Node secondNode = new Node(secondDriver)) {
            firstNode.getAllBean().hold(account.getId());
            secondNode.getAllBean().hold(account.getId());

            final Account firstAccount = firstNode.getAllBean().get(account.getId()).orElseThrow();

            firstNode.getAllBean().set(firstAccount, AccountDeclaration.NAME, "visible");
            this.await(() -> secondNode.getAllBean().get(account.getId()).orElseThrow().getName().equals("visible"));
            assertEquals("visible", accountRepository.findById(account.getId()).orElseThrow().getName());

            secondDriver.unsubscribe(this.entityStorage().channel(account.getId()));
            firstNode.getAllBean().set(firstAccount, AccountDeclaration.NAME, "missed");

            assertEquals("visible", secondNode.getAllBean().get(account.getId()).orElseThrow().getName());

            secondDriver.subscribe(
                    this.entityStorage().channel(account.getId()),
                    payload -> secondNode.getAllBean().getApplier().apply(RedisEvents.decode(new EntityKey("accounts", null, account.getId()), payload))
            );
            firstNode.getAllBean().set(firstAccount, AccountDeclaration.NAME, "repaired");
            this.await(() -> secondNode.getAllBean().getHeld().stream().anyMatch(heldAccount -> heldAccount.getName().equals("repaired")));

            assertEquals("repaired", secondNode.getAllBean().get(account.getId()).orElseThrow().getName());
            assertTrue(secondNode.getAllBean().holds(account.getId()), "Gap refetch must restore the Local hold");
        } finally {
            secondDriver.disconnect();
        }
    }

    private void await(final BooleanSupplier booleanSupplier) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        while (!booleanSupplier.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }

        assertTrue(booleanSupplier.getAsBoolean(), "Event application did not converge within five seconds");
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    @Setter
    private static final class Migration implements Entity {

        @EqualsAndHashCode.Include
        private final UUID id;

        private String name, added;
    }

    @Getter
    public static final class Node implements AutoCloseable {

        private final CoreInjector coreInjector = new CoreInjector();
        private final TestApplication testApplication = new TestApplication();

        private final RedisDriver redisDriver;

        private final AccountRepository accountRepository;

        private final AllBean allBean;

        public Node(final RedisDriver redisDriver) {
            this.redisDriver = redisDriver;
            this.accountRepository = spy(ServiceFixture.accountRepository);
            this.coreInjector.provide(AccountRepository.class, this.accountRepository);
            this.coreInjector.provide(HolderContextFactory.class, new ContextFactory(this::create));
            this.coreInjector.initialize(this.testApplication, List.of(AllBean.class));
            this.allBean = this.coreInjector.get(AllBean.class);
        }

        private HolderContext<Account> create(final Set<Tier> tiers) {
            final LinkedHashMap<Tier, Storage<UUID, Account>> storageMap = new LinkedHashMap<>();
            final RedisEntityStorage<Account> redisEntityStorage = new RedisEntityStorage<>(
                    namespace("accounts"),
                    this.redisDriver,
                    new me.trae.foundation.database.redis.codec.EntityCodec<>(
                            new me.trae.foundation.database.api.entity.EntityInstantiator<>(Account.class),
                            PropertyRegistry.getProperties(Account.class)
                    )
            );

            storageMap.put(Tier.LOCAL, new LocalStorage<>(namespace("local")));
            storageMap.put(Tier.REDIS, redisEntityStorage);

            return context(
                    this.accountRepository,
                    tiers,
                    storageMap,
                    new RedisMarker(this.redisDriver),
                    write -> new me.trae.foundation.database.redis.write.RedisEntityWriter(
                            this.redisDriver,
                            PropertyRegistry.getProperties(Account.class)
                    ).apply(redisEntityStorage.path(write.getKey().getId()), redisEntityStorage.channel(write.getKey().getId()), write, Duration.ofSeconds(10)),
                    this.accountRepository::apply,
                    (
                            id,
                            listener
                    ) -> this.redisDriver.subscribe(redisEntityStorage.channel(id), payload -> {
                        if (!RedisEvents.origin(payload).equals(this.redisDriver.getInstanceId())) {
                            listener.accept(RedisEvents.decode(new EntityKey("accounts", null, id), payload));
                        }
                    }),
                    id -> this.redisDriver.unsubscribe(redisEntityStorage.channel(id)),
                    List.of(new LocalStorageProvider(), new RedisStorageProvider(this.redisDriver))
            );
        }

        @Override
        public void close() {
            this.coreInjector.shutdown(this.testApplication);
        }
    }
}