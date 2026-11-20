package me.trae.foundation.database.deep;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.converter.ValueConverter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.entity.EntityInstantiator;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.property.PropertyRegistry;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.api.write.WriteGate;
import me.trae.foundation.database.holder.write.EntityWriter;
import me.trae.foundation.database.postgres.repository.PostgresRepository;
import me.trae.foundation.database.postgres.schema.PostgresSynchronizer;
import me.trae.foundation.database.redis.codec.EntityCodec;
import me.trae.foundation.database.redis.storage.RedisEntityStorage;
import me.trae.foundation.database.tenant.TenantScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public final class TypeEdgesTest extends RuntimeFixture {

    private static final Property<TypedEntity, Status> STATUS = Property.persistent(TypedEntity.class, "status", Status.class, TypedEntity::getStatus, TypedEntity::setStatus);

    private static final Property<TypedEntity, Instant> INSTANT = Property.persistent(TypedEntity.class, "created", Instant.class, TypedEntity::getInstant, TypedEntity::setInstant);

    private static final Property<TypedEntity, byte[]> BYTES = Property.persistent(TypedEntity.class, "payload", byte[].class, TypedEntity::getBytes, TypedEntity::setBytes);

    private static final Property<TypedEntity, Label> LABEL = Property.persistent(TypedEntity.class, "label", Label.class, TypedEntity::getLabel, TypedEntity::setLabel, new LabelConverter());

    private static final Property<TypedEntity, String> ANCHOR = Property.persistent(TypedEntity.class, "anchor", String.class, TypedEntity::getAnchor, TypedEntity::setAnchor);

    private static final Property<MemoryEntity, String> NOTE = Property.memory(MemoryEntity.class, "note", String.class, MemoryEntity::getNote, MemoryEntity::setNote);

    private static final List<Property<WideEntity, String>> PROPERTY_LIST = IntStream.range(0, 50)
            .mapToObj(index -> Property.persistent(WideEntity.class, "p%s".formatted(index), String.class, wideEntity -> wideEntity.getValueArray()[index], (wideEntity, value) -> wideEntity.getValueArray()[index] = value))
            .toList();

    @ParameterizedTest
    @EnumSource(Backend.class)
    void enumAndNullRoundTrip(final Backend backend) {
        this.roundTrip(backend, STATUS, Status.ACTIVE);
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void instantAndNullRoundTrip(final Backend backend) {
        this.roundTrip(backend, INSTANT, Instant.parse("2026-11-18T01:02:03.123456Z"));
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void byteArrayAndNullRoundTrip(final Backend backend) {
        this.roundTrip(backend, BYTES, new byte[]{0, 1, -1, 42});
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void customConverterAndNullRoundTrip(final Backend backend) {
        this.roundTrip(backend, LABEL, new Label("custom:?"));
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void fiftyPropertiesAndLongStringRoundTrip(final Backend backend) {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            final GenericRepository<WideEntity> genericRepository = new GenericRepository<>(WideEntity.class, "edge_wide", node, TypeEdgesTest.class);
            final Function<WideEntity, WideEntity> roundTrip = this.roundTripFunction(backend, WideEntity.class, genericRepository, node);
            final WideEntity wideEntity = new WideEntity(UUID.randomUUID());

            IntStream.range(0, 50).forEach(index -> wideEntity.getValueArray()[index] = "field-%s".formatted(index));
            wideEntity.getValueArray()[49] = "long-?".repeat(200_000);

            final WideEntity actualWideEntity = assertDoesNotThrow(() -> roundTrip.apply(wideEntity));

            assertEquals(50, PropertyRegistry.getProperties(WideEntity.class).size());
            assertArrayEquals(wideEntity.getValueArray(), actualWideEntity.getValueArray());
        }
    }

    @Test
    void allMemoryPropertiesSaveOnlyIdentityInPostgresAndRoundTripInRedis() {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            final GenericRepository<MemoryEntity> genericRepository = new GenericRepository<>(MemoryEntity.class, "edge_memory", node, TypeEdgesTest.class);
            final MemoryEntity memoryEntity = new MemoryEntity(UUID.randomUUID());

            memoryEntity.setNote("memory-value");

            final MemoryEntity postgresEntity = this.roundTripFunction(Backend.POSTGRES, MemoryEntity.class, genericRepository, node).apply(memoryEntity);
            final MemoryEntity redisEntity = this.roundTripFunction(Backend.REDIS, MemoryEntity.class, genericRepository, node).apply(memoryEntity);

            assertNull(postgresEntity.getNote());
            assertEquals("memory-value", redisEntity.getNote());
            assertEquals(List.of("id", "version"), node.getPostgresDriver().getDslContext().fetch("SELECT column_name FROM information_schema.columns WHERE table_name = 'edge_memory' ORDER BY ordinal_position").getValues(0, String.class));
        }
    }

    private <Value> void roundTrip(final Backend backend, final Property<TypedEntity, Value> property, final Value value) {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            final GenericRepository<TypedEntity> genericRepository = new GenericRepository<>(TypedEntity.class, "edge_types", node, TypeEdgesTest.class);
            final Function<TypedEntity, TypedEntity> roundTrip = this.roundTripFunction(backend, TypedEntity.class, genericRepository, node, List.of(property, ANCHOR));
            final TypedEntity typedEntity = new TypedEntity(UUID.randomUUID());

            assertNull(property.get(assertDoesNotThrow(() -> roundTrip.apply(typedEntity))));
            property.set(typedEntity, value);

            final TypedEntity actualTypedEntity = assertDoesNotThrow(() -> roundTrip.apply(typedEntity), "Non-null %s must round-trip through %s".formatted(property.getColumn(), backend));

            if (value instanceof final byte[] byteArray) {
                assertArrayEquals(byteArray, (byte[]) property.get(actualTypedEntity));
            } else {
                assertEquals(value, property.get(actualTypedEntity));
            }

            property.set(typedEntity, null);
            assertNull(property.get(assertDoesNotThrow(() -> roundTrip.apply(typedEntity))), "Replacing an existing value with null must clear it");
        }
    }

    private <EntityType extends Entity> Function<EntityType, EntityType> roundTripFunction(final Backend backend, final Class<EntityType> entityType, final GenericRepository<EntityType> genericRepository, final Node node) {
        return this.roundTripFunction(backend, entityType, genericRepository, node, PropertyRegistry.getProperties(entityType));
    }

    private <EntityType extends Entity> Function<EntityType, EntityType> roundTripFunction(final Backend backend, final Class<EntityType> entityType, final GenericRepository<EntityType> genericRepository, final Node node, final List<Property<?, ?>> propertyList) {
        if (backend == Backend.POSTGRES) {
            new PostgresSynchronizer(node.getPostgresDriver()).synchronize(genericRepository.getStatements());

            final EntityWriter<EntityType> entityWriter = new EntityWriter<>(
                    genericRepository.getTable(),
                    null,
                    propertyList,
                    write -> me.trae.foundation.database.postgres.write.PostgresWrite.apply(
                            node.getPostgresDriver().getDslContext(),
                            org.jooq.impl.DSL.table(org.jooq.impl.DSL.name(genericRepository.getTable())),
                            propertyList,
                            write
                    ),
                    _ -> {},
                    _ -> {},
                    new WriteGate(),
                    true,
                    _ -> true,
                    _ -> null
            );

            return entity -> {
                entityWriter.save(entity);
                return new me.trae.foundation.database.postgres.row.PostgresRow<>(new EntityInstantiator<>(entityType), propertyList).map(node.getPostgresDriver().getDslContext().selectFrom(org.jooq.impl.DSL.table(org.jooq.impl.DSL.name(genericRepository.getTable()))).where(me.trae.foundation.database.postgres.write.PostgresWrite.ID.eq(entity.getId())).fetchOne());
            };
        }

        final RedisEntityStorage<EntityType> redisEntityStorage = new RedisEntityStorage<>(new Namespace<>(genericRepository.getTable(), entityType, UUID::toString, null, Duration.ofSeconds(30)), node.getRedisDriver(), new EntityCodec<>(new EntityInstantiator<>(entityType), propertyList));

        return entity -> {
            redisEntityStorage.put(entity.getId(), entity);
            return redisEntityStorage.get(entity.getId()).orElseThrow();
        };
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    @Setter
    public static final class TypedEntity implements Entity {

        @EqualsAndHashCode.Include
        private final UUID id;

        private String anchor = "anchor";

        private Status status;

        private Instant instant;

        private byte[] bytes;

        private Label label;
    }

    @EqualsAndHashCode
    @AllArgsConstructor
    @Getter
    public static final class Label {

        private final String value;
    }

    public static final class LabelConverter implements ValueConverter<Label, String> {

        @Override
        public Class<String> getStoredType() {
            return String.class;
        }

        @Override
        public String serialize(final Label label) {
            return label == null ? null : label.getValue();
        }

        @Override
        public Label deserialize(final String value) {
            return new Label(value);
        }
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    @Setter
    public static final class MemoryEntity implements Entity {

        @EqualsAndHashCode.Include
        private final UUID id;

        private String note;
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    public static final class WideEntity implements Entity {

        private final String[] valueArray = new String[50];

        @EqualsAndHashCode.Include
        private final UUID id;
    }

    private static final class GenericRepository<EntityType extends Entity> extends PostgresRepository<EntityType> {

        private GenericRepository(final Class<EntityType> entityType, final String table, final Node node, final Class<?> declarationType) {
            super(entityType, table, node.getPostgresDriver(), new TenantScope(null), declarationType);
        }
    }

    private enum Backend {

        REDIS, POSTGRES
    }

    private enum Status {

        ACTIVE, DISABLED
    }
}