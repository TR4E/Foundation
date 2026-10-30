package me.trae.foundation.database.core.holder;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.ConnectionException;
import me.trae.foundation.database.api.holder.InstanceMode;
import me.trae.foundation.database.api.holder.RedisMode;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.tenant.TenantScope;
import me.trae.foundation.database.core.CoreDatabase;
import me.trae.foundation.database.core.batch.BatchQueue;
import me.trae.foundation.database.core.batch.PendingWrite;
import me.trae.foundation.database.core.batch.PendingWriteStore;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.core.repository.RepositoryWriter;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.redis.RedisNamespace;
import me.trae.foundation.database.storage.redis.RedisStorage;
import me.trae.foundation.database.storage.revision.EntityRevisionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class HolderSharedStateTest {

    private static final EntityProperty<TestEntity, String> NAME = EntityProperty.register(TestEntity.class, "name", TestEntity::getName, TestEntity::setName, String.class, true);
    private static final EntityProperty<TestEntity, Boolean> ADMINISTRATING = EntityProperty.register(TestEntity.class, "administrating", TestEntity::isAdministrating, TestEntity::setAdministrating, Boolean.class, false);
    private static final EntityProperty<TestEntity, Boolean> VANISHED = EntityProperty.register(TestEntity.class, "vanished", TestEntity::isVanished, TestEntity::setVanished, Boolean.class, false);
    private static final EntityProperty<TestEntity, String> NOTE = EntityProperty.register(TestEntity.class, "note", TestEntity::getNote, TestEntity::setNote, String.class, false);

    private static final RedisNamespace REDIS_NAMESPACE = RedisNamespace.of("holder_shared_state");

    private final Map<String, Map<String, String>> hashMap = new HashMap<>();

    private final List<Consumer<String>> subscriberList = new ArrayList<>();
    private final List<Runnable> notificationList = new ArrayList<>();

    private final PendingWriteStore pendingWriteStore = new PendingWriteStore();

    private final TableSchema<TestEntity> tableSchema = new TableSchema<>(TestEntity.class, "holder_shared_state", TenantScope.NONE, List.of(NAME, ADMINISTRATING, VANISHED, NOTE));

    private final CoreDatabase coreDatabase = mock(CoreDatabase.class);

    private final BatchQueue batchQueue = mock(BatchQueue.class);

    @SuppressWarnings("unchecked")
    private final AbstractEntityRepository<TestEntity> repository = mock(AbstractEntityRepository.class);

    @SuppressWarnings("unchecked")
    private final RedisCommands<String, String> redisCommands = mock(RedisCommands.class, this.redisAnswer());

    private final RedisDriver proxyRedisDriver = mock(RedisDriver.class);
    private final RedisDriver paperRedisDriver = mock(RedisDriver.class);

    @BeforeEach
    void configureHolders() {
        when(this.repository.getProperties()).thenReturn(List.of(NAME, ADMINISTRATING, VANISHED, NOTE));
        when(this.repository.getEntityType()).thenReturn(TestEntity.class);
        when(this.repository.getTable()).thenReturn("holder_shared_state");
        when(this.repository.getTableSchema()).thenReturn(this.tableSchema);
        when(this.repository.getTenantId()).thenReturn(Optional.empty());
        when(this.repository.getEntityIndexes()).thenReturn(List.of());
        when(this.repository.getCoreDatabase()).thenReturn(this.coreDatabase);
        when(this.coreDatabase.getBatchQueue()).thenReturn(this.batchQueue);
        when(this.coreDatabase.getEntityRevisionStore()).thenReturn(new EntityRevisionStore());
        when(this.batchQueue.getPendingWriteStore()).thenReturn(this.pendingWriteStore);
        this.configureDriver(this.proxyRedisDriver);
        this.configureDriver(this.paperRedisDriver);
    }

    @Test
    void adminModeAndVanishedRefreshThePinnedPaperAccountWithoutSql() {
        final HolderComponents<TestEntity> proxyHolderComponents = this.createHolder(this.proxyRedisDriver);
        final HolderComponents<TestEntity> paperHolderComponents = this.createHolder(this.paperRedisDriver);
        final TestEntity proxyTestEntity = this.pin(proxyHolderComponents, UUID.randomUUID());
        this.pin(paperHolderComponents, proxyTestEntity.getId());
        final AtomicInteger commitCount = new AtomicInteger();
        proxyTestEntity.setAdministrating(true);
        proxyTestEntity.setVanished(true);

        proxyHolderComponents.getHolderWriter().update(proxyTestEntity, () -> {
            assertEquals("1true", this.hashMap.get(this.sharedKey(proxyTestEntity.getId())).get("administrating"));
            commitCount.incrementAndGet();
        });
        this.deliverNotifications();

        final TestEntity paperTestEntity = paperHolderComponents.getLocalStorage().get(proxyTestEntity.getId()).orElseThrow();
        assertTrue(paperTestEntity.isAdministrating());
        assertTrue(paperTestEntity.isVanished());
        this.await(() -> commitCount.get() == 1);
        assertEquals(1, commitCount.get());
        assertTrue(paperHolderComponents.getLocalStorage().isPinned(proxyTestEntity.getId()));
        assertSame(proxyTestEntity, proxyHolderComponents.getLocalStorage().get(proxyTestEntity.getId()).orElseThrow());
        assertTrue(this.pendingWriteStore.isEmpty());
        verify(this.repository, never()).findById(any());
        verify(this.repository, never()).update(any(), anyCollection(), anyList(), anyList(), eq(true), anyString());

        proxyTestEntity.setAdministrating(false);
        proxyHolderComponents.getHolderWriter().update(proxyTestEntity);
        this.deliverNotifications();

        final TestEntity updatedPaperTestEntity = paperHolderComponents.getLocalStorage().get(proxyTestEntity.getId()).orElseThrow();
        assertFalse(updatedPaperTestEntity.isAdministrating());
        assertTrue(updatedPaperTestEntity.isVanished());
        assertTrue(paperHolderComponents.getChangeTracker().diff(updatedPaperTestEntity).isEmpty());
    }

    @Test
    void unchangedUpdateDoesNotPublishAgain() {
        final HolderComponents<TestEntity> holderComponents = this.createHolder(this.proxyRedisDriver);
        final TestEntity testEntity = this.pin(holderComponents, UUID.randomUUID());
        final AtomicInteger commitCount = new AtomicInteger();

        holderComponents.getHolderWriter().update(testEntity, commitCount::incrementAndGet);

        this.await(() -> commitCount.get() == 1);
        assertEquals(1, commitCount.get());
        assertTrue(this.notificationList.isEmpty());
        assertFalse(this.hashMap.containsKey(this.sharedKey(testEntity.getId())));
    }

    @Test
    void anotherJvmCanChangeOneSharedFieldWithoutOverwritingTheOthers() {
        final HolderComponents<TestEntity> proxyHolderComponents = this.createHolder(this.proxyRedisDriver);
        final HolderComponents<TestEntity> paperHolderComponents = this.createHolder(this.paperRedisDriver);
        final TestEntity proxyTestEntity = this.pin(proxyHolderComponents, UUID.randomUUID());
        final TestEntity paperTestEntity = this.pin(paperHolderComponents, proxyTestEntity.getId());
        proxyTestEntity.setAdministrating(true);
        proxyHolderComponents.getHolderWriter().update(proxyTestEntity);

        paperTestEntity.setVanished(true);
        paperHolderComponents.getHolderWriter().update(paperTestEntity);
        this.deliverNotifications();

        assertTrue(proxyHolderComponents.getLocalStorage().get(proxyTestEntity.getId()).orElseThrow().isVanished());
        assertTrue(paperHolderComponents.getLocalStorage().get(proxyTestEntity.getId()).orElseThrow().isAdministrating());
    }

    @Test
    void redisFailureRunsTheFailureCallbackAndLeavesTheChangeRetryable() {
        final HolderComponents<TestEntity> holderComponents = this.createHolder(this.proxyRedisDriver);
        final TestEntity testEntity = this.pin(holderComponents, UUID.randomUUID());
        final AtomicInteger commitCount = new AtomicInteger();
        final AtomicInteger failureCount = new AtomicInteger();
        testEntity.setAdministrating(true);
        when(this.proxyRedisDriver.getCommands()).thenThrow(new ConnectionException("Redis unavailable"));

        assertThrows(ConnectionException.class, () -> holderComponents.getHolderWriter().update(testEntity, commitCount::incrementAndGet, failureCount::incrementAndGet));

        assertEquals(0, commitCount.get());
        this.await(() -> failureCount.get() == 1);
        assertEquals(1, failureCount.get());
        assertEquals(List.of(ADMINISTRATING), holderComponents.getChangeTracker().diff(testEntity));
        assertTrue(this.pendingWriteStore.isEmpty());
    }

    @Test
    void persistentUpdatesQueueOnlySqlFieldsAndKeepSharedState() {
        final HolderComponents<TestEntity> holderComponents = this.createHolder(this.proxyRedisDriver);
        final TestEntity testEntity = this.pin(holderComponents, UUID.randomUUID());
        final RepositoryWriter<TestEntity> repositoryWriter = new RepositoryWriter<>(this.pendingWriteStore, this.tableSchema, null, REDIS_NAMESPACE, "proxy", new EntityRevisionStore());
        doAnswer(invocation -> {
            repositoryWriter.update(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4), invocation.getArgument(5));
            return null;
        }).when(this.repository).update(any(), anyCollection(), anyList(), anyList(), eq(true), anyString());
        testEntity.setAdministrating(true);
        testEntity.setName("owner");
        final AtomicInteger commitCount = new AtomicInteger();

        holderComponents.getHolderWriter().update(testEntity, commitCount::incrementAndGet);

        final PendingWrite pendingWrite = this.pendingWriteStore.drain().getFirst();
        assertEquals(Map.of(this.tableSchema.getField(NAME), "owner"), pendingWrite.getValueMap());
        assertEquals(0, commitCount.get());
        assertEquals("1true", this.hashMap.get(this.sharedKey(testEntity.getId())).get("administrating"));
    }

    @Test
    void cacheRefillAndDatabaseRefreshRestoreSharedStateIncludingNulls() {
        final UUID id = UUID.randomUUID();
        final RedisStorage<TestEntity> redisStorage = new RedisStorage<>(this.proxyRedisDriver, REDIS_NAMESPACE, TestEntity.class, Duration.ofMinutes(5));
        final Map<String, String> changedValueMap = new HashMap<>();
        changedValueMap.put("administrating", "true");
        changedValueMap.put("note", null);
        redisStorage.synchronizeProperties(id, changedValueMap, "proxy");
        final TestEntity databaseTestEntity = new TestEntity(id);
        databaseTestEntity.setName("owner");
        databaseTestEntity.setNote("default-note");

        redisStorage.cache(databaseTestEntity);

        assertTrue(databaseTestEntity.isAdministrating());
        assertNull(databaseTestEntity.getNote());
        assertFalse(this.hashMap.get(REDIS_NAMESPACE.getKey(id)).containsKey("administrating"));
        this.hashMap.remove(REDIS_NAMESPACE.getKey(id));
        assertTrue(redisStorage.get(id).isEmpty());

        final TestEntity refreshedTestEntity = redisStorage.refresh(id, () -> Optional.of(new TestEntity(id))).orElseThrow();

        assertTrue(refreshedTestEntity.isAdministrating());
        assertNull(refreshedTestEntity.getNote());
        assertTrue(redisStorage.get(id).orElseThrow().isAdministrating());
    }

    @Test
    void deletionMarksSharedStateForRemovalButAnUpdateRetainsIt() {
        final RepositoryWriter<TestEntity> repositoryWriter = new RepositoryWriter<>(this.pendingWriteStore, this.tableSchema, null, REDIS_NAMESPACE, "proxy", new EntityRevisionStore());
        final TestEntity testEntity = new TestEntity(UUID.randomUUID());

        repositoryWriter.delete(testEntity, List.of(), List.of(), true, "proxy");

        final PendingWrite pendingWrite = this.pendingWriteStore.drain().getFirst();
        assertTrue(pendingWrite.getInvalidation().isRemoveSharedState());
        assertTrue(pendingWrite.getInvalidation().withInstanceId("mixed").isRemoveSharedState());
        assertFalse(repositoryWriter.createInvalidation(testEntity.getId(), true, "proxy").isRemoveSharedState());
    }

    private void await(final BooleanSupplier condition) {
        final long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "Callback did not complete");
            try {
                Thread.sleep(1L);
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
    }

    private void configureDriver(final RedisDriver redisDriver) {
        when(redisDriver.getCommands()).thenReturn(this.redisCommands);
        doAnswer(invocation -> {
            this.subscriberList.add(invocation.getArgument(1));
            return null;
        }).when(redisDriver).subscribe(eq(REDIS_NAMESPACE.getInvalidationChannel()), any());
    }

    private HolderComponents<TestEntity> createHolder(final RedisDriver redisDriver) {
        when(this.coreDatabase.getRedisDriver()).thenReturn(Optional.of(redisDriver));

        return HolderComponents.create(this.repository, InstanceMode.MULTI_INSTANCE, RedisMode.ENABLED, Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(5));
    }

    private TestEntity pin(final HolderComponents<TestEntity> holderComponents, final UUID id) {
        final TestEntity testEntity = new TestEntity(id);
        testEntity.setName("initial");
        holderComponents.getLocalEntityIndex().pin(testEntity, holderComponents.getLocalStorage());
        holderComponents.getChangeTracker().snapshot(testEntity);
        this.hashMap.put(REDIS_NAMESPACE.getKey(id), new HashMap<>(Map.of("name", "initial")));

        return testEntity;
    }

    private String sharedKey(final UUID id) {
        return REDIS_NAMESPACE.getKey("cache:shared:%s".formatted(id));
    }

    private void deliverNotifications() {
        final List<Runnable> notificationCopyList = List.copyOf(this.notificationList);
        this.notificationList.clear();
        notificationCopyList.forEach(Runnable::run);
        assertTrue(this.notificationList.isEmpty(), "A remote refresh must not publish its incoming state again");
    }

    private Answer<Object> redisAnswer() {
        return invocation -> {
            final Object[] argumentArray = invocation.getRawArguments();

            return switch (invocation.getMethod().getName()) {
                case "hgetall" -> new HashMap<>(this.hashMap.getOrDefault((String) argumentArray[0], Map.of()));
                case "scriptLoad" -> argumentArray[0];
                case "set" -> "OK";
                case "eval" -> 1L;
                case "evalsha" -> {
                    assertSame(ScriptOutputType.INTEGER, argumentArray[1]);
                    final String[] keyArray = (String[]) argumentArray[2];
                    final String[] valueArray = (String[]) argumentArray[3];
                    final Map<String, String> valueMap = this.hashMap.computeIfAbsent(keyArray[0], _ -> new HashMap<>());

                    if (keyArray.length == 2 && keyArray[1].equals(REDIS_NAMESPACE.getInvalidationChannel())) {
                        assertEquals("0", valueArray[0], "Shared state must survive ordinary database cache expiry");

                        for (int index = 2; index < valueArray.length; index += 2) {
                            valueMap.put(valueArray[index], valueArray[index + 1]);
                        }

                        this.subscriberList.forEach(consumer -> this.notificationList.add(() -> consumer.accept(valueArray[1])));
                    } else {
                        final int offset = keyArray.length == 2 ? 1 : 0;
                        final int count = Integer.parseInt(valueArray[offset]);

                        for (int index = 0; index < count; index++) {
                            valueMap.put(valueArray[offset + 1 + index * 2], valueArray[offset + 2 + index * 2]);
                        }
                    }

                    yield 1L;
                }
                default -> null;
            };
        };
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    @Setter
    private static final class TestEntity implements Entity {

        @EqualsAndHashCode.Include
        private final UUID id;

        private String name, note;

        private boolean administrating, vanished;
    }
}