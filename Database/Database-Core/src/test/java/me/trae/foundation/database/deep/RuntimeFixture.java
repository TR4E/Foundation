package me.trae.foundation.database.deep;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.index.GroupedIndex;
import me.trae.foundation.database.api.index.UniqueIndex;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.change.Change;
import me.trae.foundation.database.api.write.change.ChangeType;
import me.trae.foundation.database.core.database.CoreDatabase;
import me.trae.foundation.database.core.settings.DatabaseSettings;
import me.trae.foundation.database.holder.context.HolderContextFactory;
import me.trae.foundation.database.holder.entity.EntityHolder;
import me.trae.foundation.database.postgres.driver.PostgresDriver;
import me.trae.foundation.database.postgres.driver.PostgresSettings;
import me.trae.foundation.database.postgres.repository.PostgresRepository;
import me.trae.foundation.database.redis.driver.RedisDriver;
import me.trae.foundation.database.redis.driver.RedisSettings;
import me.trae.foundation.database.tenant.TenantScope;
import me.trae.foundation.injector.api.annotation.Application;
import me.trae.foundation.injector.core.CoreInjector;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
public abstract class RuntimeFixture {

    @Container
    protected static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withEnv(Map.of("POSTGRES_DB", "deep", "POSTGRES_USER", "deep", "POSTGRES_PASSWORD", "deep"))
            .withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

    @Container
    protected static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @BeforeEach
    void clearServices() {
        try (final Node node = new Node(AllBean.class, Duration.ofSeconds(30))) {
            node.getPostgresDriver().getDslContext().execute("TRUNCATE deep_items");
            node.getRedisDriver().getCommands().flushdb();
        }
    }

    protected static Write write(final UUID id, final WriteKind kind, final long version, final Change... changes) {
        return new Write(new EntityKey("deep_items", null, id), kind, List.of(changes), version);
    }

    protected static Change set(final String column, final Object value) {
        return new Change(column, ChangeType.SET, value);
    }

    protected static Change increment(final long delta) {
        return new Change("counter", ChangeType.INCREMENT, delta);
    }

    protected static void await(final BooleanSupplier booleanSupplier) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);

        while (!booleanSupplier.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }

        assertTrue(booleanSupplier.getAsBoolean(), "State did not converge within eight seconds");
    }

    protected static String path(final UUID id) {
        return "deep_items:%s".formatted(id);
    }

    protected static String channel(final UUID id) {
        return "%s:events".formatted(path(id));
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    @Setter
    public static final class Item implements Entity {

        @EqualsAndHashCode.Include
        private final UUID id;

        private Long counter = 0L;

        private String name, value;
    }

    @UtilityClass
    public static final class ItemProperty {

        public static final Property<Item, String> NAME = Property.persistent(Item.class, "name", String.class, Item::getName, Item::setName);
        public static final Property<Item, String> VALUE = Property.persistent(Item.class, "value", String.class, Item::getValue, Item::setValue);

        public static final Property<Item, Long> COUNTER = Property.persistent(Item.class, "counter", Long.class, Item::getCounter, Item::setCounter);
    }

    @UtilityClass
    public static final class ItemIndex {

        public static final UniqueIndex<Item, String> NAME = UniqueIndex.of("deep_name", ItemProperty.NAME);

        public static final GroupedIndex<Item, String> GROUP = GroupedIndex.of("deep_group", ItemProperty.NAME, Set.of(Tier.LOCAL));
    }

    public static final class ItemRepository extends PostgresRepository<Item> {

        public ItemRepository(final PostgresDriver postgresDriver, final TenantScope tenantScope) {
            super(Item.class, "deep_items", postgresDriver, tenantScope, ItemProperty.class);
        }
    }

    public static class ItemHolder extends EntityHolder<Item, ItemRepository> {

        public ItemHolder(final ItemRepository itemRepository, final HolderContextFactory holderContextFactory, final Set<Tier> tiers) {
            super(Item.class, itemRepository, holderContextFactory, tiers, ItemProperty.class, ItemIndex.class);
        }

        @Override
        public boolean decrementIfAtLeast(final Item item, final Property<Item, Long> property, final long delta, final long floor) {
            throw new UnsupportedOperationException("Production holder does not implement this operation");
        }
    }

    public static final class LocalBean extends ItemHolder {

        public LocalBean(final ItemRepository itemRepository, final HolderContextFactory holderContextFactory) {
            super(itemRepository, holderContextFactory, Set.of(Tier.LOCAL, Tier.POSTGRES));
        }
    }

    public static final class RedisBean extends ItemHolder {

        public RedisBean(final ItemRepository itemRepository, final HolderContextFactory holderContextFactory) {
            super(itemRepository, holderContextFactory, Set.of(Tier.REDIS, Tier.POSTGRES));
        }
    }

    public static final class AllBean extends ItemHolder {

        public AllBean(final ItemRepository itemRepository, final HolderContextFactory holderContextFactory) {
            super(itemRepository, holderContextFactory, Tier.ALL);
        }
    }

    @Application
    public static final class TestApplication {

    }

    @Getter
    public static final class Node implements AutoCloseable {

        private final CoreInjector coreInjector = new CoreInjector();
        private final TestApplication testApplication = new TestApplication();

        private final PostgresDriver postgresDriver;

        private final RedisDriver redisDriver;

        private final CoreDatabase coreDatabase;

        private final ItemRepository itemRepository;

        private final ItemHolder itemHolder;

        public Node(final Class<? extends ItemHolder> holderType, final Duration ttl) {
            this.postgresDriver = new PostgresDriver(new PostgresSettings(POSTGRES.getHost(), POSTGRES.getMappedPort(5432), "deep", "deep", "deep", 8));
            this.redisDriver = new RedisDriver(new RedisSettings(REDIS.getHost(), REDIS.getMappedPort(6379), null, 0, Duration.ofSeconds(3)));

            final DatabaseSettings databaseSettings = new DatabaseSettings(
                    Tier.ALL,
                    null,
                    ttl,
                    Duration.ofMillis(100),
                    Duration.ofHours(1),
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(2)
            );

            this.coreDatabase = new CoreDatabase(databaseSettings, this.postgresDriver, this.redisDriver);
            this.itemRepository = new ItemRepository(this.postgresDriver, this.coreDatabase.getTenantScope());
            this.coreInjector.provide(ItemRepository.class, this.itemRepository);
            this.coreInjector.provide(HolderContextFactory.class, this.coreDatabase.getContextFactory());
            this.coreInjector.initialize(this.testApplication, List.of(holderType));
            this.itemHolder = this.coreInjector.get(holderType);
            this.coreDatabase.start();
        }

        public Item seed() {
            final Item item = new Item(UUID.randomUUID());

            item.setName("item-%s".formatted(item.getId()));
            item.setValue("initial");
            this.itemRepository.apply(write(item.getId(), WriteKind.SAVE, 0L, set("name", item.getName()), set("value", item.getValue()), set("counter", 0L)));

            return item;
        }

        public void flush() {
            this.coreDatabase.getBatchScheduler().flush();
        }

        @Override
        public void close() {
            try {
                this.itemHolder.getHeld().forEach(item -> this.itemHolder.release(item.getId()));
            } finally {
                this.itemHolder.getExecutor().shutdownNow();
                this.coreInjector.shutdown(this.testApplication);
                this.coreDatabase.stop();
            }
        }
    }
}