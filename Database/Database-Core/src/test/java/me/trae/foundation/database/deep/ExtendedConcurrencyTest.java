package me.trae.foundation.database.deep;

import me.trae.foundation.database.api.event.ChangeEvent;
import me.trae.foundation.database.api.exception.VersionConflictException;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.WriteResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public final class ExtendedConcurrencyTest extends RuntimeFixture {

    @Test
    void sixteenThreadsWriteThroughOneHolderAndAllCopiesAgree() throws Exception {
        try (final Node node = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();
            final AtomicInteger accepted = new AtomicInteger();

            node.getItemHolder().hold(item.getId());

            try (final ExecutorService executorService = Executors.newFixedThreadPool(16)) {
                final List<Future<?>> futureList = IntStream.range(0, 16)
                        .mapToObj(_ -> executorService.submit(() -> IntStream.range(0, 10).forEach(_ -> {
                            final AtomicInteger attempts = new AtomicInteger();

                            while (attempts.incrementAndGet() <= 200) {
                                try {
                                    node.getItemHolder().increment(node.getItemHolder().get(item.getId()).orElseThrow(), ItemProperty.COUNTER, 1L);
                                    accepted.incrementAndGet();

                                    return;
                                } catch (final VersionConflictException exception) {
                                    node.getItemHolder().release(item.getId());
                                    node.getItemHolder().hold(item.getId());
                                }
                            }

                            fail("Optimistic write did not succeed within 200 attempts");
                        })))
                        .collect(java.util.stream.Collectors.toList());

                for (final Future<?> future : futureList) {
                    future.get(20, TimeUnit.SECONDS);
                }
            }

            node.flush();

            assertAll(
                    () -> assertEquals(160, accepted.get()),
                    () -> assertEquals("160", node.getRedisDriver().getCommands().hget(path(item.getId()), "counter")),
                    () -> assertEquals(160L, node.getItemHolder().get(item.getId()).orElseThrow().getCounter(), "Local copy lost a concurrent delta"),
                    () -> assertEquals(160L, node.getItemRepository().findById(item.getId()).orElseThrow().getCounter(), "Postgres lost accepted deltas")
            );
        }
    }

    @Test
    void tenRapidUpdatesReachRemoteHolderWithoutRefetch() throws Exception {
        try (final Node firstNode = new Node(AllBean.class, Duration.ofSeconds(30));
             final Node secondNode = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = firstNode.seed();

            firstNode.getItemHolder().hold(item.getId());
            secondNode.getItemHolder().hold(item.getId());
            secondNode.getItemHolder().getExecutor().shutdown();

            final Item heldItem = firstNode.getItemHolder().get(item.getId()).orElseThrow();

            IntStream.range(0, 10).forEach(_ -> firstNode.getItemHolder().increment(heldItem, ItemProperty.COUNTER, 1L));
            await(() -> secondNode.getItemHolder().getWriter().getVersion(item.getId()) == 10L);
            assertEquals(10L, secondNode.getItemHolder().get(item.getId()).orElseThrow().getCounter());
        }
    }

    @Test
    void thousandGapsQueueBehindBlockedRefetchAndEventuallyConverge() throws Exception {
        try (final Node node = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            node.getItemHolder().hold(item.getId());
            node.getRedisDriver().getCommands().hset(path(item.getId()), Map.of("version", "1001", "counter", "1000"));

            final CountDownLatch blocker = new CountDownLatch(1);
            final ExecutorService executorService = node.getItemHolder().getExecutor();
            final Future<?> firstFuture = executorService.submit(() -> {
                try {
                    assertTrue(blocker.await(10, TimeUnit.SECONDS));
                } catch (final InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            });
            final long start = System.nanoTime();

            try {
                IntStream.rangeClosed(2, 1001).forEach(version -> node.getItemHolder().getApplier().apply(new ChangeEvent(new EntityKey("deep_items", null, item.getId()), version, WriteKind.UPDATE, List.of(increment(1L)))));
                assertEquals(0L, node.getItemHolder().getWriter().getVersion(item.getId()));
            } finally {
                blocker.countDown();
            }

            firstFuture.get(10, TimeUnit.SECONDS);
            executorService.submit(() -> {}).get(30, TimeUnit.SECONDS);
            System.out.println("AUDIT|gap_storm|events=1000|drain_ms=%s".formatted((System.nanoTime() - start) / 1_000_000.0));
            assertEquals(1001L, node.getItemHolder().getWriter().getVersion(item.getId()));
            assertEquals(1000L, node.getItemHolder().get(item.getId()).orElseThrow().getCounter());
        }
    }

    @Test
    void updatePausedBeforeRedisDoesNotSucceedAfterRemoteDelete() throws Exception {
        try (final Node firstNode = new Node(AllBean.class, Duration.ofSeconds(30));
             final Node secondNode = new Node(AllBean.class, Duration.ofSeconds(30));
             final ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            final Item item = firstNode.seed();

            firstNode.getItemHolder().hold(item.getId());
            secondNode.getItemHolder().hold(item.getId());

            final Item secondItem = secondNode.getItemHolder().get(item.getId()).orElseThrow();
            final Function<Write, WriteResult> authority = secondNode.getItemHolder().getWriter().getAuthority();
            final CountDownLatch entered = new CountDownLatch(1);
            final CountDownLatch resume = new CountDownLatch(1);
            final Field field = secondNode.getItemHolder().getWriter().getClass().getDeclaredField("authority");

            field.setAccessible(true);
            field.set(secondNode.getItemHolder().getWriter(), (Function<Write, WriteResult>) write -> {
                entered.countDown();

                try {
                    assertTrue(resume.await(10, TimeUnit.SECONDS));
                } catch (final InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }

                return authority.apply(write);
            });

            final Future<Boolean> future = executorService.submit(() -> {
                try {
                    secondNode.getItemHolder().set(secondItem, ItemProperty.VALUE, "after-delete");
                    return false;
                } catch (final VersionConflictException exception) {
                    return true;
                }
            });

            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                firstNode.getItemHolder().delete(item);
                firstNode.flush();
                await(() -> !secondNode.getItemHolder().holds(item.getId()));
            } finally {
                resume.countDown();
            }

            final boolean conflict = future.get(10, TimeUnit.SECONDS);

            secondNode.flush();
            assertAll(
                    () -> assertTrue(conflict, "An update after deletion must not report success"),
                    () -> assertEquals(0, secondNode.getCoreDatabase().getBatchQueue().size(), "A rejected write must not be enqueued"),
                    () -> assertTrue(secondNode.getItemRepository().findById(item.getId()).isEmpty()),
                    () -> assertEquals(0L, secondNode.getRedisDriver().getCommands().exists(path(item.getId())))
            );
        }
    }

    @Test
    void fiveHundredOperationsConvergeAcrossBothInstancesAndPostgres() throws Exception {
        try (final Node firstNode = new Node(AllBean.class, Duration.ofSeconds(30));
             final Node secondNode = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final List<Item> itemList = IntStream.range(0, 24).mapToObj(_ -> firstNode.seed()).toList();
            final Map<UUID, Long> counterMap = new java.util.HashMap<>();
            final Map<UUID, String> valueMap = new java.util.HashMap<>();
            final java.util.Set<UUID> deletedIdSet = new java.util.HashSet<>();
            final Random random = new Random(73491L);
            final AtomicInteger conflicts = new AtomicInteger();

            for (final Item item : itemList) {
                firstNode.getItemHolder().hold(item.getId());
                secondNode.getItemHolder().hold(item.getId());
                counterMap.put(item.getId(), 0L);
                valueMap.put(item.getId(), "initial");
            }

            IntStream.range(0, 500).forEach(step -> {
                final List<Item> liveItemList = itemList.stream().filter(item -> !deletedIdSet.contains(item.getId())).toList();
                final Item seedItem = liveItemList.get(random.nextInt(liveItemList.size()));
                final Node node = random.nextBoolean() ? firstNode : secondNode;

                final int operation = step < 5 ? step : 3 + random.nextInt(7);

                if (operation == 3) {
                    node.getItemHolder().release(seedItem.getId());
                    return;
                }

                if (operation == 4) {
                    node.getItemHolder().hold(seedItem.getId());
                    return;
                }

                final Item item = node.getItemHolder().get(seedItem.getId()).orElseThrow();

                try {
                    if (operation == 2) {
                        node.getItemHolder().delete(item);
                        deletedIdSet.add(item.getId());
                    } else if (operation % 2 == 0) {
                        node.getItemHolder().increment(item, ItemProperty.COUNTER, 1L);
                        counterMap.compute(item.getId(), (_, value) -> value + 1L);
                    } else {
                        final String value = "fuzz-%s".formatted(step);

                        node.getItemHolder().set(item, ItemProperty.VALUE, value);
                        valueMap.put(item.getId(), value);
                    }
                } catch (final VersionConflictException exception) {
                    conflicts.incrementAndGet();
                }

                node.flush();
            });

            Thread.sleep(300L);
            firstNode.getItemHolder().getExecutor().submit(() -> {}).get(5, TimeUnit.SECONDS);
            secondNode.getItemHolder().getExecutor().submit(() -> {}).get(5, TimeUnit.SECONDS);
            firstNode.flush();
            secondNode.flush();

            final List<org.junit.jupiter.api.function.Executable> assertionList = new ArrayList<>();

            for (final Item seedItem : itemList) {
                assertionList.add(() -> {
                    if (deletedIdSet.contains(seedItem.getId())) {
                        assertEquals(0L, firstNode.getRedisDriver().getCommands().exists(path(seedItem.getId())), "Deleted hash remains in Redis");
                        assertTrue(firstNode.getItemRepository().findById(seedItem.getId()).isEmpty(), "Deleted row remains in Postgres");
                        assertTrue(firstNode.getItemHolder().get(seedItem.getId()).isEmpty(), "First instance retained deleted entity");
                        assertTrue(secondNode.getItemHolder().get(seedItem.getId()).isEmpty(), "Second instance retained deleted entity");

                        return;
                    }

                    final Item storedItem = firstNode.getItemRepository().findById(seedItem.getId()).orElseThrow();
                    final Item firstItem = firstNode.getItemHolder().get(seedItem.getId()).orElseThrow();
                    final Item secondItem = secondNode.getItemHolder().get(seedItem.getId()).orElseThrow();

                    assertEquals(counterMap.get(seedItem.getId()), storedItem.getCounter(), "Database counter differs from accepted-write oracle");
                    assertEquals(valueMap.get(seedItem.getId()), storedItem.getValue(), "Database value differs from accepted-write oracle");
                    assertEquals(
                            String.valueOf(storedItem.getCounter()),
                            firstNode.getRedisDriver().getCommands().hget(path(seedItem.getId()), "counter"),
                            "Redis counter diverged"
                    );
                    assertEquals(storedItem.getValue(), firstNode.getRedisDriver().getCommands().hget(path(seedItem.getId()), "value"), "Redis value diverged");
                    assertEquals(storedItem.getCounter(), firstItem.getCounter(), "First instance counter diverged");
                    assertEquals(storedItem.getCounter(), secondItem.getCounter(), "Second instance counter diverged");
                    assertEquals(storedItem.getValue(), firstItem.getValue(), "First instance value diverged");
                    assertEquals(storedItem.getValue(), secondItem.getValue(), "Second instance value diverged");
                });
            }

            System.out.println("FUZZ|seed=73491|steps=500|conflicts=%s|deleted=%s".formatted(conflicts.get(), deletedIdSet.size()));
            assertAll(assertionList);
        }
    }
}