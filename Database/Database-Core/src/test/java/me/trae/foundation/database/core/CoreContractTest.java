package me.trae.foundation.database.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.property.EntityPropertyRegistry;
import me.trae.foundation.database.api.tenant.TenantScope;
import me.trae.foundation.database.core.batch.PendingWrite;
import me.trae.foundation.database.core.batch.WriteType;
import me.trae.foundation.database.core.converter.EnumValueConverter;
import me.trae.foundation.database.core.converter.JsonValueConverter;
import me.trae.foundation.database.core.schema.DataTypeMapper;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.core.value.ColumnValueMapper;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CoreContractTest {

    private static final EntityProperty<TestEntity, String> NAME = EntityProperty.register(TestEntity.class, "name", TestEntity::getName, TestEntity::setName, String.class, true);
    private static final EntityProperty<TestEntity, State> STATE = EntityProperty.register(TestEntity.class, "state", TestEntity::getState, TestEntity::setState, State.class, true);
    private static final EntityProperty<TestEntity, List<String>> TAGS = EntityProperty.registerWithConverter(TestEntity.class, "tags", TestEntity::getTags, TestEntity::setTags, JsonValueConverter.ofList(String.class), true);
    private static final EntityProperty<TestEntity, Map<String, Integer>> SCORES = EntityProperty.registerWithConverter(TestEntity.class, "scores", TestEntity::getScores, TestEntity::setScores, JsonValueConverter.ofMap(String.class, Integer.class), true);

    @Test
    void jsonConvertersRoundTripCollectionAndMapValues() {
        final JsonValueConverter<List<String>> listConverter = JsonValueConverter.ofList(String.class);
        final JsonValueConverter<Set<Integer>> setConverter = JsonValueConverter.ofSet(Integer.class);
        final JsonValueConverter<Map<String, Integer>> mapConverter = JsonValueConverter.ofMap(String.class, Integer.class);

        assertEquals(List.of("one", "two"), listConverter.deserialize(listConverter.serialize(List.of("one", "two"))));
        assertEquals(Set.of(2, 4), setConverter.deserialize(setConverter.serialize(Set.of(2, 4))));
        assertEquals(Map.of("wins", 3), mapConverter.deserialize(mapConverter.serialize(Map.of("wins", 3))));
        assertEquals(JSONB.class, listConverter.getStoredType());
    }

    @Test
    void columnMapperConvertsEnumsJsonAndNullsInBothDirections() {
        final TestEntity entity = new TestEntity(UUID.randomUUID());
        entity.setState(State.ACTIVE);
        entity.setTags(List.of("a", "b"));

        assertEquals("ACTIVE", ColumnValueMapper.readStored(STATE, entity));
        assertEquals(List.of("a", "b"), ColumnValueMapper.fromStored(TAGS, ColumnValueMapper.toStored(TAGS, entity.getTags())));
        assertNull(ColumnValueMapper.toStoredObject(TAGS, null));
        assertNull(ColumnValueMapper.fromStored(TAGS, null));

        ColumnValueMapper.writeStored(STATE, entity, "DISABLED");
        assertEquals(State.DISABLED, entity.getState());
        assertEquals(Map.of("wins", 3), ColumnValueMapper.fromStored(SCORES, JSONB.valueOf("{\"wins\":3}")));
    }

    @Test
    void dataTypeMapperUsesConverterStoredTypeAndRejectsUnknownTypes() {
        assertEquals(SQLDataType.VARCHAR.nullable(true), DataTypeMapper.getDataType(NAME));
        assertEquals(SQLDataType.VARCHAR.nullable(true), DataTypeMapper.getDataType(STATE));
        assertEquals(JSONB.class, DataTypeMapper.getStoredType(TAGS));

        final EntityProperty<UnsupportedEntity, Object> object = EntityProperty.register(UnsupportedEntity.class, "object", UnsupportedEntity::getObject, UnsupportedEntity::setObject, Object.class, true);
        assertThrows(SchemaException.class, () -> DataTypeMapper.getDataType(object));
    }

    @Test
    void enumConverterUsesNamesAndRejectsUnknownNames() {
        final EnumValueConverter<State> converter = new EnumValueConverter<>(State.class);

        assertEquals("ACTIVE", converter.serialize(State.ACTIVE));
        assertEquals(State.DISABLED, converter.deserialize("DISABLED"));
        assertThrows(IllegalArgumentException.class, () -> converter.deserialize("missing"));
    }

    @Test
    void pendingWriteMergeKeepsLatestValuesAndCarriesCallbacks() {
        final TableSchema<TestEntity> schema = new TableSchema<>(TestEntity.class, "core_contract", TenantScope.NONE, EntityPropertyRegistry.getProperties(TestEntity.class));
        final Field<?> nameField = schema.getField(NAME);
        final AtomicInteger callbacks = new AtomicInteger();
        final UUID id = UUID.randomUUID();
        final PendingWrite first = new PendingWrite(schema, null, id, WriteType.UPSERT, Map.of(nameField, "old"), List.of(callbacks::incrementAndGet), 1);
        final PendingWrite second = new PendingWrite(schema, null, id, WriteType.UPSERT, Map.of(nameField, "new"), List.of(callbacks::incrementAndGet), 2);

        final PendingWrite merged = first.merge(second);

        assertEquals(WriteType.UPSERT, merged.getWriteType());
        assertEquals("new", merged.getValueMap().get(nameField));
        assertEquals(2, merged.getCommitCallbackList().size());
        merged.getCommitCallbackList().forEach(Runnable::run);
        assertEquals(2, callbacks.get());
        assertEquals(2, merged.getSequence());
    }

    private enum State {
        ACTIVE,
        DISABLED
    }

    @RequiredArgsConstructor
    @Getter
    @Setter
    private static final class TestEntity implements Entity {

        private final UUID id;
        private String name;
        private State state;
        private List<String> tags;
        private Map<String, Integer> scores;
    }

    @RequiredArgsConstructor
    @Getter
    @Setter
    private static final class UnsupportedEntity implements Entity {

        private final UUID id;
        private Object object;
    }
}