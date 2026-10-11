package me.trae.foundation.database.core;

import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.core.driver.PostgresSettings;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.driver.RedisSettings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContainerFailureRecoveryTest {

    @Test
    void stoppedPostgresAndRedisContainersRecoverQueuedWritesAndOutboxEvents() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("containerRecoveryTests"), "Set -DcontainerRecoveryTests=true to stop and restart the explicitly named audit containers");

        final String postgresContainer = System.getProperty("audit.postgres.container");
        final String redisContainer = System.getProperty("audit.redis.container");
        Assumptions.assumeTrue(postgresContainer != null && redisContainer != null, "Set the exact audit PostgreSQL and Redis container names");

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
        final String table = "container_recovery_%s".formatted(UUID.randomUUID().toString().replace("-", ""));
        final CoreDatabase coreDatabase = new CoreDatabase(postgresDriver, redisDriver, null, Duration.ofHours(1), 100);
        boolean postgresStopped = false;
        boolean redisStopped = false;

        try {
            postgresDriver.connect();
            redisDriver.connect();
            final MultiJvmDatabaseWorker.WorkerRepository repository = new MultiJvmDatabaseWorker.WorkerRepository(coreDatabase, table);
            final MultiJvmDatabaseWorker.WorkerHolder holder = new MultiJvmDatabaseWorker.WorkerHolder(repository);
            coreDatabase.start();

            final MultiJvmDatabaseWorker.WorkerEntity entity = new MultiJvmDatabaseWorker.WorkerEntity(UUID.randomUUID());
            entity.setValue("initial");
            holder.save(entity);
            coreDatabase.getBatchQueue().flush();

            entity.setValue("postgres-recovered");
            holder.save(entity);
            this.runDocker("stop", postgresContainer);
            postgresStopped = true;
            coreDatabase.getBatchQueue().flush();
            assertTrue(coreDatabase.getBatchQueue().getPendingWriteStore().size() > 0, "PostgreSQL outage should retain its failed write");

            this.runDocker("start", postgresContainer);
            postgresStopped = false;
            this.awaitPostgres(postgresDriver);
            coreDatabase.getBatchQueue().flush();
            assertEquals("postgres-recovered", repository.findById(entity.getId()).orElseThrow().getValue());

            Thread.sleep(1_050L);
            coreDatabase.getBatchQueue().flush();
            assertEquals(0, this.getOutboxCount(postgresDriver, entity.getId()));

            entity.setValue("redis-recovered");
            holder.save(entity);
            this.runDocker("stop", redisContainer);
            redisStopped = true;
            coreDatabase.getBatchQueue().flush();
            assertEquals("redis-recovered", repository.findById(entity.getId()).orElseThrow().getValue());
            assertEquals(1, this.getOutboxCount(postgresDriver, entity.getId()));

            this.runDocker("start", redisContainer);
            redisStopped = false;
            this.awaitRedis(redisDriver);
            Thread.sleep(1_050L);
            coreDatabase.getBatchQueue().flush();
            assertEquals(0, this.getOutboxCount(postgresDriver, entity.getId()));
        } finally {
            if (postgresStopped) {
                this.runDocker("start", postgresContainer);
            }

            if (redisStopped) {
                this.runDocker("start", redisContainer);
            }

            coreDatabase.stop();

            if (postgresDriver.isConnected()) {
                postgresDriver.getDslContext().dropTableIfExists(DSL.name(table)).cascade().execute();
                postgresDriver.disconnect();
            }

            if (redisDriver.isConnected()) {
                final String[] keyList = redisDriver.getCommands().keys("%s*".formatted(table)).toArray(String[]::new);
                if (keyList.length > 0) {
                    redisDriver.getCommands().del(keyList);
                }

                redisDriver.disconnect();
            }
        }
    }

    private void awaitPostgres(final PostgresDriver postgresDriver) throws InterruptedException {
        final long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();

        while (System.nanoTime() < deadline) {
            try {
                postgresDriver.getDslContext().fetchValue("select 1");
                return;
            } catch (final RuntimeException _) {
                Thread.sleep(250L);
            }
        }

        throw new IllegalStateException("PostgreSQL did not recover within 30 seconds");
    }

    private void awaitRedis(final RedisDriver redisDriver) throws InterruptedException {
        final long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();

        while (System.nanoTime() < deadline) {
            try {
                if ("PONG".equals(redisDriver.getCommands().ping())) {
                    return;
                }
            } catch (final RuntimeException _) {
                Thread.sleep(250L);
            }
        }

        throw new IllegalStateException("Redis did not recover within 30 seconds");
    }

    private void runDocker(final String action, final String container) throws Exception {
        final Process process = new ProcessBuilder("docker", action, container).redirectErrorStream(true).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("docker %s timed out for %s".formatted(action, container));
        }

        if (process.exitValue() != 0) {
            throw new IllegalStateException(new String(process.getInputStream().readAllBytes()));
        }
    }

    private int getOutboxCount(final PostgresDriver postgresDriver, final UUID id) {
        return postgresDriver.getDslContext().fetchCount(
                DSL.table(DSL.name("foundation_cache_invalidation_outbox")),
                DSL.field(DSL.name("entity_id"), UUID.class).eq(id)
        );
    }
}
