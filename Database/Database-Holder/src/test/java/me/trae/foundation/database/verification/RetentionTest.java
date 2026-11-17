package me.trae.foundation.database.verification;

import me.trae.foundation.database.api.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static me.trae.foundation.database.verification.DatabaseFixture.Account;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountDeclaration;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountHolder;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountRepository;
import static me.trae.foundation.database.verification.DatabaseFixture.account;
import static me.trae.foundation.database.verification.DatabaseFixture.holder;
import static me.trae.foundation.database.verification.DatabaseFixture.repository;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.when;

public final class RetentionTest {

    @Test
    void releaseClearsVersionAndAllIndexMaps() throws Exception {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final AccountHolder accountHolder = holder(accountRepository, Set.of(Tier.LOCAL, Tier.POSTGRES));

        when(accountRepository.findById(account.getId())).thenReturn(java.util.Optional.of(account));
        accountHolder.hold(account.getId());
        accountHolder.set(account, AccountDeclaration.NAME, "renamed");
        accountHolder.release(account.getId());

        this.assertEmpty(accountHolder);
    }

    @Test
    void deleteClearsVersionAndAllIndexMaps() throws Exception {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final AccountHolder accountHolder = holder(accountRepository, Set.of(Tier.LOCAL, Tier.POSTGRES));

        when(accountRepository.findById(account.getId())).thenReturn(java.util.Optional.of(account));
        accountHolder.hold(account.getId());
        accountHolder.increment(account, AccountDeclaration.COUNTER, 1L);
        accountHolder.delete(account);

        this.assertEmpty(accountHolder);
    }

    @Test
    void fiftyThousandHoldReleaseCyclesRetainNothing() throws Exception {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final AccountHolder accountHolder = holder(accountRepository, Set.of(Tier.LOCAL, Tier.POSTGRES));

        when(accountRepository.findById(account.getId())).thenReturn(java.util.Optional.of(account));

        IntStream.range(0, 50_000).forEach(index -> {
            accountHolder.hold(account.getId());
            accountHolder.release(account.getId());

            if (index % 1000 == 0) {
                clearInvocations(accountRepository);
            }
        });

        this.assertEmpty(accountHolder);
    }

    @Test
    void noLocalReleaseAlsoClearsIndexReverseMaps() throws Exception {
        final AccountRepository accountRepository = repository();
        final Account account = account();
        final AccountHolder accountHolder = holder(accountRepository, Set.of(Tier.REDIS, Tier.POSTGRES));

        when(accountRepository.findById(account.getId())).thenReturn(java.util.Optional.of(account));
        accountHolder.hold(account.getId());
        accountHolder.release(account.getId());

        this.assertEmpty(accountHolder);
    }

    private void assertEmpty(final AccountHolder accountHolder) throws Exception {
        assertTrue(accountHolder.getWriter().getVersionMap().isEmpty(), "Version map retained an entity");
        assertTrue(accountHolder.getHeld().isEmpty(), "Local store retained an entity");

        for (final var uniqueIndexStore : accountHolder.getContext().getIndexStores().getUniqueStores()) {
            assertTrue(this.map(uniqueIndexStore, "keyMap").isEmpty(), "Unique reverse map retained an entity");
        }

        for (final var groupedIndexStore : accountHolder.getContext().getIndexStores().getGroupedStores()) {
            assertTrue(this.map(groupedIndexStore, "keyMap").isEmpty(), "Grouped reverse map retained an entity");
            assertTrue(this.map(groupedIndexStore, "idMap").isEmpty(), "Grouped member map retained an entity");
        }
    }

    private Map<?, ?> map(final Object object, final String name) throws Exception {
        final var field = object.getClass().getDeclaredField(name);

        field.setAccessible(true);

        return (Map<?, ?>) field.get(object);
    }
}