package me.trae.foundation.database.core;

import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.core.driver.PostgresSettings;
import me.trae.foundation.database.core.holder.change.ChangeTracker;
import me.trae.foundation.database.storage.codec.EntityCodec;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.driver.RedisSettings;
import me.trae.foundation.database.storage.redis.RedisNamespace;
import me.trae.foundation.database.storage.redis.RedisStorage;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.LongConsumer;

final class DatabasePipelineBenchmarkTest {

    @Test
    void measuresDatabaseReadWriteAndCodecPipelines() {
        final PostgresDriver postgresDriver = new PostgresDriver(new PostgresSettings(
                System.getProperty("postgres.host", "localhost"),
                Integer.getInteger("postgres.port", 5432),
                System.getProperty("postgres.database", "foundation_test"),
                System.getProperty("postgres.username", "postgres"),
                System.getProperty("postgres.password", "postgres"),
                8
        ));
        final RedisDriver redisDriver = new RedisDriver(new RedisSettings(
                System.getProperty("redis.host", "localhost"),
                Integer.getInteger("redis.port", 6379),
                System.getProperty("redis.password"),
                Integer.getInteger("redis.database", 0),
                Duration.ofSeconds(2)
        ));
        final String table = "database_benchmark_%s".formatted(UUID.randomUUID().toString().replace("-", ""));
        final UUID id = UUID.randomUUID();
        final CoreDatabase coreDatabase = new CoreDatabase(postgresDriver, redisDriver, null, Duration.ofHours(1), 100);

        try {
            postgresDriver.connect();
            redisDriver.connect();
        } catch (final RuntimeException exception) {
            postgresDriver.disconnect();
            redisDriver.disconnect();
            Assumptions.abort("PostgreSQL or Redis is not reachable: %s".formatted(exception.getMessage()));
        }

        try {
            final MultiJvmDatabaseWorker.WorkerRepository repository = new MultiJvmDatabaseWorker.WorkerRepository(coreDatabase, table);
            final MultiJvmDatabaseWorker.WorkerHolder holder = new MultiJvmDatabaseWorker.WorkerHolder(repository);
            final RedisStorage<MultiJvmDatabaseWorker.WorkerEntity> redisStorage = new RedisStorage<>(redisDriver, RedisNamespace.of(table), MultiJvmDatabaseWorker.WorkerEntity.class, Duration.ofMinutes(5));
            final MultiJvmDatabaseWorker.WorkerEntity entity = new MultiJvmDatabaseWorker.WorkerEntity(id);
            final EntityCodec<MultiJvmDatabaseWorker.WorkerEntity> entityCodec = new EntityCodec<>(MultiJvmDatabaseWorker.WorkerEntity.class);
            final ChangeTracker<MultiJvmDatabaseWorker.WorkerEntity> changeTracker = new ChangeTracker<>(MultiJvmDatabaseWorker.WorkerEntity.class, Duration.ofMinutes(5));
            entity.setValue("benchmark");
            coreDatabase.start();
            holder.save(entity);
            coreDatabase.getBatchQueue().flush();
            holder.getById(id);
            changeTracker.snapshot(entity);

            this.measure("entity encode/decode", 10_000, _ -> entityCodec.decode(id, entityCodec.encode(entity)));
            this.measure("ChangeTracker diff", 10_000, _ -> changeTracker.diff(entity));
            this.measure("local holder hit", 10_000, _ -> holder.getById(id));
            this.measure("Redis hash read", 1_000, _ -> redisStorage.get(id));
            this.measure("PostgreSQL repository read", 500, _ -> repository.findById(id));
            this.measure("cold Redis + PostgreSQL read", 100, _ -> {
                redisStorage.invalidate(id);
                redisStorage.getOrLoad(id, () -> repository.findById(id));
            });

            this.measure("repository write + outbox flush", 25, index -> {
                entity.setValue("benchmark-%s".formatted(index));
                holder.save(entity);
                coreDatabase.getBatchQueue().flush();
            });

            Thread.sleep(1_050L);
            coreDatabase.getBatchQueue().flush();
            assertTrueOutboxEmpty(postgresDriver, id);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } finally {
            coreDatabase.stop();

            if (postgresDriver.isConnected()) {
                postgresDriver.getDslContext().dropTableIfExists(DSL.name(table)).cascade().execute();
                postgresDriver.disconnect();
            }

            if (redisDriver.isConnected()) {
                final List<String> keyList = redisDriver.getCommands().keys("%s*".formatted(table));
                if (!keyList.isEmpty()) {
                    redisDriver.getCommands().del(keyList.toArray(String[]::new));
                }
                redisDriver.disconnect();
            }
        }
    }

    private void measure(final String name, final int iterationCount, final LongConsumer operation) {
        final long[] latencyArray = new long[iterationCount];
        final long totalStartNanos = System.nanoTime();

        for (int index = 0; index < iterationCount; index++) {
            final long startNanos = System.nanoTime();
            operation.accept(index);
            latencyArray[index] = System.nanoTime() - startNanos;
        }

        final long totalNanos = System.nanoTime() - totalStartNanos;
        java.util.Arrays.sort(latencyArray);
        final double operationsPerSecond = iterationCount / (totalNanos / 1_000_000_000.0);
        System.out.println("BENCHMARK|%s|ops=%s|opsPerSecond=%.2f|p50Micros=%.2f|p95Micros=%.2f|p99Micros=%.2f".formatted(
                name,
                iterationCount,
                operationsPerSecond,
                this.toMicros(latencyArray, 0.50),
                this.toMicros(latencyArray, 0.95),
                this.toMicros(latencyArray, 0.99)
        ));
    }

    private double toMicros(final long[] latencyArray, final double percentile) {
        final int index = Math.min(latencyArray.length - 1, (int) Math.ceil(latencyArray.length * percentile) - 1);
        return latencyArray[index] / 1_000.0;
    }

    private static void assertTrueOutboxEmpty(final PostgresDriver postgresDriver, final UUID id) {
        final int count = postgresDriver.getDslContext().fetchCount(
                DSL.table(DSL.name("foundation_cache_invalidation_outbox")),
                DSL.field(DSL.name("entity_id"), UUID.class).eq(id)
        );

        if (count != 0) {
            throw new AssertionError("Benchmark left %s outbox records for its entity".formatted(count));
        }
    }
}