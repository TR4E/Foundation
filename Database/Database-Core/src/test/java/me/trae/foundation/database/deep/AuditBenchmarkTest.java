package me.trae.foundation.database.deep;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import me.trae.foundation.database.api.property.PropertyRegistry;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.batch.BatchQueue;
import me.trae.foundation.database.index.GroupedIndexStore;
import me.trae.foundation.database.index.UniqueIndexStore;
import me.trae.foundation.database.local.LocalStorage;
import me.trae.foundation.database.postgres.schema.PostgresSynchronizer;
import me.trae.foundation.database.postgres.schema.SchemaDefinition;
import me.trae.foundation.database.redis.event.RedisEvents;
import me.trae.foundation.database.redis.script.RedisScript;
import me.trae.foundation.database.redis.write.RedisEntityWriter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class AuditBenchmarkTest extends RuntimeFixture {

    @Test
    void localScalesFromOneToSixteenThreadsAndBatchMerge() throws Exception {
        final Item item = new Item(UUID.randomUUID());
        final LocalStorage<UUID, Item> localStorage = new LocalStorage<>(new Namespace<>("audit", Item.class, UUID::toString, null, Duration.ZERO));

        localStorage.put(item.getId(), item);

        for (final int threads : new int[]{1, 2, 4, 8, 16}) {
            this.measureParallel("local_get_%s".formatted(threads), threads, () -> localStorage.get(item.getId()).orElseThrow());
            this.measureParallel("local_put_%s".formatted(threads), threads, () -> localStorage.put(item.getId(), item));
        }

        final BatchQueue batchQueue = new BatchQueue();
        final Write write = write(item.getId(), WriteKind.UPDATE, 0L, increment(1L));

        this.measure("batch_merge", 500_000, () -> batchQueue.add(write));
        assertEquals(501_000L, batchQueue.drain().getFirst().getChanges().getFirst().getValue());
    }

    @Test
    void postgresAndRedisLatencyAndFrameworkCost() throws Exception {
        try (final Node redisNode = new Node(RedisBean.class, Duration.ofMinutes(5));
             final Node postgresNode = new Node(LocalBean.class, Duration.ofMinutes(5));
             final Node subscriberNode = new Node(AllBean.class, Duration.ofMinutes(5))) {
            final Item redisItem = redisNode.seed();
            final Item postgresItem = postgresNode.seed();

            redisNode.getItemHolder().get(redisItem.getId()).orElseThrow();
            subscriberNode.getItemHolder().hold(redisItem.getId());
            postgresNode.getPostgresDriver().getDslContext().execute("INSERT INTO deep_items (id,name,value,counter) SELECT md5(i::text)::uuid, 'audit-' || i, 'value-' || i, 0 FROM generate_series(1,10000) i");
            postgresNode.getPostgresDriver().getDslContext().execute("CREATE INDEX IF NOT EXISTS audit_name_idx ON deep_items(name)");
            postgresNode.getPostgresDriver().getDslContext().execute("ANALYZE deep_items");

            final RedisEntityWriter redisEntityWriter = new RedisEntityWriter(redisNode.getRedisDriver(), PropertyRegistry.getProperties(Item.class));
            final Write write = write(redisItem.getId(), WriteKind.UPDATE, 0L, increment(1L));
            final Set<Tier> postgresTierSet = Set.of(Tier.POSTGRES);

            this.measure("redis_hash_read_raw", 2000, () -> redisNode.getRedisDriver().getCommands().hgetall(path(redisItem.getId())));
            this.measure("redis_read_holder", 2000, () -> redisNode.getItemHolder().get(redisItem.getId()).orElseThrow());
            this.measure("redis_increment_raw", 2000, () -> redisNode.getRedisDriver().getCommands().hincrby(path(redisItem.getId()), "counter", 1L));
            final RedisCommands<String, String> redisCommands = redisNode.getRedisDriver().getCommands();
            final Field scriptField = RedisEntityWriter.class.getDeclaredField("WRITE");
            final Field sourceField = RedisScript.class.getDeclaredField("source");

            scriptField.setAccessible(true);
            sourceField.setAccessible(true);

            final String sha = redisCommands.scriptLoad((String) sourceField.get(scriptField.get(null)));
            final String[] keyArray = {path(redisItem.getId()), channel(redisItem.getId())};

            this.measure("redis_increment_script_raw", 2000, () -> redisCommands.evalsha(sha, ScriptOutputType.INTEGER, keyArray, "0", "300000", redisNode.getRedisDriver().getInstanceId(), "UPDATE", RedisEvents.encode(write.getChanges()), "counter", "INCREMENT", "1"));
            this.measure("redis_increment_script", 2000, () -> redisEntityWriter.apply(path(redisItem.getId()), channel(redisItem.getId()), write, Duration.ofMinutes(5)));
            this.measure("redis_increment_holder", 2000, () -> redisNode.getItemHolder().increment(redisItem, ItemProperty.COUNTER, 1L));
            this.measure("postgres_findById", 2000, () -> postgresNode.getItemRepository().findById(postgresItem.getId()).orElseThrow());
            this.measure("postgres_read_raw", 2000, () -> postgresNode.getPostgresDriver().getDslContext().fetchOne("SELECT * FROM deep_items WHERE id = ?", postgresItem.getId()));
            this.measure("postgres_read_holder", 2000, () -> postgresNode.getItemHolder().get(postgresItem.getId(), postgresTierSet).orElseThrow());
            this.measure("postgres_update_raw", 2000, () -> postgresNode.getPostgresDriver().getDslContext().fetchOne("UPDATE deep_items SET value = ?, version = version + 1 WHERE id = ? RETURNING version", "raw", postgresItem.getId()));

            final Item refreshedItem = postgresNode.getItemRepository().findById(postgresItem.getId()).orElseThrow();

            this.measure("postgres_update_holder", 2000, () -> postgresNode.getItemHolder().set(refreshedItem, ItemProperty.VALUE, "holder"));
            this.measure("postgres_indexed", 2000, () -> postgresNode.getItemRepository().find(Query.where(ItemProperty.NAME, "audit-5000")));
            this.measure("postgres_unindexed", 2000, () -> postgresNode.getItemRepository().find(Query.where(ItemProperty.VALUE, "value-5000")));

            final Item publishItem = subscriberNode.seed();
            final AtomicInteger sequence = new AtomicInteger();

            subscriberNode.getItemHolder().hold(publishItem.getId());
            this.measure("publish_to_applied", 2000, () -> {
                final int version = sequence.incrementAndGet();
                final String payload = "%s\naudit-sender\nUPDATE\ncounter\tINCREMENT\tMQ==".formatted(version);
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);

                redisNode.getRedisDriver().getCommands().publish(channel(publishItem.getId()), payload);

                while (subscriberNode.getItemHolder().getWriter().getVersion(publishItem.getId()) != version && System.nanoTime() < deadline) {
                    java.util.concurrent.locks.LockSupport.parkNanos(10_000L);
                }

                assertEquals(version, subscriberNode.getItemHolder().getWriter().getVersion(publishItem.getId()));
            });
            assertEquals(3000L, subscriberNode.getItemHolder().get(publishItem.getId()).orElseThrow().getCounter());
        }
    }

    @Test
    void holdAllAndIndexesAtThreeSizes() throws Exception {
        try (final Node node = new Node(LocalBean.class, Duration.ofMinutes(5))) {
            final List<UniqueIndexStore> uniqueStoreList = node.getItemHolder().getContext().getIndexStores().getUniqueStores();
            final List<GroupedIndexStore> groupedStoreList = node.getItemHolder().getContext().getIndexStores().getGroupedStores();
            final UniqueIndexStore uniqueIndexStore = uniqueStoreList.getFirst();
            final GroupedIndexStore groupedIndexStore = groupedStoreList.getFirst();

            uniqueStoreList.clear();
            groupedStoreList.clear();

            for (final int size : new int[]{1000, 10_000, 100_000}) {
                node.getPostgresDriver().getDslContext().execute("TRUNCATE deep_items");
                node.getPostgresDriver().getDslContext().execute("INSERT INTO deep_items (id,name,value,counter) SELECT md5(i::text)::uuid, 'memory-' || i, 'v', i FROM generate_series(1, ?) i", size);

                final long before = this.usedHeap();
                final long start = System.nanoTime();

                node.getItemHolder().holdAll();

                final long holdNanos = System.nanoTime() - start;
                final long retained = this.usedHeap() - before;
                final long indexStart = System.nanoTime();

                node.getItemHolder().getHeld().forEach(uniqueIndexStore::index);

                final long uniqueNanos = System.nanoTime() - indexStart;
                final long uniqueBytes = this.usedHeap() - before - retained;
                final long groupedStart = System.nanoTime();

                node.getItemHolder().getHeld().forEach(groupedIndexStore::index);

                final long groupedNanos = System.nanoTime() - groupedStart;
                final long groupedBytes = this.usedHeap() - before - retained - uniqueBytes;

                uniqueStoreList.add(uniqueIndexStore);
                groupedStoreList.add(groupedIndexStore);

                final List<Item> itemList = node.getItemHolder().getHeld();
                final Item item = itemList.getLast();

                assertEquals(size, itemList.size());
                System.out.println("AUDIT|hold|n=%s|hold_ms=%s|unique_ms=%s|grouped_ms=%s|entity_bytes=%s|unique_bytes=%s|grouped_bytes=%s".formatted(size, holdNanos / 1_000_000.0, uniqueNanos / 1_000_000.0, groupedNanos / 1_000_000.0, retained / size, uniqueBytes / size, groupedBytes / size));
                this.measure("index_".concat(Integer.toString(size)), 5000, () -> node.getItemHolder().getByIndex(ItemIndex.NAME, item.getName()).orElseThrow());
                this.measure("held_scan_".concat(Integer.toString(size)), 2000, () -> node.getItemHolder().getByProperty(ItemProperty.COUNTER, item.getCounter()).orElseThrow());
                itemList.forEach(heldItem -> node.getItemHolder().release(heldItem.getId()));

                final long indexedStart = System.nanoTime();

                node.getItemHolder().holdAll();

                System.out.println("AUDIT|hold_indexed|n=%s|ms=%s".formatted(size, (System.nanoTime() - indexedStart) / 1_000_000.0));
                node.getItemHolder().getHeld().forEach(heldItem -> node.getItemHolder().release(heldItem.getId()));
                uniqueStoreList.clear();
                groupedStoreList.clear();
            }
        }
    }

    @Test
    void ddlForTenTables() {
        try (final Node node = new Node(LocalBean.class, Duration.ofMinutes(5))) {
            final AtomicInteger batch = new AtomicInteger();
            final PostgresSynchronizer postgresSynchronizer = new PostgresSynchronizer(node.getPostgresDriver());

            this.measure("ddl_ten_tables", 3, 12, () -> {
                final int batchId = batch.incrementAndGet();
                final List<String> statementList = new ArrayList<>();

                IntStream.range(0, 10).forEach(index -> statementList.addAll(SchemaDefinition.statements("audit_%s_%s".formatted(batchId, index), false, PropertyRegistry.getProperties(Item.class), Map.of(ItemProperty.NAME, me.trae.foundation.database.postgres.index.PostgresIndexType.UNIQUE))));
                postgresSynchronizer.synchronize(statementList);
            });
        }
    }

    private void measure(final String name, final int samples, final Runnable runnable) {
        this.measure(name, 1000, samples, runnable);
    }

    private void measure(final String name, final int warmup, final int samples, final Runnable runnable) {
        IntStream.range(0, warmup).forEach(_ -> runnable.run());

        final long[] sampleArray = new long[samples];
        final long start = System.nanoTime();

        this.sample(runnable, sampleArray, 0, samples);
        this.report(name, sampleArray, System.nanoTime() - start);
    }

    private void measureParallel(final String name, final int threads, final Runnable runnable) throws Exception {
        final int samples = 100_000;
        final long[] sampleArray = new long[threads * samples];
        final CountDownLatch ready = new CountDownLatch(threads);
        final CountDownLatch start = new CountDownLatch(1);

        try (final ExecutorService executorService = Executors.newFixedThreadPool(threads)) {
            final List<Future<Void>> futureList = IntStream.range(0, threads)
                    .mapToObj(thread -> executorService.<Void>submit(() -> {
                        IntStream.range(0, 20_000).forEach(_ -> runnable.run());
                        ready.countDown();
                        start.await();
                        this.sample(runnable, sampleArray, thread * samples, samples);
                        return null;
                    }))
                    .toList();

            try {
                assertTrue(ready.await(20, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }

            final long before = System.nanoTime();

            for (final Future<Void> future : futureList) {
                future.get(30, TimeUnit.SECONDS);
            }

            this.report(name, sampleArray, System.nanoTime() - before);
        }
    }

    private void sample(final Runnable runnable, final long[] sampleArray, final int offset, final int samples) {
        IntStream.range(offset, offset + samples).forEach(index -> {
            final long before = System.nanoTime();

            runnable.run();
            sampleArray[index] = System.nanoTime() - before;
        });
    }

    private void report(final String name, final long[] sampleArray, final long nanos) {
        Arrays.sort(sampleArray);
        System.out.println(String.format(Locale.ROOT, "AUDITBENCH|%s|n=%s|ops=%.0f|p50=%.3f|p99=%.3f|p999=%.3f|max=%.3f", name, sampleArray.length, sampleArray.length * 1_000_000_000.0 / nanos, this.percentile(sampleArray, 0.5), this.percentile(sampleArray, 0.99), this.percentile(sampleArray, 0.999), sampleArray[sampleArray.length - 1] / 1000.0));
    }

    private double percentile(final long[] sampleArray, final double fraction) {
        return sampleArray[(int) Math.ceil(sampleArray.length * fraction) - 1] / 1000.0;
    }

    private long usedHeap() throws InterruptedException {
        System.gc();
        Thread.sleep(100L);
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }
}