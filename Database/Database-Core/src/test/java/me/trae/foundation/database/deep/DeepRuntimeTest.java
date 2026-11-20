package me.trae.foundation.database.deep;

import me.trae.foundation.database.api.exception.VersionConflictException;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.property.PropertyRegistry;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.WriteResult;
import me.trae.foundation.database.redis.driver.RedisDriver;
import me.trae.foundation.database.redis.driver.RedisSettings;
import me.trae.foundation.database.redis.event.RedisEvents;
import me.trae.foundation.database.redis.write.RedisEntityWriter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class DeepRuntimeTest extends RuntimeFixture {

    @Test
    void productionCoreConstructsAllThreeTierBeans() {
        try (final Node localNode = new Node(LocalBean.class, Duration.ofSeconds(30));
             final Node redisNode = new Node(RedisBean.class, Duration.ofSeconds(30));
             final Node allNode = new Node(AllBean.class, Duration.ofSeconds(30))) {
            assertEquals(
                    Set.of(me.trae.foundation.database.api.tier.Tier.LOCAL, me.trae.foundation.database.api.tier.Tier.POSTGRES),
                    localNode.getItemHolder().getContext().getTiers()
            );
            assertFalse(redisNode.getItemHolder().getContext().getStorageMap().containsKey(me.trae.foundation.database.api.tier.Tier.LOCAL));
            assertEquals(2, allNode.getItemHolder().getContext().getStorageMap().size());
            assertSame(allNode.getItemHolder(), allNode.getCoreDatabase().getHolder(Item.class));
            assertNull(allNode.getCoreDatabase().getSettings().getTenant());
            assertTrue(allNode.getCoreDatabase().getTenantScope().getTenant().isEmpty());
        }
    }

    @Test
    void instanceAIncrementAppliesOnceOnAAndOnB() throws Exception {
        try (final Node firstNode = new Node(AllBean.class, Duration.ofSeconds(30));
             final Node secondNode = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = firstNode.seed();

            firstNode.getItemHolder().hold(item.getId());
            secondNode.getItemHolder().hold(item.getId());
            firstNode.getItemHolder().increment(firstNode.getItemHolder().get(item.getId()).orElseThrow(), ItemProperty.COUNTER, 1L);
            await(() -> secondNode.getItemHolder().get(item.getId()).orElseThrow().getCounter() == 1L);
            Thread.sleep(50L);

            assertEquals(1L, firstNode.getItemHolder().get(item.getId()).orElseThrow().getCounter());
            assertEquals(1L, secondNode.getItemHolder().get(item.getId()).orElseThrow().getCounter());
        }
    }

    @Test
    void writePayloadIsVersionOriginChanges() throws Exception {
        try (final Node node = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            node.getItemHolder().hold(item.getId());

            try (final PayloadProbe payloadProbe = new PayloadProbe(item.getId())) {
                node.getItemHolder().set(node.getItemHolder().get(item.getId()).orElseThrow(), ItemProperty.VALUE, "payload");

                final String payload = payloadProbe.next();

                assertEquals("1", payload.split("\n")[0]);
                assertEquals(node.getRedisDriver().getInstanceId(), payload.split("\n")[1]);
                assertEquals("payload", RedisEvents.decode(new EntityKey("deep_items", null, item.getId()), payload).getChanges().getFirst().getValue());
            }
        }
    }

    @Test
    void deleteDispatchIgnoresOriginButReachesOtherInstance() throws Exception {
        try (final Node firstNode = new Node(AllBean.class, Duration.ofSeconds(30));
             final Node secondNode = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = firstNode.seed();
            final AtomicInteger ownCount = new AtomicInteger();
            final AtomicInteger remoteCount = new AtomicInteger();

            firstNode.getItemHolder().getContext().getSubscriber().accept(List.of(item.getId()), _ -> ownCount.incrementAndGet());
            secondNode.getItemHolder().getContext().getSubscriber().accept(List.of(item.getId()), _ -> remoteCount.incrementAndGet());
            this.writer(firstNode).delete(path(item.getId()), channel(item.getId()), 1L);
            await(() -> remoteCount.get() == 1);
            Thread.sleep(50L);

            assertEquals(0, ownCount.get());
            assertEquals(1, remoteCount.get());
        }
    }

    @Test
    void remoteDeleteEvictsHeldCopy() throws Exception {
        try (final Node firstNode = new Node(AllBean.class, Duration.ofSeconds(30));
             final Node secondNode = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = firstNode.seed();

            firstNode.getItemHolder().hold(item.getId());
            secondNode.getItemHolder().hold(item.getId());
            firstNode.getItemHolder().delete(firstNode.getItemHolder().get(item.getId()).orElseThrow());
            firstNode.flush();
            Thread.sleep(150L);

            assertFalse(secondNode.getItemHolder().holds(item.getId()), "A remote delete must evict the held copy");
            assertTrue(secondNode.getItemRepository().findById(item.getId()).isEmpty());
        }
    }

    @Test
    void nullEmptyNullRoundTripsHashAndPublishedEvent() throws Exception {
        try (final Node node = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            node.getItemHolder().hold(item.getId());

            try (final PayloadProbe payloadProbe = new PayloadProbe(item.getId())) {
                for (final String value : new String[]{null, "", null}) {
                    this.writer(node).apply(
                            path(item.getId()),
                            channel(item.getId()),
                            write(item.getId(), WriteKind.UPDATE, 0L, set("value", value)),
                            Duration.ofSeconds(30)
                    );

                    assertEquals(value, node.getRedisDriver().getCommands().hget(path(item.getId()), "value"));
                    assertEquals(
                            value,
                            RedisEvents.decode(new EntityKey("deep_items", null, item.getId()), payloadProbe.next()).getChanges().getFirst().getValue()
                    );
                }
            }
        }
    }

    @Test
    void nullEmptyNullAppliesOnOtherInstance() throws Exception {
        try (final Node firstNode = new Node(AllBean.class, Duration.ofSeconds(30));
             final Node secondNode = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = firstNode.seed();

            firstNode.getItemHolder().hold(item.getId());
            secondNode.getItemHolder().hold(item.getId());

            for (final String value : new String[]{null, "", null}) {
                firstNode.getItemHolder().set(firstNode.getItemHolder().get(item.getId()).orElseThrow(), ItemProperty.VALUE, value);
                firstNode.flush();
                await(() -> java.util.Objects.equals(value, secondNode.getItemHolder().get(item.getId()).orElseThrow().getValue()));

                assertEquals(value, secondNode.getItemRepository().findById(item.getId()).orElseThrow().getValue());
            }
        }
    }

    @Test
    void nullEmptyNullRoundTripsPostgres() {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            for (final String value : new String[]{null, "", null}) {
                node.getItemRepository().apply(write(item.getId(), WriteKind.UPDATE, 0L, set("value", value)));

                assertEquals(value, node.getItemRepository().findById(item.getId()).orElseThrow().getValue());
            }
        }
    }

    @Test
    void gapAfterTwoMissedWritesConvergesAndSubscriptionStillWorks() throws Exception {
        try (final Node firstNode = new Node(AllBean.class, Duration.ofSeconds(30));
             final Node secondNode = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = firstNode.seed();

            firstNode.getItemHolder().hold(item.getId());
            secondNode.getItemHolder().hold(item.getId());

            final Item firstItem = firstNode.getItemHolder().get(item.getId()).orElseThrow();

            firstNode.getItemHolder().set(firstItem, ItemProperty.VALUE, "first");
            firstNode.flush();
            await(() -> secondNode.getItemHolder().get(item.getId()).orElseThrow().getValue().equals("first"));
            secondNode.getRedisDriver().unsubscribe(channel(item.getId()));
            firstNode.getItemHolder().set(firstItem, ItemProperty.VALUE, "missed-one");
            firstNode.flush();
            firstNode.getItemHolder().set(firstItem, ItemProperty.VALUE, "missed-two");
            firstNode.flush();
            secondNode.getItemHolder().hold(item.getId());
            firstNode.getItemHolder().set(firstItem, ItemProperty.VALUE, "repaired");
            firstNode.flush();
            await(() -> secondNode.getItemHolder().get(item.getId()).orElseThrow().getValue().equals("repaired"));

            assertTrue(secondNode.getItemHolder().holds(item.getId()));

            firstNode.getItemHolder().set(firstItem, ItemProperty.VALUE, "after-repair");
            firstNode.flush();
            await(() -> secondNode.getItemHolder().get(item.getId()).orElseThrow().getValue().equals("after-repair"));

            assertEquals("PONG", secondNode.getRedisDriver().getCommands().ping());
            assertEquals("after-repair", secondNode.getItemRepository().findById(item.getId()).orElseThrow().getValue());
        }
    }

    @Test
    void exactlyOneRacingScriptConflictsAndLoserSucceedsAfterRefetch() throws Exception {
        try (final Node firstNode = new Node(AllBean.class, Duration.ofSeconds(30));
             final Node secondNode = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = firstNode.seed();
            final AtomicInteger conflicts = new AtomicInteger();
            final AtomicInteger winner = new AtomicInteger(-1);
            final CountDownLatch go = new CountDownLatch(1);

            firstNode.getItemHolder().hold(item.getId());
            firstNode.getRedisDriver().getCommands().hset(path(item.getId()), "version", "1");

            try (final var executorService = Executors.newFixedThreadPool(2)) {
                final List<java.util.concurrent.Future<?>> futureList = IntStream.range(0, 2)
                        .mapToObj(index -> executorService.submit(() -> {
                            try {
                                go.await();
                                this.writer(index == 0 ? firstNode : secondNode).apply(
                                        path(item.getId()),
                                        channel(item.getId()),
                                        write(item.getId(), WriteKind.UPDATE, 1L, set("value", "writer-%d".formatted(index))),
                                        Duration.ofSeconds(30)
                                );
                                winner.set(index);
                            } catch (final VersionConflictException exception) {
                                conflicts.incrementAndGet();
                            } catch (final InterruptedException exception) {
                                Thread.currentThread().interrupt();

                                throw new AssertionError(exception);
                            }
                        }))
                        .collect(java.util.stream.Collectors.toList());

                go.countDown();

                for (final var future : futureList) {
                    future.get(10, TimeUnit.SECONDS);
                }
            }

            assertEquals(1, conflicts.get());
            assertEquals("writer-%d".formatted(winner.get()), firstNode.getRedisDriver().getCommands().hget(path(item.getId()), "value"));

            final Node loserNode = winner.get() == 0 ? secondNode : firstNode;

            loserNode.getItemHolder().release(item.getId());
            loserNode.getItemHolder().hold(item.getId());

            final long version = Long.parseLong(loserNode.getRedisDriver().getCommands().hget(path(item.getId()), "version"));

            this.writer(loserNode).apply(
                    path(item.getId()),
                    channel(item.getId()),
                    write(item.getId(), WriteKind.UPDATE, version, set("value", "retry")),
                    Duration.ofSeconds(30)
            );

            assertEquals("retry", loserNode.getRedisDriver().getCommands().hget(path(item.getId()), "value"));
        }
    }

    @Test
    void eightThreadsScriptedIncrementsEndAtExactSum() throws Exception {
        try (final Node node = new Node(RedisBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            node.getItemHolder().hold(item.getId());

            try (final var executorService = Executors.newFixedThreadPool(8)) {
                final List<java.util.concurrent.Future<?>> futureList = IntStream.range(0, 8)
                        .mapToObj(_ -> executorService.submit(() -> IntStream.range(
                                0,
                                100
                        ).forEach(_ -> this.writer(node).apply(path(item.getId()), channel(item.getId()), write(item.getId(), WriteKind.UPDATE, 0L, increment(1L)), Duration.ofSeconds(30)))))
                        .collect(java.util.stream.Collectors.toList());

                for (final var future : futureList) {
                    future.get(20, TimeUnit.SECONDS);
                }
            }

            assertEquals("800", node.getRedisDriver().getCommands().hget(path(item.getId()), "counter"));
        }
    }

    @Test
    void unsetRemovesFieldAndMissingIncrementStartsAtZero() {
        try (final Node node = new Node(RedisBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            node.getItemHolder().hold(item.getId());
            this.writer(node).apply(
                    path(item.getId()),
                    channel(item.getId()),
                    write(item.getId(), WriteKind.UPDATE, 0L, set("value", null)),
                    Duration.ofSeconds(30)
            );
            node.getRedisDriver().getCommands().hdel(path(item.getId()), "counter");
            this.writer(node).apply(
                    path(item.getId()),
                    channel(item.getId()),
                    write(item.getId(), WriteKind.UPDATE, 0L, increment(7L)),
                    Duration.ofSeconds(30)
            );

            assertFalse(node.getRedisDriver().getCommands().hexists(path(item.getId()), "value"));
            assertEquals("7", node.getRedisDriver().getCommands().hget(path(item.getId()), "counter"));
        }
    }

    @Test
    void missingHashReturnsZeroWithoutCreatingAnything() {
        try (final Node node = new Node(RedisBean.class, Duration.ofSeconds(30))) {
            final UUID id = UUID.randomUUID();

            final WriteResult writeResult = this.writer(node).apply(path(id), channel(id), write(id, WriteKind.UPDATE, 0L, increment(1L)), Duration.ofSeconds(30));

            assertEquals(0L, writeResult.getVersion());
            assertNull(writeResult.getValue());
            assertFalse(node.getRedisDriver().getCommands().exists(path(id)) > 0);
        }
    }

    @Test
    void conflictChangesNeitherFieldsNorVersion() {
        try (final Node node = new Node(RedisBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            node.getItemHolder().hold(item.getId());
            node.getRedisDriver().getCommands().hset(path(item.getId()), "version", "5");

            final Map<String, String> beforeMap = node.getRedisDriver().getCommands().hgetall(path(item.getId()));

            assertThrows(
                    VersionConflictException.class,
                    () -> this.writer(node).apply(
                            path(item.getId()),
                            channel(item.getId()),
                            write(item.getId(), WriteKind.UPDATE, 4L, set("value", "rejected"), increment(7L)),
                            Duration.ofSeconds(30)
                    )
            );
            assertEquals(beforeMap, node.getRedisDriver().getCommands().hgetall(path(item.getId())));
        }
    }

    @Test
    void expiredRedisEntryFallsThroughToPostgresAndRepopulates() throws Exception {
        try (final Node node = new Node(RedisBean.class, Duration.ofMillis(200))) {
            final Item item = node.seed();

            node.getItemHolder().getRefresher().stop();
            assertTrue(node.getItemHolder().get(item.getId()).isPresent());
            Thread.sleep(350L);

            assertFalse(node.getRedisDriver().getCommands().exists(path(item.getId())) > 0);
            assertTrue(node.getItemHolder().get(item.getId()).isPresent());
            assertTrue(node.getRedisDriver().getCommands().exists(path(item.getId())) > 0);
        }
    }

    @Test
    void heldLocalCopyIsReturnedAfterRedisExpiry() throws Exception {
        try (final Node node = new Node(AllBean.class, Duration.ofMillis(200))) {
            final Item item = node.seed();
            final String path = path(item.getId());

            node.getItemHolder().getRefresher().stop();
            node.getItemHolder().hold(item.getId());

            final Item held = node.getItemHolder().getHeld().getFirst();

            await(() -> node.getRedisDriver().getCommands().exists(path) == 0L);

            assertSame(held, node.getItemHolder().get(item.getId()).orElseThrow());
            assertEquals(0L, node.getRedisDriver().getCommands().exists(path));
        }
    }

    @Test
    void refresherKeepsHeldRedisEntityAlive() throws Exception {
        try (final Node node = new Node(AllBean.class, Duration.ofMillis(500))) {
            final Item item = node.seed();

            node.getItemHolder().hold(item.getId());
            Thread.sleep(1400L);

            assertTrue(node.getRedisDriver().getCommands().exists(path(item.getId())) > 0);
            assertTrue(node.getRedisDriver().getCommands().pttl(path(item.getId())) > 0L);
        }
    }

    @Test
    void batchedIncrementsPersistAllAcceptedWrites() {
        try (final Node node = new Node(RedisBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            node.getItemHolder().hold(item.getId());

            final Item loadedItem = node.getItemHolder().get(item.getId()).orElseThrow();

            node.getItemHolder().increment(loadedItem, ItemProperty.COUNTER, 1L);
            node.getItemHolder().increment(loadedItem, ItemProperty.COUNTER, 1L);
            node.getItemHolder().increment(loadedItem, ItemProperty.COUNTER, 1L);
            node.flush();

            assertEquals(0, node.getCoreDatabase().getBatchQueue().size(), "Accepted increments must not remain queued after flush");
            assertEquals(3L, node.getItemRepository().findById(item.getId()).orElseThrow().getCounter());
        }
    }

    @Test
    void refetchInitializesExpectedVersionFromBackend() {
        try (final Node node = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            node.getItemHolder().hold(item.getId());
            node.getRedisDriver().getCommands().hset(path(item.getId()), "version", "5");
            node.getItemHolder().release(item.getId());
            node.getItemHolder().hold(item.getId());

            assertEquals(5L, node.getItemHolder().getWriter().getVersion(item.getId()), "Refetch must retain backend version for optimistic writes");
        }
    }

    @Test
    void coreStopShutsDownRefetchExecutor() {
        try (final Node node = new Node(AllBean.class, Duration.ofSeconds(30))) {
            node.getCoreDatabase().stop();

            assertTrue(node.getItemHolder().getExecutor().isShutdown(), "Core stop must close its holder refetch executors");
        }
    }

    @Test
    void stalePostgresDeleteMustReportConflict() {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            final long version = node.getItemRepository().apply(write(
                    item.getId(),
                    WriteKind.UPDATE,
                    0L,
                    set("value", "updated")
            )).getVersion();

            assertThrows(VersionConflictException.class, () -> node.getItemRepository().apply(write(
                    item.getId(),
                    WriteKind.DELETE,
                    version - 1L
            )), "A stale delete must not report success while leaving the row");
            assertEquals("updated", node.getItemRepository().findById(item.getId()).orElseThrow().getValue());
        }
    }

    private RedisEntityWriter writer(final Node node) {
        return new RedisEntityWriter(node.getRedisDriver(), PropertyRegistry.getProperties(Item.class));
    }

    private static final class PayloadProbe implements AutoCloseable {

        private final java.util.concurrent.LinkedBlockingQueue<String> payloadQueue = new java.util.concurrent.LinkedBlockingQueue<>();

        private final RedisDriver redisDriver;

        private PayloadProbe(final UUID id) {
            this.redisDriver = new RedisDriver(new RedisSettings(REDIS.getHost(), REDIS.getMappedPort(6379), null, 0, Duration.ofSeconds(3)));

            this.redisDriver.connect();
            this.redisDriver.subscribe(Map.of(channel(id), this.payloadQueue::add));
        }

        private String next() throws InterruptedException {
            final String payload = this.payloadQueue.poll(5, TimeUnit.SECONDS);

            assertNotNull(payload, "Expected a published event");

            return payload;
        }

        @Override
        public void close() {
            this.redisDriver.disconnect();
        }
    }
}