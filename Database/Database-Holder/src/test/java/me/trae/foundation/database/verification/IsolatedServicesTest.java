package me.trae.foundation.database.verification;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.entity.EntityInstantiator;
import me.trae.foundation.database.api.event.ChangeEvent;
import me.trae.foundation.database.api.exception.VersionConflictException;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.property.PropertyRegistry;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.WriteResult;
import me.trae.foundation.database.api.write.change.Change;
import me.trae.foundation.database.api.write.change.ChangeType;
import me.trae.foundation.database.postgres.driver.PostgresDriver;
import me.trae.foundation.database.postgres.driver.PostgresSettings;
import me.trae.foundation.database.postgres.repository.PostgresRepository;
import me.trae.foundation.database.postgres.schema.PostgresSynchronizer;
import me.trae.foundation.database.redis.codec.EntityCodec;
import me.trae.foundation.database.redis.driver.RedisDriver;
import me.trae.foundation.database.redis.driver.RedisSettings;
import me.trae.foundation.database.redis.event.RedisEvents;
import me.trae.foundation.database.redis.storage.RedisEntityStorage;
import me.trae.foundation.database.redis.write.RedisEntityWriter;
import me.trae.foundation.database.tenant.TenantScope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
public final class IsolatedServicesTest {

    private static final Property<Probe, String> NAME = Property.persistent(Probe.class, "name", String.class, Probe::getName, Probe::setName);

