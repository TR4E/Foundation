package me.trae.foundation.database.deep;

import com.zaxxer.hikari.HikariDataSource;
import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.batch.BatchQueue;
import me.trae.foundation.database.batch.BatchScheduler;
import me.trae.foundation.database.postgres.driver.PostgresDriver;
import me.trae.foundation.database.postgres.driver.PostgresSettings;
import me.trae.foundation.database.redis.script.RedisScript;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ServiceFaultsTest extends RuntimeFixture {

    @Test
    void scriptFlushActuallyExercisesNoScriptReload() throws Exception {
        try (final Node node = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final RedisScript redisScript = new RedisScript("return redis.call('INCR', KEYS[1])");
            final String key = "reload:%s".formatted(UUID.randomUUID());

            assertEquals(1L, redisScript.<Long>execute(node.getRedisDriver(), ScriptOutputType.INTEGER, new String[]{key}));

            final Field field = RedisScript.class.getDeclaredField("sha");

            field.setAccessible(true);

            final String sha = (String) field.get(redisScript);

            node.getRedisDriver().getCommands().scriptFlush();
            assertThrows(RedisNoScriptException.class, () -> node.getRedisDriver().getCommands().evalsha(sha, ScriptOutputType.INTEGER, key));
            assertEquals(2L, redisScript.<Long>execute(node.getRedisDriver(), ScriptOutputType.INTEGER, new String[]{key}), "Same RedisScript instance must reload and retry after NOSCRIPT");
            assertTrue(node.getRedisDriver().getCommands().scriptExists(sha).getFirst());
        }
    }

    @Test
    void connectionKilledDuringQueuedScriptHasNoPartialWrite() throws Exception {
        try (final Node node = new Node(AllBean.class, Duration.ofSeconds(30));
             final var executorService = Executors.newSingleThreadExecutor()) {
            final RedisScript redisScript = new RedisScript("redis.call('HINCRBY', KEYS[1], 'left', 1); redis.call('HINCRBY', KEYS[1], 'right', 1); return 1");
            final String key = "disconnect:%s".formatted(UUID.randomUUID());

            node.getRedisDriver().getCommands().hset(key, Map.of("left", "0", "right", "0"));
            node.getRedisDriver().getCommands().scriptLoad("redis.call('HINCRBY', KEYS[1], 'left', 1); redis.call('HINCRBY', KEYS[1], 'right', 1); return 1");
            assertEquals(0, REDIS.execInContainer("redis-cli", "CLIENT", "PAUSE", "800", "WRITE").getExitCode());

            final var future = executorService.submit(() -> redisScript.<Long>execute(node.getRedisDriver(), ScriptOutputType.INTEGER, new String[]{key}));

            Thread.sleep(100L);
            assertFalse(future.isDone(), "Script must still be in flight when the socket is closed");

            final Field field = node.getRedisDriver().getClass().getDeclaredField("connection");

            field.setAccessible(true);
            ((StatefulRedisConnection<?, ?>) field.get(node.getRedisDriver())).close();
            assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS), "Closing the in-flight socket must report failure to the caller");
            node.getRedisDriver().disconnect();
            node.getRedisDriver().connect();

            final Map<String, String> fieldMap = node.getRedisDriver().getCommands().hgetall(key);
            final long left = Long.parseLong(fieldMap.get("left"));
            final long right = Long.parseLong(fieldMap.get("right"));

            System.out.println("EDGE|redis_disconnect|left=%s|right=%s|caller=exception".formatted(left, right));
            assertEquals(left, right, "Disconnected scripted write must remain atomic");
            assertTrue(left == 0L || left == 1L, "A single script invocation must not be replayed twice");
            assertEquals(1L, redisScript.<Long>execute(node.getRedisDriver(), ScriptOutputType.INTEGER, new String[]{key}));
        }
    }

    @Test
    void stoppedPostgresRequeuesWritesAndHikariRecoversOnRestart() throws Exception {
        try (final GenericContainer<?> genericContainer = new RecoveryContainer(this.availablePort())
                .withEnv(Map.of("POSTGRES_DB", "deep", "POSTGRES_USER", "deep", "POSTGRES_PASSWORD", "deep"))
                .withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));
             final var executorService = Executors.newFixedThreadPool(4)) {
            genericContainer.start();

            final PostgresDriver postgresDriver = new PostgresDriver(new PostgresSettings(genericContainer.getHost(), genericContainer.getMappedPort(5432), "deep", "deep", "deep", 4));

            postgresDriver.connect();

            try {
                final ItemRepository itemRepository = new ItemRepository(postgresDriver, new me.trae.foundation.database.tenant.TenantScope(null));
                final Field field = PostgresDriver.class.getDeclaredField("dataSource");

                field.setAccessible(true);
                ((HikariDataSource) field.get(postgresDriver)).setConnectionTimeout(1000L);
                new me.trae.foundation.database.postgres.schema.PostgresSynchronizer(postgresDriver).synchronize(itemRepository.getStatements());

                final BatchQueue batchQueue = new BatchQueue();
                final BatchScheduler batchScheduler = new BatchScheduler(batchQueue, itemRepository::apply, Duration.ofHours(1));
                final UUID firstId = UUID.randomUUID();
                final UUID secondId = UUID.randomUUID();
                final List<java.util.concurrent.Future<Boolean>> futureList = IntStream.range(0, 4)
                        .mapToObj(_ -> executorService.submit(() -> {
                            try {
                                postgresDriver.getDslContext().execute("SELECT pg_sleep(3)");
                                return false;
                            } catch (final RuntimeException exception) {
                                return true;
                            }
                        }))
                        .toList();

                Thread.sleep(150L);
                DockerClientFactory.instance().client().stopContainerCmd(genericContainer.getContainerId()).withTimeout(1).exec();
                batchQueue.add(write(firstId, WriteKind.SAVE, 0L, set("name", "pending-one")));
                batchQueue.add(write(secondId, WriteKind.SAVE, 0L, set("name", "pending-two")));
                batchScheduler.flush();
                assertEquals(2, batchQueue.size(), "Failed Postgres writes must stay queued");

                for (final var future : futureList) {
                    assertTrue(future.get(5, TimeUnit.SECONDS), "An active query must observe the container outage");
                }

                DockerClientFactory.instance().client().startContainerCmd(genericContainer.getContainerId()).exec();
                final long recoveryDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
                final java.util.concurrent.atomic.AtomicBoolean recovered = new java.util.concurrent.atomic.AtomicBoolean();

                while (!recovered.get() && System.nanoTime() < recoveryDeadline) {
                    try {
                        recovered.set(postgresDriver.getDslContext().fetchOne("SELECT 1").get(0, Integer.class) == 1);
                    } catch (final RuntimeException ignored) {
                        Thread.sleep(250L);
                    }
                }

                assertTrue(recovered.get(), "Hikari must recover within sixty seconds without reconnecting the driver");
                batchScheduler.flush();

                assertEquals(0, batchQueue.size(), "Recovered Hikari pool must drain pending writes");
                assertEquals("pending-one", itemRepository.findById(firstId).orElseThrow().getName());
                assertEquals("pending-two", itemRepository.findById(secondId).orElseThrow().getName());
                System.out.println("EDGE|postgres_restart|queued_during_outage=2|queued_after_recovery=0|driver_reconnect_called=false");
                batchScheduler.stop();
            } finally {
                postgresDriver.disconnect();
            }
        }
    }

    @Test
    void twoJvmBootsSerializeDdlOnAnEmptyDatabase() throws Exception {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30));
             final var executorService = Executors.newFixedThreadPool(2)) {
            final String database = "boot_%s".formatted(UUID.randomUUID().toString().replace("-", ""));

            node.getPostgresDriver().getDslContext().execute("CREATE DATABASE %s".formatted(database));

            final Process firstProcess = this.bootProcess(database);
            final Process secondProcess = this.bootProcess(database);

            try {
                final var firstReady = executorService.submit(() -> this.ready(firstProcess));
                final var secondReady = executorService.submit(() -> this.ready(secondProcess));

                assertTrue(firstReady.get(20, TimeUnit.SECONDS));
                assertTrue(secondReady.get(20, TimeUnit.SECONDS));
                firstProcess.getOutputStream().write(1);
                firstProcess.getOutputStream().flush();
                secondProcess.getOutputStream().write(1);
                secondProcess.getOutputStream().flush();

                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                final java.util.concurrent.atomic.AtomicBoolean observed = new java.util.concurrent.atomic.AtomicBoolean();

                while (!observed.get() && System.nanoTime() < deadline) {
                    observed.set(node.getPostgresDriver().getDslContext().fetchExists(org.jooq.impl.DSL.selectOne().from("pg_locks").where("locktype = 'advisory' AND objid = 7041975 AND NOT granted")));
                    Thread.sleep(10L);
                }

                assertTrue(firstProcess.waitFor(20, TimeUnit.SECONDS));
                assertTrue(secondProcess.waitFor(20, TimeUnit.SECONDS));
                assertEquals(0, firstProcess.exitValue(), "First JVM DDL boot failed");
                assertEquals(0, secondProcess.exitValue(), "Second JVM DDL boot failed");
                assertTrue(observed.get(), "A JVM must demonstrably wait on the DDL advisory lock");
                System.out.println("EDGE|two_jvm_boot|exit_a=0|exit_b=0|advisory_wait_observed=true");
            } finally {
                firstProcess.destroyForcibly();
                secondProcess.destroyForcibly();
                node.getPostgresDriver().getDslContext().execute("DROP DATABASE %s WITH (FORCE)".formatted(database));
            }
        }
    }

    private int availablePort() throws Exception {
        try (final java.net.ServerSocket serverSocket = new java.net.ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    private Process bootProcess(final String database) throws Exception {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(), "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), DdlBootProcess.class.getName(), POSTGRES.getHost(), POSTGRES.getMappedPort(5432).toString(), database).redirectError(ProcessBuilder.Redirect.INHERIT).start();
    }

    private boolean ready(final Process process) throws Exception {
        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        return "READY".equals(bufferedReader.readLine());
    }

    private static final class RecoveryContainer extends GenericContainer<RecoveryContainer> {

        private RecoveryContainer(final int port) {
            super(DockerImageName.parse("postgres:17-alpine"));
            this.addFixedExposedPort(port, 5432);
        }
    }
}