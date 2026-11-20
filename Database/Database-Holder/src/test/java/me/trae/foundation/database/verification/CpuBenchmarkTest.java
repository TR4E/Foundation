package me.trae.foundation.database.verification;

import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.batch.BatchQueue;
import me.trae.foundation.database.local.LocalStorage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static me.trae.foundation.database.verification.BenchmarkSupport.latency;
import static me.trae.foundation.database.verification.BenchmarkSupport.throughput;
import static me.trae.foundation.database.verification.DatabaseFixture.Account;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountDeclaration;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountHolder;
import static me.trae.foundation.database.verification.DatabaseFixture.account;
import static me.trae.foundation.database.verification.DatabaseFixture.holder;
import static me.trae.foundation.database.verification.DatabaseFixture.increment;
import static me.trae.foundation.database.verification.DatabaseFixture.namespace;
import static me.trae.foundation.database.verification.DatabaseFixture.repository;
import static me.trae.foundation.database.verification.DatabaseFixture.write;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("benchmark")
public final class CpuBenchmarkTest {

    @Test
    void localGetAndPutOneThread() throws Exception {
        this.localOperations(1);
    }

    @Test
    void localGetAndPutEightThreads() throws Exception {
        this.localOperations(8);
    }

    @Test
    void indexVersusHeldScanAtOneThousand() {
        this.indexVersusScan(1000);
    }

    @Test
    void indexVersusHeldScanAtTenThousand() {
        this.indexVersusScan(10_000);
    }

    @Test
    void batchMergeThroughput() throws Exception {
        final BatchQueue batchQueue = new BatchQueue(0);
        final var write = write(UUID.randomUUID(), WriteKind.UPDATE, 0L, increment(1L));

        throughput("batch_merge", 1, 500_000, () -> batchQueue.add(write));
        assertEquals(1, batchQueue.size());
        assertEquals(600_000L, batchQueue.drain().getFirst().getChanges().getFirst().getValue());
    }

    private void localOperations(final int threads) throws Exception {
        final LocalStorage<UUID, Account> localStorage = new LocalStorage<>(namespace("benchmark"), 0, Duration.ZERO);
        final Account account = account();

        localStorage.put(account.getId(), account);
        throughput("local_get", threads, 500_000, () -> localStorage.get(account.getId()).orElseThrow());
        throughput("local_put", threads, 500_000, () -> localStorage.put(account.getId(), account));

        assertEquals(account, localStorage.get(account.getId()).orElseThrow());
    }

    private void indexVersusScan(final int size) {
        final AccountHolder accountHolder = holder(repository(), Set.of(Tier.LOCAL, Tier.POSTGRES));
        final List<Account> accountList = IntStream.range(0, size)
                .mapToObj(index -> {
                    final Account account = account();

                    account.setCounter((long) index);
                    accountHolder.getContext().getStorageMap().get(Tier.LOCAL).put(account.getId(), account);
                    accountHolder.reindex(account);

                    return account;
                })
                .toList();
        final Account targetAccount = accountList.getLast();

        latency(
                "index_lookup_%d".formatted(size),
                2000,
                5000,
                () -> accountHolder.getByProperty(AccountDeclaration.NAME, targetAccount.getName()).orElseThrow()
        );
        latency(
                "held_scan_%d".formatted(size),
                200,
                2000,
                () -> accountHolder.getByProperty(AccountDeclaration.COUNTER, targetAccount.getCounter()).orElseThrow()
        );

        assertEquals(targetAccount, accountHolder.getByProperty(AccountDeclaration.COUNTER, targetAccount.getCounter()).orElseThrow());
    }
}