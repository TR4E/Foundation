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

import static me.trae.foundation.database.verification.DatabaseFixture.Account;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountRepository;
import static me.trae.foundation.database.verification.DatabaseFixture.account;
import static me.trae.foundation.database.verification.DatabaseFixture.lookup;
import static me.trae.foundation.database.verification.DatabaseFixture.repository;
import static me.trae.foundation.database.verification.DatabaseFixture.storages;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public final class CoalescingIsolationTest {

    @Test
    void localOnlyMissMustNotBecomeFullLookupMiss() throws Exception {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final SharedMarker sharedMarker = mock(SharedMarker.class);
        final CountDownLatch loading = new CountDownLatch(1), release = new CountDownLatch(1);

        when(sharedMarker.claim(any(), any())).thenReturn(true);
        when(accountRepository.findById(account.getId())).thenAnswer(_ -> {
            loading.countDown();
            assertTrue(release.await(5L, TimeUnit.SECONDS));

            return Optional.of(account);
        });

        final EntityLookup<Account> entityLookup = lookup(accountRepository, Tier.ALL, storages(Tier.ALL), sharedMarker);

        try (final ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            final Future<Optional<Account>> fullFuture = executorService.submit(() -> entityLookup.find(account.getId(), Tier.ALL));

            try {
                assertTrue(loading.await(5L, TimeUnit.SECONDS));
                assertTrue(entityLookup.find(account.getId(), Set.of(Tier.LOCAL)).isEmpty());
                verify(sharedMarker).claim(any(), any());
                verifyNoMoreInteractions(sharedMarker);
            } finally {
                release.countDown();
            }

            assertEquals(Optional.of(account), fullFuture.get(5L, TimeUnit.SECONDS));
            verify(accountRepository).findById(account.getId());
        }
    }
}