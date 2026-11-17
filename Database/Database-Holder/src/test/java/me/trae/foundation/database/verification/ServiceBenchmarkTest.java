package me.trae.foundation.database.verification;

import me.trae.foundation.database.api.property.PropertyRegistry;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.postgres.schema.PostgresSynchronizer;
import me.trae.foundation.database.postgres.schema.SchemaDefinition;
import me.trae.foundation.database.redis.event.RedisEvents;
import me.trae.foundation.database.redis.storage.RedisEntityStorage;
import me.trae.foundation.database.redis.write.RedisEntityWriter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static me.trae.foundation.database.verification.BenchmarkSupport.latency;
import static me.trae.foundation.database.verification.DatabaseFixture.Account;
import static me.trae.foundation.database.verification.DatabaseFixture.AccountDeclaration;
import static me.trae.foundation.database.verification.DatabaseFixture.account;
import static me.trae.foundation.database.verification.DatabaseFixture.increment;
import static me.trae.foundation.database.verification.DatabaseFixture.set;
import static me.trae.foundation.database.verification.DatabaseFixture.write;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("benchmark")
public final class ServiceBenchmarkTest extends ServiceFixture {

    @Test
    void redisHashReadAndScriptedWriteLatencyAndThroughput() {
        final Account account = account();
        final RedisEntityStorage<Account> redisEntityStorage = this.entityStorage();
        final RedisEntityWriter redisEntityWriter = this.entityWriter();
        final var write = write(account.getId(), WriteKind.UPDATE, 0L, increment(1L));

        redisEntityStorage.put(account.getId(), account);
        latency("redis_hash_read", 200, 2000, () -> redisEntityStorage.get(account.getId()).orElseThrow());
        latency(
                "redis_scripted_write",
                200,
                2000,
                () -> redisEntityWriter.apply(
                        redisEntityStorage.path(account.getId()),
                        redisEntityStorage.channel(account.getId()),
                        write,
                        Duration.ofSeconds(10)
                )
        );

        assertEquals("2200", redisDriver.getCommands().hget(redisEntityStorage.path(account.getId()), "counter"));
    }

    @Test
    void postgresFindByIdAndIndexedColumnLatency() {
        postgresDriver.getDslContext().execute("INSERT INTO accounts (id, name, account_group, counter) SELECT md5(i::text)::uuid, 'bench-' || i, 'group', i FROM generate_series(1, 10000) i");

        final Account account = accountRepository.find(Query.where(AccountDeclaration.NAME, "bench-5000")).getFirst();

        latency("postgres_findById_10k", 100, 1000, () -> accountRepository.findById(account.getId()).orElseThrow());
        latency("postgres_indexed_name_10k", 100, 1000, () -> accountRepository.find(Query.where(AccountDeclaration.NAME, "bench-5000")).getFirst());
    }

    @Test
    void bootDdlTimeForTenTables() {
        final AtomicInteger batchNumber = new AtomicInteger();

        latency("boot_ddl_10_tables", 1, 5, () -> {
            final int batch = batchNumber.incrementAndGet();
            final List<String> statementList = new ArrayList<>();

            IntStream.range(
                    0,
                    10
            ).forEach(index -> statementList.addAll(SchemaDefinition.statements("boot_%s_%s".formatted(batch, index), false, PropertyRegistry.getProperties(Account.class), Map.of(AccountDeclaration.NAME, me.trae.foundation.database.postgres.index.PostgresIndexType.UNIQUE))));
            new PostgresSynchronizer(postgresDriver).synchronize(statementList);
        });
    }

    @Test
    void publishToAppliedLatency() {
        final Account account = this.persistedAccount();
        final AtomicInteger sequence = new AtomicInteger();

        try (final ServicesTest.Node node = new ServicesTest.Node(redisDriver)) {
            node.getAllBean().hold(account.getId());

            latency("publish_to_applied", 50, 500, () -> {
                final int version = sequence.incrementAndGet();
                final String value = "applied-%s".formatted(version);

                redisDriver.getCommands().publish(
                        this.entityStorage().channel(account.getId()),
                        "%s\nbenchmark-sender\nUPDATE\n%s".formatted(version, RedisEvents.encode(List.of(set("name", value))))
                );

                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);

                while (!value.equals(node.getAllBean().getHeld().getFirst().getName()) && System.nanoTime() < deadline) {
                    java.util.concurrent.locks.LockSupport.parkNanos(10_000L);
                }

                assertEquals(value, node.getAllBean().getHeld().getFirst().getName());
            });
        }
    }
}