package me.trae.foundation.database.core.batch;

import me.trae.foundation.database.api.exception.ConnectionException;
import me.trae.foundation.database.core.DatabaseTestEnvironment;
import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.core.driver.PostgresSettings;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.driver.RedisSettings;
import me.trae.foundation.database.storage.redis.RedisInvalidation;
import me.trae.foundation.database.storage.redis.RedisNamespace;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OutboxCrashRecoveryTest {

    private static final Table<Record> TABLE = DSL.table(DSL.name("foundation_cache_invalidation_outbox"));
    private static final Field<UUID> ID = DSL.field(DSL.name("id"), SQLDataType.UUID);
    private static final Field<String> CACHE_KEY = DSL.field(DSL.name("cache_key"), SQLDataType.VARCHAR(1024));
    private static final Field<String> MISSING_KEY = DSL.field(DSL.name("missing_key"), SQLDataType.VARCHAR(1024));
    private static final Field<String> LOCK_KEY = DSL.field(DSL.name("lock_key"), SQLDataType.VARCHAR(1024));
    private static final Field<String> CHANNEL = DSL.field(DSL.name("channel"), SQLDataType.VARCHAR(1024));
    private static final Field<UUID> ENTITY_ID = DSL.field(DSL.name("entity_id"), SQLDataType.UUID);
    private static final Field<String> INSTANCE_ID = DSL.field(DSL.name("instance_id"), SQLDataType.VARCHAR(64));
    private static final Field<Instant> CREATED_AT = DSL.field(DSL.name("created_at"), SQLDataType.INSTANT);

    @Test
    void processCrashAfterRedisDeliveryLeavesOutboxEventForRedelivery() throws Exception {
        DatabaseTestEnvironment.requirePostgresAndRedis();

        final PostgresDriver postgresDriver = new PostgresDriver(new PostgresSettings(
                System.getProperty("postgres.host", "localhost"),
                Integer.getInteger("postgres.port", 5432),
                System.getProperty("postgres.database", "foundation_test"),
                System.getProperty("postgres.username", "postgres"),
                System.getProperty("postgres.password", "postgres"),
                4
        ));
        final RedisDriver redisDriver = new RedisDriver(new RedisSettings(
                System.getProperty("redis.host", "localhost"),
                Integer.getInteger("redis.port", 6379),
                System.getProperty("redis.password"),
                Integer.getInteger("redis.database", 0),
                Duration.ofSeconds(2)
        ));
        final UUID eventId = UUID.randomUUID();

        final UUID entityId = UUID.randomUUID();

        final String tableName = "outbox_crash_%s".formatted(UUID.randomUUID().toString().replace("-", ""));

        final RedisInvalidation invalidation = RedisNamespace.of(tableName).getInvalidation(entityId, "crash-test");

        final CountDownLatch notificationLatch = new CountDownLatch(1);

        final CountDownLatch rowLockAcquired = new CountDownLatch(1);

        final CountDownLatch releaseRowLock = new CountDownLatch(1);

        final AtomicReference<Throwable> transactionFailure = new AtomicReference<>();
        Process workerProcess = null;
        Thread transactionThread = null;

        try {
            postgresDriver.connect();
            redisDriver.connect();
        } catch (final ConnectionException exception) {
            postgresDriver.disconnect();
            redisDriver.disconnect();
            Assumptions.abort("PostgreSQL or Redis is not reachable: %s".formatted(exception.getMessage()));
        }

        try {
            final CacheInvalidationOutbox outbox = new CacheInvalidationOutbox(postgresDriver, redisDriver);
            outbox.synchronize();
            redisDriver.getCommands().set(invalidation.getCacheKey(), "stale");
            redisDriver.subscribe(invalidation.getChannel(), _ -> notificationLatch.countDown());
            redisDriver.publish(invalidation.getChannel(), "ready");
            assertTrue(notificationLatch.await(5, TimeUnit.SECONDS), "Redis subscription did not become ready");
            // Ignore the readiness message and wait for the event message separately.
            final CountDownLatch eventLatch = new CountDownLatch(1);
            redisDriver.unsubscribe(invalidation.getChannel());
            redisDriver.subscribe(invalidation.getChannel(), message -> {
                if (message.endsWith(entityId.toString())) {
                    eventLatch.countDown();
                }
            });
            Thread.sleep(100L);

            postgresDriver.getDslContext().insertInto(TABLE)
                    .set(ID, eventId)
                    .set(CACHE_KEY, invalidation.getCacheKey())
                    .set(MISSING_KEY, invalidation.getMissingKey())
                    .set(LOCK_KEY, invalidation.getLockKey())
                    .set(CHANNEL, invalidation.getChannel())
                    .set(ENTITY_ID, entityId)
                    .set(INSTANCE_ID, invalidation.getInstanceId())
                    .set(CREATED_AT, Instant.EPOCH)
                    .execute();

            transactionThread = Thread.ofVirtual().start(() -> {
                try {
                    postgresDriver.getDslContext().transaction(configuration -> {
                        final DSLContext transactionContext = DSL.using(configuration);
                        transactionContext.select(ID).from(TABLE).where(ID.eq(eventId)).forUpdate().fetchOne();
                        rowLockAcquired.countDown();

                        if (!releaseRowLock.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting to release the outbox row lock");
                        }
                    });
                } catch (final Throwable throwable) {
                    transactionFailure.set(throwable);
                }
            });

            assertTrue(rowLockAcquired.await(5, TimeUnit.SECONDS), "Could not lock the outbox row");
            final String javaExecutable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();

            final String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            workerProcess = new ProcessBuilder(
                    javaExecutable,
                    "-Xms32m",
                    "-Xmx128m",
                    "-cp",
                    classPath,
                    OutboxDispatchWorker.class.getName(),
                    System.getProperty("postgres.host", "localhost"),
                    System.getProperty("postgres.port", "5432"),
                    System.getProperty("postgres.database", "foundation_test"),
                    System.getProperty("postgres.username", "postgres"),
                    System.getProperty("postgres.password", "postgres"),
                    System.getProperty("redis.host", "localhost"),
                    System.getProperty("redis.port", "6379"),
                    System.getProperty("redis.password", ""),
                    System.getProperty("redis.database", "0")
            ).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();

            assertTrue(eventLatch.await(10, TimeUnit.SECONDS), "Outbox worker did not publish its invalidation");
            assertEquals(0L, redisDriver.getCommands().exists(invalidation.getCacheKey()));
            workerProcess.destroyForcibly();
            assertTrue(workerProcess.waitFor(10, TimeUnit.SECONDS), "Outbox worker did not terminate");
            releaseRowLock.countDown();
            transactionThread.join(Duration.ofSeconds(10));

            assertNull(transactionFailure.get());
            assertEquals(1, this.getOutboxCount(postgresDriver, eventId));
            assertTrue(redisDriver.getCommands().pttl("foundation:cache-invalidation-outbox:dispatcher") > 0L);
            assertTrue(redisDriver.getCommands().pttl(invalidation.getLockKey()) > 0L);
            redisDriver.getCommands().pexpire("foundation:cache-invalidation-outbox:dispatcher", 1L);
            redisDriver.getCommands().pexpire(invalidation.getLockKey(), 1L);
            Thread.sleep(25L);

            new CacheInvalidationOutbox(postgresDriver, redisDriver).dispatch();

            assertEquals(0, this.getOutboxCount(postgresDriver, eventId));
        } finally {
            if (workerProcess != null && workerProcess.isAlive()) {
                workerProcess.destroyForcibly();
            }

            releaseRowLock.countDown();
            if (transactionThread != null) {
                transactionThread.join(Duration.ofSeconds(10));
            }

            redisDriver.unsubscribe(invalidation.getChannel());
            if (postgresDriver.isConnected()) {
                postgresDriver.getDslContext().deleteFrom(TABLE).where(ID.eq(eventId)).execute();
                postgresDriver.disconnect();
            }

            if (redisDriver.isConnected()) {
                redisDriver.getCommands().del(invalidation.getCacheKey(), invalidation.getMissingKey(), invalidation.getLockKey());
                redisDriver.disconnect();
            }
        }
    }

    private int getOutboxCount(final PostgresDriver postgresDriver, final UUID eventId) {
        return postgresDriver.getDslContext().fetchCount(TABLE, ID.eq(eventId));
    }
}
