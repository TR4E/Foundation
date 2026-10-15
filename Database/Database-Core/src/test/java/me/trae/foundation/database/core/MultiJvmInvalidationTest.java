package me.trae.foundation.database.core;

import me.trae.foundation.database.core.batch.PendingWriteStore;
import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.core.driver.PostgresSettings;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.driver.RedisSettings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MultiJvmInvalidationTest {

    private final Map<Process, BufferedReader> readerMap = new ConcurrentHashMap<>();

    @Test
    void separateJvmCachesAreInvalidatedAfterCommittedWrite() throws Exception {
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
        final String table = "multi_jvm_%s".formatted(UUID.randomUUID().toString().replace("-", ""));

        final UUID id = UUID.randomUUID();

        final CoreDatabase coreDatabase = new CoreDatabase(postgresDriver, redisDriver, null, Duration.ofHours(1), 100, PendingWriteStore.DEFAULT_MAX_PENDING_WRITES);

        final List<Process> processList = new ArrayList<>();

        try {
            postgresDriver.connect();
            redisDriver.connect();
        } catch (final RuntimeException exception) {
            postgresDriver.disconnect();
            redisDriver.disconnect();
            Assumptions.abort("PostgreSQL or Redis is not reachable: %s".formatted(exception.getMessage()));
        }

        try {
            final WorkerRepository repository = new WorkerRepository(coreDatabase, table);

            final WorkerHolder holder = new WorkerHolder(repository);
            coreDatabase.start();

            final WorkerEntity entity = new WorkerEntity(id);
            entity.setValue("before");
            holder.save(entity);
            coreDatabase.getBatchQueue().flush();

            for (int index = 0; index < 5; index++) {
                final Process process = this.startWorker(table, id, false, null);
                processList.add(process);
                assertEquals("READY|before", this.readMessage(process, "READY|"));
            }

            this.send(processList.getFirst(), "DISCONNECT");
            assertEquals("ACK|disconnected", this.readMessage(processList.getFirst(), "ACK|"));

            entity.setValue("after");
            holder.update(entity);
            coreDatabase.getBatchQueue().flush();

            this.send(processList.getFirst(), "RECONNECT");
            assertEquals("ACK|reconnected", this.readMessage(processList.getFirst(), "ACK|"));

            for (final Process process : processList) {
                this.send(process, "READ|after");
            }

            for (final Process process : processList) {
                assertEquals("VALUE|after", this.readMessage(process, "VALUE|"));
            }
        } finally {
            processList.forEach(this::stopWorker);
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

    @Test
    void concurrentCreatesForOneUuidFromSeparateJvmsPreserveOneInsertedValue() throws Exception {
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
        final String table = "multi_jvm_create_%s".formatted(UUID.randomUUID().toString().replace("-", ""));

        final String barrierKey = "%s:create-barrier".formatted(table);

        final UUID id = UUID.randomUUID();

        final CoreDatabase coreDatabase = new CoreDatabase(postgresDriver, redisDriver, null, Duration.ofHours(1), 100, PendingWriteStore.DEFAULT_MAX_PENDING_WRITES);

        final List<Process> processList = new ArrayList<>();

        try {
            postgresDriver.connect();
            redisDriver.connect();
        } catch (final RuntimeException exception) {
            postgresDriver.disconnect();
            redisDriver.disconnect();
            Assumptions.abort("PostgreSQL or Redis is not reachable: %s".formatted(exception.getMessage()));
        }

        try {
            final WorkerRepository repository = new WorkerRepository(coreDatabase, table);
            coreDatabase.start();

            for (int index = 0; index < 2; index++) {
                final Process process = this.startWorker(table, id, true, null);
                processList.add(process);
                assertEquals("READY|deferred", this.readMessage(process, "READY|"));
                this.send(process, "CREATE|writer-%s|%s".formatted(index, barrierKey));
                assertEquals("PREPARED|writer-%s".formatted(index), this.readMessage(process, "PREPARED|"));
            }

            redisDriver.getCommands().set(barrierKey, "go");

            final List<String> storedValueList = new ArrayList<>();
            for (final Process process : processList) {
                storedValueList.add(this.readMessage(process, "CREATED|").substring("CREATED|".length()));
            }

            assertTrue(List.of("writer-0", "writer-1").contains(repository.findById(id).orElseThrow().getValue()));
            assertTrue(storedValueList.stream().allMatch(List.of("writer-0", "writer-1")::contains));
        } finally {
            processList.forEach(this::stopWorker);
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

    @Test
    void multipleJvmColdMissesShareOnePostgresLoad() throws Exception {
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
        final String table = "multi_jvm_%s".formatted(UUID.randomUUID().toString().replace("-", ""));

        final String countKey = "%s:loader-count".formatted(table);

        final UUID id = UUID.randomUUID();

        final int jvmCount = Integer.getInteger("multiJvm.count", 5);

        final CoreDatabase coreDatabase = new CoreDatabase(postgresDriver, redisDriver, null, Duration.ofHours(1), 100, PendingWriteStore.DEFAULT_MAX_PENDING_WRITES);

        final List<Process> processList = new ArrayList<>();

        assertTrue(jvmCount >= 2 && jvmCount <= 100, "multiJvm.count must be between 2 and 100");

        try {
            postgresDriver.connect();
            redisDriver.connect();
        } catch (final RuntimeException exception) {
            postgresDriver.disconnect();
            redisDriver.disconnect();
            Assumptions.abort("PostgreSQL or Redis is not reachable: %s".formatted(exception.getMessage()));
        }

        try {
            final WorkerRepository repository = new WorkerRepository(coreDatabase, table);

            final WorkerHolder holder = new WorkerHolder(repository);
            coreDatabase.start();

            final WorkerEntity entity = new WorkerEntity(id);
            entity.setValue("cold");
            holder.save(entity);
            coreDatabase.getBatchQueue().flush();

            for (int index = 0; index < jvmCount; index++) {
                final Process process = this.startWorker(table, id, true, countKey);
                processList.add(process);
                assertEquals("READY|deferred", this.readMessage(process, "READY|"));
            }

            for (final Process process : processList) {
                this.send(process, "READ|cold");
            }

            for (final Process process : processList) {
                assertEquals("VALUE|cold", this.readMessage(process, "VALUE|"));
            }

            assertEquals("1", redisDriver.getCommands().get(countKey));
        } finally {
            processList.forEach(this::stopWorker);
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

    @Test
    void twoJvmWritesToTheSameEntityBothCommitWithLastCommitWinning() throws Exception {
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
        final String table = "multi_jvm_%s".formatted(UUID.randomUUID().toString().replace("-", ""));

        final String barrierKey = "%s:write-barrier".formatted(table);

        final UUID id = UUID.randomUUID();

        final CoreDatabase coreDatabase = new CoreDatabase(postgresDriver, redisDriver, null, Duration.ofHours(1), 100, PendingWriteStore.DEFAULT_MAX_PENDING_WRITES);

        final List<Process> processList = new ArrayList<>();

        try {
            postgresDriver.connect();
            redisDriver.connect();
        } catch (final RuntimeException exception) {
            postgresDriver.disconnect();
            redisDriver.disconnect();
            Assumptions.abort("PostgreSQL or Redis is not reachable: %s".formatted(exception.getMessage()));
        }

        try {
            final WorkerRepository repository = new WorkerRepository(coreDatabase, table);

            final WorkerHolder holder = new WorkerHolder(repository);
            coreDatabase.start();

            final WorkerEntity entity = new WorkerEntity(id);
            entity.setValue("initial");
            holder.save(entity);
            coreDatabase.getBatchQueue().flush();

            for (int index = 0; index < 2; index++) {
                final Process process = this.startWorker(table, id, false, null);
                processList.add(process);
                assertEquals("READY|initial", this.readMessage(process, "READY|"));
            }

            this.send(processList.get(0), "PREPARE|writer-a|%s".formatted(barrierKey));
            this.send(processList.get(1), "PREPARE|writer-b|%s".formatted(barrierKey));
            assertEquals("PREPARED|writer-a", this.readMessage(processList.get(0), "PREPARED|"));
            assertEquals("PREPARED|writer-b", this.readMessage(processList.get(1), "PREPARED|"));

            redisDriver.getCommands().set(barrierKey, "go");
            assertEquals("WRITE|writer-a", this.readMessage(processList.get(0), "WRITE|"));
            assertEquals("WRITE|writer-b", this.readMessage(processList.get(1), "WRITE|"));

            final String finalValue = repository.findById(id).orElseThrow().getValue();
            assertTrue(List.of("writer-a", "writer-b").contains(finalValue));
        } finally {
            processList.forEach(this::stopWorker);
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

    private Process startWorker(final String table, final UUID id, final boolean deferred, final String countKey) throws Exception {
        final String javaPath = "%s\\bin\\java.exe".formatted(System.getProperty("java.home"));

        final String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));

        final Process process = new ProcessBuilder(
                javaPath,
                "-Xms32m",
                "-Xmx128m",
                "-cp",
                classPath,
                MultiJvmDatabaseWorker.class.getName(),
                System.getProperty("postgres.host", "localhost"),
                System.getProperty("postgres.port", "5432"),
                System.getProperty("postgres.database", "foundation_test"),
                System.getProperty("postgres.username", "postgres"),
                System.getProperty("postgres.password", "postgres"),
                System.getProperty("redis.host", "localhost"),
                System.getProperty("redis.port", "6379"),
                System.getProperty("redis.password", ""),
                System.getProperty("redis.database", "0"),
                table,
                id.toString(),
                String.valueOf(deferred),
                countKey == null ? "" : countKey,
                String.valueOf(Integer.getInteger("multiJvm.postgresPoolSize", 4))
        ).redirectErrorStream(true).start();

        return process;
    }

    private String readMessage(final Process process, final String prefix) throws Exception {
        final BufferedReader reader = this.getReader(process);

        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

        while (System.nanoTime() < deadline) {
            if (!reader.ready()) {
                if (!process.isAlive()) {
                    throw new IllegalStateException("Worker JVM exited with code %s".formatted(process.exitValue()));
                }

                Thread.sleep(10L);
                continue;
            }

            final String line = reader.readLine();
            if (line == null) {
                throw new IllegalStateException("Worker JVM exited with code %s".formatted(process.waitFor()));
            }

            if (line.startsWith(prefix)) {
                return line;
            }
        }

        throw new IllegalStateException("Timed out waiting for %s from worker JVM".formatted(prefix));
    }

    private BufferedReader getReader(final Process process) {
        return this.readerMap.computeIfAbsent(process, _ -> new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)));
    }

    private void send(final Process process, final String command) throws Exception {
        final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        writer.write(command);
        writer.newLine();
        writer.flush();
    }

    private void stopWorker(final Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }

        try {
            this.send(process, "STOP");
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (final Exception exception) {
            process.destroyForcibly();
        }
    }
}