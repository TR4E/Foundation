package me.trae.foundation.database.deep;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.entity.EntityInstantiator;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.property.PropertyRegistry;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.api.write.WriteGate;
import me.trae.foundation.database.holder.write.EntityWriter;
import me.trae.foundation.database.postgres.repository.PostgresRepository;
import me.trae.foundation.database.postgres.row.PostgresRow;
import me.trae.foundation.database.postgres.schema.PostgresSynchronizer;
import me.trae.foundation.database.postgres.schema.SchemaDefinition;
import me.trae.foundation.database.postgres.write.PostgresWrite;
import me.trae.foundation.database.redis.codec.EntityCodec;
import me.trae.foundation.database.redis.storage.RedisEntityStorage;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public final class AuditDataTest extends RuntimeFixture {

    private static final Property<DataEntity, BigDecimal> AMOUNT = Property.persistent(DataEntity.class, "amount", BigDecimal.class, DataEntity::getAmount, DataEntity::setAmount);

    private static final Property<DataEntity, byte[]> BYTES = Property.persistent(DataEntity.class, "payload", byte[].class, DataEntity::getBytes, DataEntity::setBytes);

    private static final Property<DataEntity, String> LABEL = Property.persistent(DataEntity.class, "label", String.class, DataEntity::getLabel, DataEntity::setLabel);

    private static final Property<DataEntity, TypeEdgesTest.Label> CUSTOM = Property.persistent(DataEntity.class, "custom_value", TypeEdgesTest.Label.class, DataEntity::getCustom, DataEntity::setCustom, new TypeEdgesTest.LabelConverter());

    @Test
    void decimalRoundTripsInRedisAndClearsToNull() {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            final DataEntity dataEntity = new DataEntity(UUID.randomUUID());
            final RedisEntityStorage<DataEntity> redisEntityStorage = this.storage(node);

            dataEntity.setLabel("");
            dataEntity.setAmount(new BigDecimal("1234567890.1234500"));
            redisEntityStorage.put(dataEntity.getId(), dataEntity);

            assertEquals(dataEntity.getAmount(), redisEntityStorage.get(dataEntity.getId()).orElseThrow().getAmount());
            dataEntity.setAmount(null);
            redisEntityStorage.put(dataEntity.getId(), dataEntity);
            assertNull(redisEntityStorage.get(dataEntity.getId()).orElseThrow().getAmount());
        }
    }

    @Test
    void decimalHasPostgresSchemaAndRoundTrips() {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            final DataRepository dataRepository = new DataRepository(node);
            final PostgresSynchronizer postgresSynchronizer = new PostgresSynchronizer(node.getPostgresDriver());

            assertDoesNotThrow(() -> postgresSynchronizer.synchronize(dataRepository.getStatements()), "A native decimal property should support PostgreSQL DDL");

            final EntityWriter<DataEntity> entityWriter = new EntityWriter<>(
                    dataRepository.getTable(),
                    null,
                    PropertyRegistry.getProperties(DataEntity.class),
                    dataRepository::apply,
                    _ -> {},
                    _ -> {},
                    new WriteGate(),
                    true,
                    _ -> true,
                    _ -> null
            );
            final DataEntity dataEntity = new DataEntity(UUID.randomUUID());

            dataEntity.setAmount(new BigDecimal("1234567890.1234500"));
            entityWriter.save(dataEntity);
            assertEquals(dataEntity.getAmount(), dataRepository.findById(dataEntity.getId()).orElseThrow().getAmount());
            entityWriter.set(dataEntity, AMOUNT, null);
            assertNull(dataRepository.findById(dataEntity.getId()).orElseThrow().getAmount());
        }
    }

    @Test
    void clearingLastRedisFieldPreservesVersionedHash() {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            final RedisEntityStorage<DataEntity> redisEntityStorage = this.storage(node);
            final DataEntity dataEntity = new DataEntity(UUID.randomUUID());

            dataEntity.setLabel("");
            redisEntityStorage.put(dataEntity.getId(), dataEntity);
            node.getRedisDriver().getCommands().hset(redisEntityStorage.path(dataEntity.getId()), "version", "1");
            dataEntity.setLabel(null);

            assertDoesNotThrow(() -> redisEntityStorage.put(dataEntity.getId(), dataEntity));
            assertNull(redisEntityStorage.get(dataEntity.getId()).orElseThrow().getLabel());
            assertEquals(1L, redisEntityStorage.version(dataEntity.getId()));
        }
    }

    @Test
    void emptyByteArrayRoundTripsAsEmptyInRedis() {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            final RedisEntityStorage<DataEntity> redisEntityStorage = this.storage(node);
            final DataEntity dataEntity = new DataEntity(UUID.randomUUID());

            dataEntity.setBytes(new byte[0]);
            redisEntityStorage.put(dataEntity.getId(), dataEntity);
            assertEquals(0, assertDoesNotThrow(() -> redisEntityStorage.get(dataEntity.getId()).orElseThrow()).getBytes().length);
        }
    }

    @Test
    void emptyByteArrayRoundTripsAsEmptyInPostgres() {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            final List<Property<?, ?>> propertyList = List.of(BYTES);
            final String tableName = "audit_bytea";
            final PostgresSynchronizer postgresSynchronizer = new PostgresSynchronizer(node.getPostgresDriver());

            postgresSynchronizer.synchronize(SchemaDefinition.statements(tableName, false, propertyList, Map.of()));

            final org.jooq.DSLContext dslContext = node.getPostgresDriver().getDslContext();
            final org.jooq.Table<?> table = DSL.table(DSL.name(tableName));
            final PostgresRow<DataEntity> postgresRow = new PostgresRow<>(new EntityInstantiator<>(DataEntity.class), propertyList);
            final EntityWriter<DataEntity> entityWriter = new EntityWriter<>(
                    tableName,
                    null,
                    propertyList,
                    write -> PostgresWrite.apply(dslContext, table, propertyList, write),
                    _ -> {},
                    _ -> {},
                    new WriteGate(),
                    true,
                    _ -> true,
                    _ -> null
            );
            final DataEntity dataEntity = new DataEntity(UUID.randomUUID());

            dataEntity.setBytes(new byte[0]);
            entityWriter.save(dataEntity);
            assertEquals(0, postgresRow.map(dslContext.selectFrom(table).where(PostgresWrite.ID.eq(dataEntity.getId())).fetchSingle()).getBytes().length);
            entityWriter.set(dataEntity, BYTES, null);
            assertNull(postgresRow.map(dslContext.selectFrom(table).where(PostgresWrite.ID.eq(dataEntity.getId())).fetchSingle()).getBytes());
        }
    }

    @Test
    void decimalRoundTripsThroughAnExistingNumericColumn() {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            final DataRepository dataRepository = new DataRepository(node, "audit_existing_numeric");
            final EntityWriter<DataEntity> entityWriter = new EntityWriter<>(
                    dataRepository.getTable(),
                    null,
                    PropertyRegistry.getProperties(DataEntity.class),
                    dataRepository::apply,
                    _ -> {},
                    _ -> {},
                    new WriteGate(),
                    true,
                    _ -> true,
                    _ -> null
            );
            final DataEntity dataEntity = new DataEntity(UUID.randomUUID());

            node.getPostgresDriver().getDslContext().execute("CREATE TABLE audit_existing_numeric (id uuid PRIMARY KEY, version bigint NOT NULL DEFAULT 0, amount numeric, payload bytea, label text, custom_value text)");
            dataEntity.setAmount(new BigDecimal("1234567890.1234500"));
            dataEntity.setLabel("");
            dataEntity.setCustom(new TypeEdgesTest.Label(""));
            entityWriter.save(dataEntity);

            final DataEntity savedEntity = dataRepository.findById(dataEntity.getId()).orElseThrow();

            assertEquals(dataEntity.getAmount(), savedEntity.getAmount());
            assertEquals("", savedEntity.getLabel());
            assertEquals("", savedEntity.getCustom().getValue());
            this.storage(node).put(dataEntity.getId(), dataEntity);
            assertEquals("", this.storage(node).get(dataEntity.getId()).orElseThrow().getCustom().getValue());
            entityWriter.set(dataEntity, AMOUNT, null);
            entityWriter.set(dataEntity, CUSTOM, null);
            assertNull(dataRepository.findById(dataEntity.getId()).orElseThrow().getAmount());
            assertNull(dataRepository.findById(dataEntity.getId()).orElseThrow().getCustom());
            this.storage(node).put(dataEntity.getId(), dataEntity);
            assertNull(this.storage(node).get(dataEntity.getId()).orElseThrow().getCustom());
        }
    }

    private RedisEntityStorage<DataEntity> storage(final Node node) {
        return new RedisEntityStorage<>(new Namespace<>("audit_data", DataEntity.class, UUID::toString, null, Duration.ofSeconds(30)), node.getRedisDriver(), new EntityCodec<>(new EntityInstantiator<>(DataEntity.class), PropertyRegistry.getProperties(DataEntity.class)));
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    @Setter
    public static final class DataEntity implements Entity {

        @EqualsAndHashCode.Include
        private final UUID id;

        private BigDecimal amount;

        private byte[] bytes;

        private String label;

        private TypeEdgesTest.Label custom;
    }

    private static final class DataRepository extends PostgresRepository<DataEntity> {

        private DataRepository(final Node node) {
            this(node, "audit_data");
        }

        private DataRepository(final Node node, final String table) {
            super(DataEntity.class, table, node.getPostgresDriver(), node.getCoreDatabase().getTenantScope(), AuditDataTest.class);
        }
    }
}