package me.trae.foundation.database.verification;

import me.trae.foundation.database.api.marker.SharedMarker;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.lookup.EntityLookup;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static me.trae.foundation.database.verification.DatabaseFixture.Account;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountRepository;
import static me.trae.foundation.database.verification.DatabaseFixture.account;
import static me.trae.foundation.database.verification.DatabaseFixture.lookup;
import static me.trae.foundation.database.verification.DatabaseFixture.repository;
import static me.trae.foundation.database.verification.DatabaseFixture.storages;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class CoalescingIsolationTest {

    @Test
    void localOnlyMissMustNotBecomeFullLookupMiss() throws Exception {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final SharedMarker sharedMarker = mock(SharedMarker.class);
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger claims = new AtomicInteger();

        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(sharedMarker.claim(any(), any())).thenAnswer(_ -> {
            if (claims.incrementAndGet() == 1) {
                entered.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }

            return true;
        });

        final EntityLookup<Account> entityLookup = lookup(accountRepository, Tier.ALL, storages(Tier.ALL), sharedMarker);

        try (final ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            final Future<Optional<Account>> localFuture = executorService.submit(() -> entityLookup.find(account.getId(), Set.of(Tier.LOCAL)));

            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));

                final Future<Optional<Account>> fullFuture = executorService.submit(() -> entityLookup.find(account.getId(), Tier.ALL));

                assertTrue(fullFuture.get(3, TimeUnit.SECONDS).isPresent(), "A LOCAL-only in-flight miss must not suppress a PostgreSQL-capable read");
            } finally {
                release.countDown();
            }

            assertTrue(localFuture.get(5, TimeUnit.SECONDS).isEmpty());
            verify(accountRepository).findById(account.getId());
        }
    }
}