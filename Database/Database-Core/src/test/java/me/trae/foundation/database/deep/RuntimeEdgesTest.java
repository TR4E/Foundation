package me.trae.foundation.database.deep;

import lombok.Getter;
import me.trae.foundation.database.api.converter.ValueConverter;
import me.trae.foundation.database.api.index.GroupedIndex;
import me.trae.foundation.database.api.index.UniqueIndex;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.concurrency.Stampede;
import me.trae.foundation.database.core.database.CoreDatabase;
import me.trae.foundation.database.core.settings.DatabaseSettings;
import me.trae.foundation.database.index.GroupedIndexStore;
import me.trae.foundation.database.index.UniqueIndexStore;
import me.trae.foundation.database.local.LocalStorage;
import me.trae.foundation.database.postgres.driver.PostgresDriver;
import me.trae.foundation.database.postgres.driver.PostgresSettings;
import me.trae.foundation.database.redis.driver.RedisDriver;
import me.trae.foundation.database.redis.driver.RedisSettings;
import me.trae.foundation.database.redis.marker.RedisMarker;
import me.trae.foundation.database.redis.storage.RedisEntityStorage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class RuntimeEdgesTest extends RuntimeFixture {

    @Test
    void collidingTenantIdsRemainIndependentInPostgres() {
        this.dropTenantTable();

        try (final TenantNode firstTenantNode = new TenantNode("edge_alpha");
             final TenantNode secondTenantNode = new TenantNode("edge_beta")) {
            final UUID id = UUID.randomUUID();

            firstTenantNode.getItemRepository().apply(new Write(new EntityKey("deep_items", "edge_alpha", id), WriteKind.SAVE, List.of(set("name", "alpha")), 0L));
            assertTrue(secondTenantNode.getItemRepository().findById(id).isEmpty(), "Tenant B must not read A's row");
            secondTenantNode.getItemRepository().apply(new Write(new EntityKey("deep_items", "edge_beta", id), WriteKind.SAVE, List.of(set("name", "beta")), 0L));

            assertAll(
                    () -> assertEquals(Optional.of("alpha"), firstTenantNode.getItemRepository().findById(id).map(Item::getName), "Saving a colliding tenant ID must preserve A"),
                    () -> assertEquals(Optional.of("beta"), secondTenantNode.getItemRepository().findById(id).map(Item::getName)),
                    () -> assertEquals(2, firstTenantNode.getPostgresDriver().getDslContext().fetchCount(org.jooq.impl.DSL.table("deep_items")))
            );
        } finally {
            this.dropTenantTable();
        }
    }

    @Test
    void tenantRedisKeysAndSchemaAreIsolated() {
        try (final TenantNode firstTenantNode = new TenantNode("edge_alpha");
             final TenantNode secondTenantNode = new TenantNode("edge_beta")) {
            final UUID id = UUID.randomUUID();
            final Item firstItem = new Item(id);
            final Item secondItem = new Item(id);

            firstItem.setName("alpha");
            secondItem.setName("beta");
            firstItem.setValue("alpha");
            secondItem.setValue("beta");

            final RedisEntityStorage<Item> firstStorage = (RedisEntityStorage<Item>) firstTenantNode.getItemHolder().getContext().getStorageMap().get(Tier.REDIS);
            final RedisEntityStorage<Item> secondStorage = (RedisEntityStorage<Item>) secondTenantNode.getItemHolder().getContext().getStorageMap().get(Tier.REDIS);

            firstStorage.put(id, firstItem);
            secondStorage.put(id, secondItem);

            assertNotEquals(firstStorage.path(id), secondStorage.path(id));
            assertNotEquals(firstStorage.channel(id), secondStorage.channel(id));
            assertEquals("alpha", firstStorage.get(id).orElseThrow().getName());
            assertEquals("beta", secondStorage.get(id).orElseThrow().getName());
            assertEquals(1, firstTenantNode.getPostgresDriver().getDslContext().fetchCount(org.jooq.impl.DSL.table("information_schema.columns"), org.jooq.impl.DSL.condition("table_name = 'deep_items' AND column_name = 'tenant'")));
        } finally {
            this.dropTenantTable();
        }

        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            assertEquals(0, node.getPostgresDriver().getDslContext().fetchCount(org.jooq.impl.DSL.table("information_schema.columns"), org.jooq.impl.DSL.condition("table_name = 'deep_items' AND column_name = 'tenant'")));
        }
    }

    @Test
    void nullUniqueExtractorDoesNotLeaveOldMapping() {
        final UniqueIndex<TypeEdgesTest.MemoryEntity, String> uniqueIndex = UniqueIndex.derived("edge_null_unique", TypeEdgesTest.MemoryEntity.class, String.class, TypeEdgesTest.MemoryEntity::getNote, java.util.Set.of());
        final UniqueIndexStore uniqueIndexStore = new UniqueIndexStore(uniqueIndex, List.of(new LocalStorage<>(new Namespace<>("edge_null_unique", UUID.class, String::valueOf, null, Duration.ZERO), 0, Duration.ZERO)));
        final TypeEdgesTest.MemoryEntity memoryEntity = new TypeEdgesTest.MemoryEntity(UUID.randomUUID());

        memoryEntity.setNote("before");
        uniqueIndexStore.index(memoryEntity);
        memoryEntity.setNote(null);

        assertDoesNotThrow(() -> uniqueIndexStore.index(memoryEntity), "A nullable extractor must remove the old key without throwing");
        assertNull(uniqueIndexStore.lookup("before"));
    }

    @Test
    void nullGroupedExtractorDoesNotLeaveOldMapping() {
        final GroupedIndex<TypeEdgesTest.MemoryEntity, String> groupedIndex = GroupedIndex.derived("edge_null_grouped", TypeEdgesTest.MemoryEntity.class, String.class, TypeEdgesTest.MemoryEntity::getNote, java.util.Set.of());
        final GroupedIndexStore groupedIndexStore = new GroupedIndexStore(groupedIndex);
        final TypeEdgesTest.MemoryEntity memoryEntity = new TypeEdgesTest.MemoryEntity(UUID.randomUUID());

        memoryEntity.setNote("before");
        groupedIndexStore.index(memoryEntity);
        memoryEntity.setNote(null);

        assertDoesNotThrow(() -> groupedIndexStore.index(memoryEntity), "A nullable extractor must remove the old grouped key without throwing");
        assertTrue(groupedIndexStore.lookup("before").isEmpty());
    }

    @Test
    void groupedLookupOmitsUnresolvableIds() {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            final Item item = node.seed();
            final Item missingItem = new Item(UUID.randomUUID());

            missingItem.setName(item.getName());
            node.getItemHolder().hold(item.getId());
            node.getItemHolder().reindex(missingItem);

            final List<Item> itemList = node.getItemHolder().getAllByIndex(ItemIndex.GROUP, item.getName());

            assertEquals(List.of(item.getId()), itemList.stream().map(Item::getId).toList());
        }
    }

    @Test
    void holdAllOneHundredThousandRowsMeasuresRetentionAndIndexRebuild() throws Exception {
        try (final Node node = new Node(LocalBean.class, Duration.ofSeconds(30))) {
            node.getPostgresDriver().getDslContext().execute("INSERT INTO deep_items(id,name,value,counter) SELECT md5(i::text)::uuid, 'bulk-' || i, 'value', i FROM generate_series(1,100000) i");
            System.gc();
            Thread.sleep(100L);

            final long beforeBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            final long start = System.nanoTime();

            node.getItemHolder().holdAll();

            final long holdNanos = System.nanoTime() - start;
            final long allocatedBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() - beforeBytes;
            final List<Item> itemList = node.getItemHolder().getHeld();
            final long indexStart = System.nanoTime();

            itemList.forEach(node.getItemHolder()::reindex);

            final long indexNanos = System.nanoTime() - indexStart;

            System.gc();
            Thread.sleep(100L);

            final long retainedBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() - beforeBytes;

            System.out.println("EDGE|holdAll100k|hold_ms=%s|index_rebuild_ms=%s|heap_delta_mib=%s|retained_delta_mib=%s".formatted(holdNanos / 1_000_000.0, indexNanos / 1_000_000.0, allocatedBytes / 1048576.0, retainedBytes / 1048576.0));
            assertEquals(100_000, itemList.size());
            assertEquals(itemList.getLast().getId(), node.getItemHolder().getByIndex(ItemIndex.NAME, itemList.getLast().getName()).orElseThrow().getId());
        }
    }

    @Test
    void expiredMarkerLeaseAllowsOneAdditionalPostgresQuery() throws Exception {
        try (final Node firstNode = new Node(AllBean.class, Duration.ofSeconds(30));
             final Node secondNode = new Node(AllBean.class, Duration.ofSeconds(30));
             final ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            final AtomicInteger queryCount = new AtomicInteger();
            final CountDownLatch firstStarted = new CountDownLatch(1), secondStarted = new CountDownLatch(1);
            final ValueConverter<String, String> valueConverter = new StringConverter();
            final Stampede firstStampede = new Stampede(new RedisMarker(firstNode.getRedisDriver()), Duration.ofMillis(50), Duration.ofSeconds(2));
            final Stampede secondStampede = new Stampede(new RedisMarker(secondNode.getRedisDriver()), Duration.ofMillis(50), Duration.ofSeconds(2));
            final String key = "lease-edge:%s".formatted(UUID.randomUUID());

            final Future<Optional<String>> firstFuture = executorService.submit(() -> firstStampede.load(key, valueConverter, () -> {
                firstStarted.countDown();
                assertDoesNotThrow(() -> assertTrue(secondStarted.await(5L, TimeUnit.SECONDS)));

                queryCount.incrementAndGet();
                firstNode.getPostgresDriver().getDslContext().execute("SELECT 1");

                return Optional.of("loaded");
            }));

            assertTrue(firstStarted.await(3L, TimeUnit.SECONDS));
            await(() -> firstNode.getRedisDriver().getCommands().exists("%s:claim".formatted(key)) == 0L);

            final Future<Optional<String>> secondFuture = executorService.submit(() -> secondStampede.load(key, valueConverter, () -> {
                secondStarted.countDown();
                queryCount.incrementAndGet();
                secondNode.getPostgresDriver().getDslContext().execute("SELECT 1");

                return Optional.of("loaded");
            }));

            assertEquals(Optional.of("loaded"), firstFuture.get(5L, TimeUnit.SECONDS));
            assertEquals(Optional.of("loaded"), secondFuture.get(5L, TimeUnit.SECONDS));
            assertEquals(2, queryCount.get());
        }
    }

    @Test
    void stopWaitsForAcceptedWriteToReachPersistence() throws Exception {
        try (final Node node = new Node(RedisBean.class, Duration.ofSeconds(30));
             final ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            final Item item = node.getItemHolder().get(node.seed().getId()).orElseThrow();
            final CountDownLatch authorityCompleted = new CountDownLatch(1);
            final CountDownLatch resumePersistence = new CountDownLatch(1);
            final CountDownLatch stopStarted = new CountDownLatch(1);
            final Consumer<Write> persistence = node.getItemHolder().getWriter().getPersistence();
            final Field field = node.getItemHolder().getWriter().getClass().getDeclaredField("persistence");

            field.setAccessible(true);
            field.set(node.getItemHolder().getWriter(), (Consumer<Write>) write -> {
                authorityCompleted.countDown();

                try {
                    assertTrue(resumePersistence.await(10, TimeUnit.SECONDS));
                } catch (final InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }

                persistence.accept(write);
            });

            final Future<Long> writeFuture = executorService.submit(() -> node.getItemHolder().increment(item, ItemProperty.COUNTER, 1L));

            assertTrue(authorityCompleted.await(5, TimeUnit.SECONDS));

            final Future<?> stopFuture = executorService.submit(() -> {
                stopStarted.countDown();
                node.getCoreDatabase().stop();
            });

            try {
                assertTrue(stopStarted.await(5, TimeUnit.SECONDS));
                assertFalse(stopFuture.isDone(), "Stop must wait for the authority-accepted write");
            } finally {
                resumePersistence.countDown();
            }

            assertEquals(1L, writeFuture.get(5, TimeUnit.SECONDS));
            stopFuture.get(7, TimeUnit.SECONDS);

            final PostgresDriver postgresDriver = new PostgresDriver(node.getPostgresDriver().getPostgresSettings());

            postgresDriver.connect();

            try {
                final long actual = new ItemRepository(postgresDriver, node.getCoreDatabase().getTenantScope()).findById(item.getId()).orElseThrow().getCounter();

                assertAll(
                        () -> assertEquals(0, node.getCoreDatabase().getBatchQueue().size(), "Stop left an accepted write stranded in the queue"),
                        () -> assertEquals(1L, actual, "An authority-accepted write was not persisted by stop")
                );
            } finally {
                postgresDriver.disconnect();
            }
        }
    }

    private void dropTenantTable() {
        final PostgresDriver postgresDriver = new PostgresDriver(new PostgresSettings(POSTGRES.getHost(), POSTGRES.getMappedPort(5432), "deep", "deep", "deep", 2));

        postgresDriver.connect();

        try {
            postgresDriver.getDslContext().execute("DROP TABLE deep_items");
        } finally {
            postgresDriver.disconnect();
        }
    }

    @Getter
    private static final class TenantNode implements AutoCloseable {

        private final PostgresDriver postgresDriver;

        private final RedisDriver redisDriver;

        private final CoreDatabase coreDatabase;

        private final ItemRepository itemRepository;

        private final ItemHolder itemHolder;

        private TenantNode(final String tenant) {
            this.postgresDriver = new PostgresDriver(new PostgresSettings(POSTGRES.getHost(), POSTGRES.getMappedPort(5432), "deep", "deep", "deep", 4));
            this.redisDriver = new RedisDriver(new RedisSettings(REDIS.getHost(), REDIS.getMappedPort(6379), null, 0, Duration.ofSeconds(3)));
            final DatabaseSettings databaseSettings = new DatabaseSettings(
                    Tier.ALL,
                    tenant,
                    Duration.ofSeconds(30),
                    Duration.ofHours(1),
                    Duration.ofHours(1),
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(2),
                    Duration.ZERO,
                    0, 0
            );

            this.coreDatabase = new CoreDatabase(databaseSettings, this.postgresDriver, this.redisDriver);
            this.itemRepository = new ItemRepository(this.postgresDriver, this.coreDatabase.getTenantScope());
            this.itemHolder = new ItemHolder(this.itemRepository, this.coreDatabase.getContextFactory(), Tier.ALL);
            this.coreDatabase.start();
        }

        @Override
        public void close() {
            this.coreDatabase.stop();
        }
    }

    private static final class StringConverter implements ValueConverter<String, String> {

        @Override
        public Class<String> getStoredType() {
            return String.class;
        }

        @Override
        public String serialize(final String value) {
            return value;
        }

        @Override
        public String deserialize(final String value) {
            return value;
        }
    }
}