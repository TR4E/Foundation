package me.trae.foundation.database.deep;

import me.trae.foundation.database.api.exception.VersionConflictException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public final class ConvergenceTest extends RuntimeFixture {

    @Test
    void eightThreadsWriteThroughOneHolderAndAllCopiesAgree() throws Exception {
        try (final Node node = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();
            final AtomicInteger accepted = new AtomicInteger();

            node.getItemHolder().hold(item.getId());

            try (final var executorService = Executors.newFixedThreadPool(8)) {
                final List<java.util.concurrent.Future<?>> futureList = IntStream.range(0, 8)
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

                for (final var future : futureList) {
                    future.get(20, TimeUnit.SECONDS);
                }
            }

            node.flush();

            assertAll(
                    () -> assertEquals(80, accepted.get()),
                    () -> assertEquals("80", node.getRedisDriver().getCommands().hget(path(item.getId()), "counter")),
                    () -> assertEquals(80L, node.getItemHolder().get(item.getId()).orElseThrow().getCounter(), "Local copy lost a concurrent delta"),
                    () -> assertEquals(80L, node.getItemRepository().findById(item.getId()).orElseThrow().getCounter(), "Postgres lost accepted deltas")
            );
        }
    }

    @Test
    void eightThreadsChurnHoldReleaseWhileReading() throws Exception {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();

            try (final var executorService = Executors.newFixedThreadPool(8)) {
                final List<java.util.concurrent.Future<?>> futureList = IntStream.range(0, 8)
                        .mapToObj(_ -> executorService.submit(() -> IntStream.range(0, 100).forEach(_ -> {
                            node.getItemHolder().hold(item.getId());
                            assertEquals(item.getId(), node.getItemHolder().get(item.getId()).orElseThrow().getId());
                            node.getItemHolder().release(item.getId());
                        })))
                        .collect(java.util.stream.Collectors.toList());

                for (final var future : futureList) {
                    future.get(30, TimeUnit.SECONDS);
                }
            }

            node.getItemHolder().release(item.getId());

            assertFalse(node.getItemHolder().holds(item.getId()));
            assertTrue(node.getItemHolder().getWriter().getVersionMap().isEmpty());
        }
    }

    @Test
    void tenThousandHeldEntitiesRemainIsolatedAcrossHolders() {
        try (final Node firstNode = new Node(LocalBean.class, Duration.ofSeconds(30));
             final Node secondNode = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            firstNode.getPostgresDriver().getDslContext().execute("INSERT INTO deep_items (id, name, value, counter) SELECT md5(i::text)::uuid, 'bulk-' || i, 'original', i FROM generate_series(1, 10000) i");
            firstNode.getItemHolder().holdAll();
            secondNode.getItemHolder().holdAll();

            assertEquals(10_000, firstNode.getItemHolder().getHeld().size());
            assertEquals(10_000, secondNode.getItemHolder().getHeld().size());

            final Item firstItem = firstNode.getItemHolder().getHeld().getFirst();
            final Item secondItem = secondNode.getItemHolder().get(firstItem.getId()).orElseThrow();

            assertNotSame(firstItem, secondItem);
            firstNode.getItemHolder().set(firstItem, ItemProperty.VALUE, "changed-on-first");

            assertEquals("original", secondItem.getValue());
            firstNode.getItemHolder().release(firstItem.getId());

            assertEquals(9999, firstNode.getItemHolder().getHeld().size());
            assertEquals(10_000, secondNode.getItemHolder().getHeld().size());
        }
    }

    @Test
    void deterministicFuzzConvergesAcrossBothInstancesAndPostgres() throws Exception {
        try (final Node firstNode = new Node(AllBean.class, Duration.ofSeconds(30));
             final Node secondNode = new Node(AllBean.class, Duration.ofSeconds(30))) {
            final List<Item> itemList = IntStream.range(0, 8).mapToObj(_ -> firstNode.seed()).toList();
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

            IntStream.range(0, 80).forEach(step -> {
                final Item seedItem = itemList.get(random.nextInt(itemList.size()));
                final Node node = random.nextBoolean() ? firstNode : secondNode;

                if (deletedIdSet.contains(seedItem.getId())) {
                    return;
                }

                final int operation = step < 5 ? step : random.nextInt(10);

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

            System.out.println("FUZZ|seed=73491|steps=80|conflicts=%s|deleted=%s".formatted(conflicts.get(), deletedIdSet.size()));
            assertAll(assertionList);
        }
    }
}