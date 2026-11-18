package me.trae.foundation.database.deep;

import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.repository.Repository;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.postgres.repository.PostgresRepository;
import me.trae.foundation.database.postgres.schema.PostgresSynchronizer;
import me.trae.foundation.database.tenant.Tenant;
import me.trae.foundation.database.tenant.TenantScope;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class AuditLifecycleTest extends RuntimeFixture {

    @Test
    void freshTenantedSchemaAndSaveBothScopeByTenantAndId() {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            final TenantRepository firstRepository = new TenantRepository(node, "audit_alpha");
            final TenantRepository secondRepository = new TenantRepository(node, "audit_beta");
            final UUID id = UUID.randomUUID();

            new PostgresSynchronizer(node.getPostgresDriver()).synchronize(firstRepository.getStatements());
            firstRepository.apply(new Write(new EntityKey(firstRepository.getTable(), "audit_alpha", id), WriteKind.SAVE, List.of(set("name", "alpha")), 0L));
            assertTrue(secondRepository.findById(id).isEmpty());
            secondRepository.apply(new Write(new EntityKey(secondRepository.getTable(), "audit_beta", id), WriteKind.SAVE, List.of(set("name", "beta")), 0L));

            assertEquals("alpha", firstRepository.findById(id).orElseThrow().getName());
            assertEquals("beta", secondRepository.findById(id).orElseThrow().getName());
            assertEquals(1L, firstRepository.count(Query.all()));
            assertEquals(1L, secondRepository.count(Query.all()));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void coreStopFlushesOnceBeforeDisconnectingPostgres() {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            final Repository<Item> repository = mock(Repository.class);
            final AtomicInteger attempts = new AtomicInteger();

            when(repository.apply(any(Write.class))).thenAnswer(_ -> {
                assertTrue(node.getPostgresDriver().isConnected());

                attempts.incrementAndGet();

                return 1L;
            });
            node.getCoreDatabase().getRegistry().getTableMap().put("deep_items", repository);
            node.getCoreDatabase().getBatchQueue().add(write(UUID.randomUUID(), WriteKind.UPDATE, 0L, increment(1L)));
            node.getCoreDatabase().stop();

            assertEquals(1, attempts.get());
            assertEquals(0, node.getCoreDatabase().getBatchQueue().size());
            assertTrue(node.getItemHolder().getExecutor().isShutdown());
        }
    }

    @Test
    void unsubscribeReturnsBeforeAnotherRedisCommandCanRun() throws Exception {
        try (final Node node = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            node.getItemHolder().hold(item.getId());

            final java.util.concurrent.CompletableFuture<Boolean> completion = new java.util.concurrent.CompletableFuture<>();
            final String channel = channel(item.getId());

            node.getRedisDriver().subscribe(channel, _ -> {
                try {
                    node.getRedisDriver().unsubscribe(channel);
                    completion.complete(true);
                } catch (final RuntimeException exception) {
                    completion.completeExceptionally(exception);
                }
            });
            node.getRedisDriver().getCommands().publish(channel, "trigger");

            assertTrue(completion.get(1, java.util.concurrent.TimeUnit.SECONDS), "An unsubscribe on the Pub/Sub callback must not wait for its own acknowledgement");
            assertEquals("PONG", node.getRedisDriver().getCommands().ping());
        }
    }

    @Test
    void heldScanAndGroupedRenameUseCurrentValues() {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            node.getItemHolder().holdAll();

            final Item heldItem = node.getItemHolder().getByProperty(ItemProperty.VALUE, "initial").orElseThrow();

            assertEquals(item.getId(), heldItem.getId());
            assertEquals(List.of(heldItem), node.getItemHolder().getAllByIndex(ItemIndex.GROUP, item.getName()));
            node.getItemHolder().set(heldItem, ItemProperty.NAME, "renamed");
            assertTrue(node.getItemHolder().getAllByIndex(ItemIndex.GROUP, item.getName()).isEmpty());
            assertEquals(List.of(heldItem), node.getItemHolder().getAllByIndex(ItemIndex.GROUP, "renamed"));
        }
    }

    @Test
    void holdAllWithoutLocalRetainsNoHeldEntities() {
        try (final Node node = new Node(RedisBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            node.getItemHolder().get(item.getId()).orElseThrow();
            node.getItemHolder().holdAll();
            assertTrue(node.getItemHolder().getHeld().isEmpty());
            assertFalse(node.getItemHolder().holds(item.getId()));
            assertEquals(item.getId(), node.getItemHolder().getAllByIndex(ItemIndex.GROUP, item.getName()).getFirst().getId());
            node.getItemHolder().release(item.getId());
            assertTrue(node.getItemHolder().getAllByIndex(ItemIndex.GROUP, item.getName()).isEmpty());
        }
    }

    private static final class TenantRepository extends PostgresRepository<Item> {

        private TenantRepository(final Node node, final String tenant) {
            super(Item.class, "audit_tenanted", node.getPostgresDriver(), new TenantScope(Tenant.of(tenant)), ItemProperty.class);
        }
    }
}