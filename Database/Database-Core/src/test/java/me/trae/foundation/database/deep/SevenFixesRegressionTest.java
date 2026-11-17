package me.trae.foundation.database.deep;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.event.ChangeEvent;
import me.trae.foundation.database.api.exception.VersionConflictException;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.repository.Repository;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.holder.entity.EntityHolder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class SevenFixesRegressionTest extends RuntimeFixture {

    @Test
    void remoteDeleteClearsLocalIndexesAndTrackedVersion() throws Exception {
        try (final Node firstNode = new Node(AllBean.class, Duration.ofSeconds(30));
             final Node secondNode = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = firstNode.seed();

            firstNode.getItemHolder().hold(item.getId());
            secondNode.getItemHolder().hold(item.getId());
            firstNode.getItemHolder().increment(firstNode.getItemHolder().get(item.getId()).orElseThrow(), ItemProperty.COUNTER, 1L);
            firstNode.flush();
            await(() -> secondNode.getItemHolder().getWriter().getVersion(item.getId()) == 1L);
            firstNode.getItemHolder().delete(firstNode.getItemHolder().get(item.getId()).orElseThrow());
            firstNode.flush();
            Thread.sleep(150L);

            this.assertReleased(secondNode, item.getId());
        }
    }

    @Test
    void holdSeedsVersionAndNextWriteRejectsChangedBackend() {
        try (final Node node = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            node.getItemHolder().get(item.getId()).orElseThrow();
            node.getRedisDriver().getCommands().hset(path(item.getId()), "version", "5");
            node.getItemHolder().hold(item.getId());

            assertEquals(5L, node.getItemHolder().getWriter().getVersion(item.getId()));

            node.getRedisDriver().getCommands().hset(path(item.getId()), "version", "6");

            assertThrows(VersionConflictException.class, () -> node.getItemHolder().set(
                    node.getItemHolder().get(item.getId()).orElseThrow(),
                    ItemProperty.VALUE,
                    "must-conflict"
            ));
        }
    }

    @Test
    void asynchronousRefetchSeedsVersionAndNextWriteUsesIt() throws Exception {
        try (final Node node = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            node.getItemHolder().hold(item.getId());
            node.getRedisDriver().getCommands().hset(path(item.getId()), Map.of("version", "5", "value", "refetched"));
            node.getItemHolder().getApplier().apply(new ChangeEvent(
                    new EntityKey("deep_items", null, item.getId()),
                    5L,
                    WriteKind.UPDATE,
                    List.of(set("value", "gap"))
            ));
            await(() -> node.getItemHolder().getWriter().getVersion(item.getId()) == 5L);

            assertEquals("refetched", node.getItemHolder().get(item.getId()).orElseThrow().getValue());

            node.getRedisDriver().getCommands().hset(path(item.getId()), "version", "6");

            assertThrows(VersionConflictException.class, () -> node.getItemHolder().set(
                    node.getItemHolder().get(item.getId()).orElseThrow(),
                    ItemProperty.VALUE,
                    "must-conflict"
            ));
        }
    }

    @Test
    void releaseWithoutLocalClearsIndexesAndTrackedVersion() throws Exception {
        try (final Node node = new Node(RedisBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            node.getItemHolder().hold(item.getId());
            node.getItemHolder().release(item.getId());

            this.assertReleased(node, item.getId());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void coreStopClosesEveryRegisteredHolderAndExecutor() {
        try (final Node node = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Repository<OtherEntity> repository = mock(Repository.class);
            final EntityHolder<OtherEntity, Repository<OtherEntity>> entityHolder = mock(EntityHolder.class);

            when(repository.getEntityType()).thenReturn(OtherEntity.class);
            when(repository.getTable()).thenReturn("other_entity");
            node.getCoreDatabase().getRegistry().register(repository, entityHolder);
            node.getCoreDatabase().stop();

            assertTrue(node.getItemHolder().getExecutor().isShutdown());
            assertTrue(node.getItemHolder().getRefresher().getExecutor().isShutdown());
            verify(entityHolder).close();
        }
    }

    private void assertReleased(final Node node, final UUID id) throws Exception {
        final List<org.junit.jupiter.api.function.Executable> assertionList = new ArrayList<>();

        assertionList.add(() -> assertFalse(node.getItemHolder().holds(id), "Local copy retained"));
        assertionList.add(() -> assertFalse(node.getItemHolder().getWriter().getVersionMap().containsKey(id), "Tracked version retained"));

        for (final var uniqueIndexStore : node.getItemHolder().getContext().getIndexStores().getUniqueStores()) {
            assertionList.add(() -> assertFalse(this.map(uniqueIndexStore, "keyMap").containsKey(id), "Unique reverse map retained"));
        }

        for (final var groupedIndexStore : node.getItemHolder().getContext().getIndexStores().getGroupedStores()) {
            assertionList.add(() -> assertFalse(this.map(groupedIndexStore, "keyMap").containsKey(id), "Grouped reverse map retained"));
        }

        assertAll(assertionList);
    }

    private Map<?, ?> map(final Object object, final String name) throws Exception {
        final var field = object.getClass().getDeclaredField(name);

        field.setAccessible(true);

        return (Map<?, ?>) field.get(object);
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    private static final class OtherEntity implements Entity {

        @EqualsAndHashCode.Include
        private final UUID id;
    }
}