    @Container
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withEnv(Map.of("POSTGRES_DB", "isolated", "POSTGRES_USER", "isolated", "POSTGRES_PASSWORD", "isolated"))
            .withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));
    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static PostgresDriver postgresDriver;

    private static RedisDriver redisDriver;

    private static ProbeRepository probeRepository;

    @BeforeAll
    static void connect() {
        postgresDriver = new PostgresDriver(new PostgresSettings(POSTGRES.getHost(), POSTGRES.getMappedPort(5432), "isolated", "isolated", "isolated", 4));
        redisDriver = new RedisDriver(new RedisSettings(REDIS.getHost(), REDIS.getMappedPort(6379), null, 0, Duration.ofSeconds(5)));

        postgresDriver.connect();
        redisDriver.connect();

        probeRepository = new ProbeRepository(postgresDriver);

        new PostgresSynchronizer(postgresDriver).synchronize(probeRepository.getStatements());
    }

    @AfterAll
    static void disconnect() {
        if (redisDriver != null) {
            redisDriver.disconnect();
        }

        if (postgresDriver != null) {
            postgresDriver.disconnect();
        }
    }

    @BeforeEach
    void reset() {
        postgresDriver.getDslContext().execute("TRUNCATE isolated_probe");
        redisDriver.getCommands().flushdb();
    }

    @Test
    void publishedPayloadContainsVersionOriginKindChanges() throws Exception {
        final Probe probe = this.seedRedis();
        final RedisEntityStorage<Probe> redisEntityStorage = this.storage();
        final LinkedBlockingQueue<String> payloadQueue = new LinkedBlockingQueue<>();

        redisDriver.subscribe(Map.of(redisEntityStorage.channel(probe.getId()), payloadQueue::add));

        try {
            final WriteResult writeResult = this.writer().apply(

                    redisEntityStorage.path(probe.getId()),
                    redisEntityStorage.channel(probe.getId()),
                    this.write(probe.getId(), WriteKind.UPDATE, 0L, "changed"),
                    Duration.ofSeconds(10)
            );

            assertEquals(1L, writeResult.getVersion());
            assertNull(writeResult.getValue());

            final String payload = payloadQueue.poll(5, TimeUnit.SECONDS);

            assertNotNull(payload);

            final String[] lineArray = payload.split("\n");
            final ChangeEvent changeEvent = RedisEvents.decode(new EntityKey("isolated_probe", null, probe.getId()), payload);

            assertEquals("1", lineArray[0]);
            assertEquals(redisDriver.getInstanceId(), lineArray[1]);
            assertEquals("UPDATE", lineArray[2]);
            assertEquals(WriteKind.UPDATE, changeEvent.getKind());
            assertEquals("changed", changeEvent.getChanges().getFirst().getValue());
        } finally {
            redisDriver.unsubscribe(redisEntityStorage.channel(probe.getId()));
        }
    }

    @Test
    void deletePayloadCarriesDeleteKind() throws Exception {
        final Probe probe = this.seedRedis();
        final RedisEntityStorage<Probe> redisEntityStorage = this.storage();
        final LinkedBlockingQueue<String> payloadQueue = new LinkedBlockingQueue<>();

        redisDriver.subscribe(Map.of(redisEntityStorage.channel(probe.getId()), payloadQueue::add));

        try {
            this.writer().delete(redisEntityStorage.path(probe.getId()), redisEntityStorage.channel(probe.getId()), 5L);

            final String payload = payloadQueue.poll(5, TimeUnit.SECONDS);

            assertNotNull(payload);

            final ChangeEvent changeEvent = RedisEvents.decode(new EntityKey("isolated_probe", null, probe.getId()), payload);

            assertEquals(WriteKind.DELETE, changeEvent.getKind());
            assertEquals(5L, changeEvent.getVersion());
            assertTrue(changeEvent.getChanges().isEmpty());
            assertEquals(redisDriver.getInstanceId(), RedisEvents.origin(payload));
            assertFalse(redisEntityStorage.contains(probe.getId()));
        } finally {
            redisDriver.unsubscribe(redisEntityStorage.channel(probe.getId()));
        }
    }

    @Test
    void stalePostgresDeleteThrowsAndPreservesRow() {
        final UUID id = UUID.randomUUID();

        final long savedVersion = probeRepository.apply(this.write(id, WriteKind.SAVE, 0L, "seed")).getVersion();

        assertEquals(savedVersion + 1L, probeRepository.apply(this.write(id, WriteKind.UPDATE, savedVersion, "updated")).getVersion());
        assertThrows(VersionConflictException.class, () -> probeRepository.apply(this.write(id, WriteKind.DELETE, savedVersion, null)));
        assertEquals("updated", probeRepository.findById(id).orElseThrow().getName());
    }

    @Test
    void matchingVersionPostgresDeleteRemovesRow() {
        final UUID id = UUID.randomUUID();

        final long savedVersion = probeRepository.apply(this.write(id, WriteKind.SAVE, 0L, "seed")).getVersion();
        final long updatedVersion = probeRepository.apply(this.write(id, WriteKind.UPDATE, savedVersion, "updated")).getVersion();

        assertEquals(updatedVersion, probeRepository.apply(this.write(id, WriteKind.DELETE, updatedVersion, null)).getVersion());
        assertTrue(probeRepository.findById(id).isEmpty());
        assertEquals(0L, probeRepository.count(Query.all()));
    }

    @Test
    void redisVersionAccessorReadsHashAndDefaultsToZero() {
        final Probe probe = this.seedRedis();
        final RedisEntityStorage<Probe> redisEntityStorage = this.storage();

        assertEquals(0L, redisEntityStorage.version(probe.getId()));

        redisDriver.getCommands().hset(redisEntityStorage.path(probe.getId()), "version", "5");

        assertEquals(5L, redisEntityStorage.version(probe.getId()));
    }

    @Test
    void nullAndEmptyRemainDistinctInCurrentEventFormat() {
        final EntityKey entityKey = new EntityKey("isolated_probe", null, UUID.randomUUID());

        for (final String value : new String[]{null, ""}) {
            final String payload = "1\nexternal-origin\nUPDATE\n%s".formatted(RedisEvents.encode(List.of(new Change(NAME.getColumn(), ChangeType.SET, value))));

            assertEquals(value, RedisEvents.decode(entityKey, payload).getChanges().getFirst().getValue());
        }
    }

    private Probe seedRedis() {
        final Probe probe = new Probe(UUID.randomUUID());

        probe.setName("seed");
        this.storage().put(probe.getId(), probe);

        return probe;
    }

    private RedisEntityStorage<Probe> storage() {
        return new RedisEntityStorage<>(
                new Namespace<>("isolated_probe", Probe.class, UUID::toString, null, Duration.ofSeconds(10)),
                redisDriver,
                new EntityCodec<>(new EntityInstantiator<>(Probe.class), PropertyRegistry.getProperties(Probe.class))
        );
    }

    private RedisEntityWriter writer() {
        return new RedisEntityWriter(redisDriver, PropertyRegistry.getProperties(Probe.class));
    }

    private Write write(final UUID id, final WriteKind kind, final long version, final String name) {
        return new Write(
                new EntityKey("isolated_probe", null, id),
                kind,
                kind == WriteKind.DELETE ? List.of() : List.of(new Change(NAME.getColumn(), ChangeType.SET, name)),
                version
        );
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    @Setter
    public static final class Probe implements Entity {

        @EqualsAndHashCode.Include
        private final UUID id;

        private String name;
    }

    private static final class ProbeRepository extends PostgresRepository<Probe> {

        private ProbeRepository(final PostgresDriver postgresDriver) {
            super(Probe.class, "isolated_probe", postgresDriver, new TenantScope(null));
        }
    }
}