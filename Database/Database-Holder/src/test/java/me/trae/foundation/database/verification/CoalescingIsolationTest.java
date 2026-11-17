package me.trae.foundation.database.verification;

import me.trae.foundation.database.api.marker.SharedMarker;
import me.trae.foundation.database.api.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static me.trae.foundation.database.verification.DatabaseFixture.Account;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountRepository;
import static me.trae.foundation.database.verification.DatabaseFixture.account;
import static me.trae.foundation.database.verification.DatabaseFixture.lookup;
import static me.trae.foundation.database.verification.DatabaseFixture.repository;
import static me.trae.foundation.database.verification.DatabaseFixture.storages;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class CoalescingIsolationTest {

    @Test
    void localOnlyMissMustNotBecomeFullLookupMiss() throws Exception {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final SharedMarker sharedMarker = mock(SharedMarker.class);
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicReference<Thread> readerThread = new AtomicReference<>();

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(sharedMarker.claim(any(), any())).thenAnswer(_ -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));

            return true;
        });

        final var entityLookup = lookup(accountRepository, Tier.ALL, storages(Tier.ALL), sharedMarker);

        try (final var executorService = Executors.newFixedThreadPool(2)) {
            final var localFuture = executorService.submit(() -> entityLookup.find(account.getId(), Set.of(Tier.LOCAL)));

            assertTrue(entered.await(5, TimeUnit.SECONDS));

            final var fullFuture = executorService.submit(() -> {
                readerThread.set(Thread.currentThread());

                return entityLookup.find(account.getId(), Tier.ALL);
            });
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

            while ((readerThread.get() == null || readerThread.get().getState() != Thread.State.WAITING) && System.nanoTime() < deadline) {
                Thread.sleep(1L);
            }

            assertNotNull(readerThread.get());
            assertEquals(Thread.State.WAITING, readerThread.get().getState());
            release.countDown();

            assertTrue(localFuture.get(5, TimeUnit.SECONDS).isEmpty());
            assertTrue(fullFuture.get(5, TimeUnit.SECONDS).isPresent(), "A LOCAL-only in-flight miss must not suppress a PostgreSQL-capable read");
        } finally {
            release.countDown();
        }
    }
}