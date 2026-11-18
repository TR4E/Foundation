package me.trae.foundation.database.verification;

import me.trae.foundation.database.api.event.ChangeEvent;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.api.write.WriteResult;
import me.trae.foundation.database.holder.context.HolderContextFactory;
import me.trae.foundation.database.local.LocalMarker;
import me.trae.foundation.database.local.LocalStorageProvider;
import me.trae.foundation.database.lookup.Backfill;
import me.trae.foundation.database.lookup.EntityLookup;
import me.trae.foundation.injector.api.Injector;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static me.trae.foundation.database.verification.DatabaseFixture.Account;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountDeclaration;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountHolder;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountRepository;
import static me.trae.foundation.database.verification.DatabaseFixture.AllBean;
import static me.trae.foundation.database.verification.DatabaseFixture.ContextFactory;
import static me.trae.foundation.database.verification.DatabaseFixture.LocalBean;
import static me.trae.foundation.database.verification.DatabaseFixture.MemoryMarker;
import static me.trae.foundation.database.verification.DatabaseFixture.RedisBean;
import static me.trae.foundation.database.verification.DatabaseFixture.TestApplication;
import static me.trae.foundation.database.verification.DatabaseFixture.account;
import static me.trae.foundation.database.verification.DatabaseFixture.context;
import static me.trae.foundation.database.verification.DatabaseFixture.holder;
import static me.trae.foundation.database.verification.DatabaseFixture.increment;
import static me.trae.foundation.database.verification.DatabaseFixture.lookup;
import static me.trae.foundation.database.verification.DatabaseFixture.repository;
import static me.trae.foundation.database.verification.DatabaseFixture.set;
import static me.trae.foundation.database.verification.DatabaseFixture.storages;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public final class HolderLookupTest {

    @Test
    void localHitDoesNotTouchRedisOrRepository() {
        final AccountRepository accountRepository = repository();
        final LinkedHashMap<Tier, Storage<UUID, Account>> storageMap = storages(Tier.ALL);
        final Storage<UUID, Account> redisStorage = spy(storageMap.get(Tier.REDIS));
        final Account account = account();

        storageMap.put(Tier.REDIS, redisStorage);
        storageMap.get(Tier.LOCAL).put(account.getId(), account);

        assertSame(account, lookup(accountRepository, Tier.ALL, storageMap, new LocalMarker()).find(account.getId()).orElseThrow());
        verifyNoInteractions(redisStorage);
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void redisHitDoesNotTouchRepository() {
        final AccountRepository accountRepository = repository();
        final LinkedHashMap<Tier, Storage<UUID, Account>> storageMap = storages(Tier.ALL);
        final Account account = account();

        storageMap.get(Tier.REDIS).put(account.getId(), account);

        assertSame(account, lookup(accountRepository, Tier.ALL, storageMap, new LocalMarker()).find(account.getId()).orElseThrow());
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void postgresReadBackfillsRedisButDoesNotCreateLocalHold() {
        final AccountRepository accountRepository = repository();
        final LinkedHashMap<Tier, Storage<UUID, Account>> storageMap = storages(Tier.ALL);
        final Account account = account();

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertEquals(account, lookup(accountRepository, Tier.ALL, storageMap, new LocalMarker()).find(account.getId()).orElseThrow());
        assertTrue(storageMap.get(Tier.REDIS).contains(account.getId()));
        assertFalse(storageMap.get(Tier.LOCAL).contains(account.getId()));
    }

    @Test
    void backfillUpdatesAlreadyHeldLocalObject() {
        final LinkedHashMap<Tier, Storage<UUID, Account>> storageMap = storages(Tier.ALL);
        final Account account = account();

        new Backfill<>(storageMap, _ -> true).apply(account, Tier.ALL);

        assertTrue(storageMap.get(Tier.LOCAL).contains(account.getId()));
        assertTrue(storageMap.get(Tier.REDIS).contains(account.getId()));
    }

    @Test
    void thousandConcurrentGetsMakeOneRepositoryRead() throws Exception {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final EntityLookup<Account> entityLookup = lookup(accountRepository, Tier.ALL, storages(Tier.ALL), new LocalMarker());

        when(accountRepository.findById(account.getId())).thenAnswer(_ -> {
            assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));

            return Optional.of(account);
        });

        try (final var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            final List<java.util.concurrent.Future<Optional<Account>>> futureList = IntStream.range(0, 1000)
                    .mapToObj(_ -> executorService.submit(() -> entityLookup.find(account.getId())))
                    .toList();

            countDownLatch.countDown();

            for (final var future : futureList) {
                assertEquals(account, future.get(10, TimeUnit.SECONDS).orElseThrow());
            }
        }

        verify(accountRepository, times(1)).findById(account.getId());
    }

    @Test
    void sharedMarkerEmptyPayloadAvoidsSecondQuery() throws Exception {
        final AccountRepository accountRepository = repository();
        final MemoryMarker memoryMarker = spy(new MemoryMarker());
        final CountDownLatch waiting = new CountDownLatch(1);
        final UUID id = UUID.randomUUID();

        doAnswer(invocation -> {
            waiting.countDown();

            return invocation.callRealMethod();
        }).when(memoryMarker).await(any(), any());
        when(accountRepository.findById(id)).thenAnswer(_ -> {
            assertTrue(waiting.await(5, TimeUnit.SECONDS));

            return Optional.empty();
        });

        final EntityLookup<Account> firstLookup = lookup(accountRepository, Set.of(Tier.POSTGRES), storages(Set.of(Tier.POSTGRES)), memoryMarker);
        final EntityLookup<Account> secondLookup = lookup(accountRepository, Set.of(Tier.POSTGRES), storages(Set.of(Tier.POSTGRES)), memoryMarker);

        try (final var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            final var firstFuture = executorService.submit(() -> firstLookup.find(id));
            final var secondFuture = executorService.submit(() -> secondLookup.find(id));

            assertTrue(firstFuture.get(10, TimeUnit.SECONDS).isEmpty());
            assertTrue(secondFuture.get(10, TimeUnit.SECONDS).isEmpty());
        }

        verify(accountRepository, times(1)).findById(id);
    }

    @Test
    void holdAndReleaseMembershipAndGetHeld() {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final AccountHolder accountHolder = holder(accountRepository, Set.of(Tier.LOCAL, Tier.POSTGRES));

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        accountHolder.hold(account.getId());

        assertTrue(accountHolder.holds(account.getId()));
        assertEquals(List.of(account), accountHolder.getHeld());

        accountHolder.release(account.getId());

        assertFalse(accountHolder.holds(account.getId()));
        assertTrue(accountHolder.getHeld().isEmpty());
    }

    @Test
    void indexHitAvoidsQueryAndScan() {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final AccountHolder accountHolder = spy(holder(accountRepository, Set.of(Tier.LOCAL, Tier.POSTGRES)));

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        accountHolder.hold(account.getId());

        assertSame(account, accountHolder.getByProperty(AccountDeclaration.NAME, account.getName()).orElseThrow());
        verify(accountHolder, never()).getHeld();
        verify(accountRepository, never()).find(any());
    }

    @Test
    void indexMissSkipsHeldScanQueriesAndWarmsIndex() {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final AccountHolder accountHolder = spy(holder(accountRepository, Set.of(Tier.LOCAL, Tier.POSTGRES)));

        when(accountRepository.find(any())).thenReturn(List.of(account));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertEquals(account, accountHolder.getByProperty(AccountDeclaration.NAME, account.getName()).orElseThrow());
        assertEquals(account, accountHolder.getByProperty(AccountDeclaration.NAME, account.getName()).orElseThrow());

        verify(accountHolder, never()).getHeld();
        verify(accountRepository, times(1)).find(any());
    }

    @Test
    void failedAuthorityWriteRevertsField() {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final String previous = account.getName();
        final AccountHolder accountHolder = holder(accountRepository, Set.of(Tier.LOCAL, Tier.POSTGRES));

        when(accountRepository.apply(any())).thenThrow(new IllegalStateException("offline"));

        assertThrows(IllegalStateException.class, () -> accountHolder.set(account, AccountDeclaration.NAME, "rejected"));
        assertEquals(previous, account.getName());
    }

    @Test
    void failedIncrementRevertsCounter() {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final AccountHolder accountHolder = holder(accountRepository, Set.of(Tier.LOCAL, Tier.POSTGRES));

        when(accountRepository.apply(any())).thenThrow(new IllegalStateException("offline"));

        assertThrows(IllegalStateException.class, () -> accountHolder.increment(account, AccountDeclaration.COUNTER, 5L));
        assertEquals(0L, account.getCounter());
    }

    @Test
    void incrementReturnsNewValue() {
        final Account account = account();
        final AccountHolder accountHolder = holder(repository(), Set.of(Tier.LOCAL, Tier.POSTGRES));

        assertEquals(7L, accountHolder.increment(account, AccountDeclaration.COUNTER, 7L));
        assertEquals(7L, account.getCounter());
    }

    @Test
    void nextVersionAppliesAndDuplicateIsDropped() {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final AccountHolder accountHolder = holder(accountRepository, Set.of(Tier.LOCAL, Tier.POSTGRES));
        final ChangeEvent changeEvent = new ChangeEvent(
                new EntityKey("accounts", null, account.getId()),
                1L,
                me.trae.foundation.database.api.write.WriteKind.UPDATE,
                List.of(increment(3L))
        );

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        accountHolder.hold(account.getId());
        accountHolder.getApplier().apply(changeEvent);
        accountHolder.getApplier().apply(changeEvent);

        assertEquals(3L, account.getCounter());
        assertEquals(1L, accountHolder.getWriter().getVersion(account.getId()));
    }

    @Test
    void versionGapTriggersRefetch() throws Exception {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final Account replacement = new Account(account.getId());
        final AccountHolder accountHolder = holder(accountRepository, Set.of(Tier.LOCAL, Tier.POSTGRES));

        replacement.setName("replacement");
        replacement.setGroup("group");
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account), Optional.of(replacement));
        accountHolder.hold(account.getId());
        accountHolder.getApplier().apply(new ChangeEvent(
                new EntityKey("accounts", null, account.getId()),
                3L,
                me.trae.foundation.database.api.write.WriteKind.UPDATE,
                List.of(set("name", "event"))
        ));

        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        while (accountHolder.get(account.getId()).orElseThrow() != replacement && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }

        assertSame(replacement, accountHolder.get(account.getId()).orElseThrow());
        verify(accountRepository, times(2)).findById(account.getId());
    }

    @Test
    void deleteUnindexes() {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final AccountHolder accountHolder = holder(accountRepository, Set.of(Tier.LOCAL, Tier.POSTGRES));

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        accountHolder.hold(account.getId());
        accountHolder.delete(account);

        assertFalse(accountHolder.holds(account.getId()));
        assertTrue(accountHolder.getContext().getIndexStores().findUnique(AccountDeclaration.NAME_INDEX).orElseThrow().lookup(account.getName()).isEmpty());
        assertTrue(accountHolder.getAllByIndex(AccountDeclaration.GROUP_INDEX, "group").isEmpty());
    }

    @Test
    void holderWithoutLocalHoldsNothing() {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final AccountHolder accountHolder = holder(accountRepository, Set.of(Tier.REDIS, Tier.POSTGRES));

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        accountHolder.hold(account.getId());

        assertFalse(accountHolder.holds(account.getId()));
        assertTrue(accountHolder.getHeld().isEmpty());
    }

    @Test
    void noLocalHolderMustNotThrowWhenSubscribedEventArrives() {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final AccountHolder accountHolder = holder(accountRepository, Set.of(Tier.REDIS, Tier.POSTGRES));

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        accountHolder.hold(account.getId());

        assertDoesNotThrow(() -> accountHolder.getApplier().apply(new ChangeEvent(
                new EntityKey("accounts", null, account.getId()),
                1L,
                me.trae.foundation.database.api.write.WriteKind.UPDATE,
                List.of(set("name", "remote"))
        )));
    }

    @Test
    void directApplierInterleavingLeavesSingleIncrement() {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final java.util.concurrent.atomic.AtomicReference<AccountHolder> holderReference = new java.util.concurrent.atomic.AtomicReference<>();
        final ContextFactory contextFactory = new ContextFactory(tiers -> context(
                accountRepository,
                tiers,
                storages(tiers),
                new LocalMarker(),
                write -> {
                    holderReference.get().getApplier().apply(new ChangeEvent(write.getKey(), 1L, write.getKind(), write.getChanges()));

                    return new WriteResult(1L, null);
                },
                _ -> {},
                (_, _) -> {},
                _ -> {},
                List.of(new LocalStorageProvider())
        ));
        final AccountHolder accountHolder = new AccountHolder(accountRepository, contextFactory, Set.of(Tier.LOCAL, Tier.REDIS, Tier.POSTGRES));

        holderReference.set(accountHolder);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        accountHolder.hold(account.getId());

        assertEquals(1L, accountHolder.increment(account, AccountDeclaration.COUNTER, 1L));
        assertEquals(1L, account.getCounter(), "Assigning the write result must preserve a single increment");
    }

    @Test
    void constructorInjectedBeansKeepTheirOwnTiersInSameJvm() {
        final AccountRepository accountRepository = repository();
        final ContextFactory contextFactory = new ContextFactory(tiers -> context(
                accountRepository,
                tiers,
                storages(tiers),
                new LocalMarker(),
                write -> new WriteResult(accountRepository.apply(write), null),
                _ -> {},
                (_, _) -> {},
                _ -> {},
                List.of(new LocalStorageProvider())
        ));
        final TestApplication testApplication = new TestApplication();

        Injector.INSTANCE.provide(AccountRepository.class, accountRepository);
        Injector.INSTANCE.provide(HolderContextFactory.class, contextFactory);

        try {
            Injector.INSTANCE.initialize(testApplication, List.of(LocalBean.class, RedisBean.class, AllBean.class));

            assertEquals(Set.of(Tier.LOCAL, Tier.POSTGRES), Injector.INSTANCE.get(LocalBean.class).getContext().getTiers());
            assertEquals(Set.of(Tier.REDIS, Tier.POSTGRES), Injector.INSTANCE.get(RedisBean.class).getContext().getTiers());
            assertEquals(Tier.ALL, Injector.INSTANCE.get(AllBean.class).getContext().getTiers());
            assertEquals(3, contextFactory.getHolderList().size());

            final Account account = account();

            when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
            Injector.INSTANCE.get(LocalBean.class).hold(account.getId());

            assertTrue(Injector.INSTANCE.get(LocalBean.class).holds(account.getId()));
            assertFalse(Injector.INSTANCE.get(AllBean.class).holds(account.getId()));
            assertFalse(Injector.INSTANCE.get(RedisBean.class).holds(account.getId()));
        } finally {
            Injector.INSTANCE.shutdown(testApplication);
            Injector.INSTANCE.unprovide(AccountRepository.class);
            Injector.INSTANCE.unprovide(HolderContextFactory.class);
        }
    }

    @Test
    void gapReloadRunsOffCallbackAndNeverUnsubscribes() throws Exception {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final java.util.concurrent.atomic.AtomicInteger loadCount = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger unsubscribeCount = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicReference<String> reloadThread = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.CountDownLatch reloaded = new java.util.concurrent.CountDownLatch(1);
        final ContextFactory contextFactory = new ContextFactory(tiers -> context(
                accountRepository,
                tiers,
                storages(tiers),
                new LocalMarker(),
                write -> new WriteResult(accountRepository.apply(write), null),
                _ -> {},
                (_, _) -> {},
                _ -> unsubscribeCount.incrementAndGet(),
                List.of(new LocalStorageProvider())
        ));
        final AccountHolder accountHolder = new AccountHolder(
                accountRepository,
                contextFactory,
                Set.of(Tier.LOCAL, Tier.POSTGRES)
        );

        when(accountRepository.findById(account.getId())).thenAnswer(_ -> {
            if (loadCount.incrementAndGet() > 1) {
                reloadThread.set(Thread.currentThread().getName());
                reloaded.countDown();
            }

            return Optional.of(account);
        });

        try {
            accountHolder.hold(account.getId());
            accountHolder.getApplier().apply(new ChangeEvent(
                    new EntityKey("accounts", null, account.getId()),
                    3L,
                    me.trae.foundation.database.api.write.WriteKind.UPDATE,
                    List.of(set("name", "gap"))
            ));

            assertTrue(reloaded.await(5, TimeUnit.SECONDS));
            assertEquals("Database-Refetch", reloadThread.get());
            assertEquals(0, unsubscribeCount.get());
        } finally {
            accountHolder.getExecutor().shutdownNow();
        }
    }
}