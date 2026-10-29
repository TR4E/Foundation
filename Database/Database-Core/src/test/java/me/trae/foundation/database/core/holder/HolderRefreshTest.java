package me.trae.foundation.database.core.holder;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import io.lettuce.core.api.sync.RedisCommands;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.ConnectionException;
import me.trae.foundation.database.api.holder.InstanceMode;
import me.trae.foundation.database.api.holder.RedisMode;
import me.trae.foundation.database.api.holder.LookupTier;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.repository.index.EntityIndex;
import me.trae.foundation.database.core.CoreDatabase;
import me.trae.foundation.database.core.batch.BatchQueue;
import me.trae.foundation.database.core.holder.change.ChangeTracker;
import me.trae.foundation.database.core.holder.claim.UniqueClaimer;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.lookup.TieredLookup;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.local.LocalStorage;
import me.trae.foundation.database.storage.redis.RedisNamespace;
import me.trae.foundation.database.storage.revision.EntityRevisionStore;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

final class HolderRefreshTest {

    private static final EntityProperty<TestEntity, String> NAME = EntityProperty.register(TestEntity.class, "name", TestEntity::getName, TestEntity::setName, String.class, true);
    private static final EntityProperty<TestEntity, String> SESSION = EntityProperty.register(TestEntity.class, "session", TestEntity::getSession, TestEntity::setSession, String.class, false);

    private static final EntityIndex<TestEntity> NAME_INDEX = EntityIndex.of("name", NAME);

    @SuppressWarnings("unchecked")
    private final AbstractEntityRepository<TestEntity> repository = mock(AbstractEntityRepository.class);

    @SuppressWarnings("unchecked")
    private final HolderLookups<TestEntity> holderLookups = mock(HolderLookups.class);

    @SuppressWarnings("unchecked")
    private final TieredLookup<UUID, TestEntity> tieredLookup = mock(TieredLookup.class);

    @SuppressWarnings("unchecked")
    private final UniqueClaimer<TestEntity> uniqueClaimer = mock(UniqueClaimer.class);

    private final LocalStorage<UUID, TestEntity> localStorage = new LocalStorage<>(Duration.ofMinutes(5));

    private final LocalEntityIndex<TestEntity> localEntityIndex = new LocalEntityIndex<>(List.of(NAME_INDEX));

    private final ChangeTracker<TestEntity> changeTracker = new ChangeTracker<>(TestEntity.class, Duration.ofMinutes(5));

    private final HolderWriter<TestEntity> holderWriter = new HolderWriter<>(this.repository, this.localStorage, this.localEntityIndex, null, this.changeTracker, this.uniqueClaimer, true);

    @BeforeEach
    void configureLookups() {
        when(this.repository.getProperties()).thenReturn(List.of(NAME, SESSION));
        when(this.holderLookups.getIdLookup()).thenReturn(this.tieredLookup);
    }

    @Test
    void refreshesPinnedEntityWithoutLosingLocalStateOrIndexes() {
        final TestEntity localTestEntity = this.cacheEntity(true);
        final TestEntity refreshedTestEntity = this.remoteEntity(localTestEntity.getId());

        this.holderWriter.refreshLocal(localTestEntity.getId(), this.holderLookups);

        assertSame(refreshedTestEntity, this.localStorage.get(localTestEntity.getId()).orElseThrow());
        assertEquals("owner", refreshedTestEntity.getName());
        assertEquals("local-session", refreshedTestEntity.getSession());
        assertTrue(this.localStorage.isPinned(localTestEntity.getId()));
        assertEquals(List.of(refreshedTestEntity), this.localStorage.getPinnedValues());
        assertTrue(this.localEntityIndex.get(NAME_INDEX, new Object[]{"default"}, this.localStorage).isEmpty());
        assertSame(refreshedTestEntity, this.localEntityIndex.get(NAME_INDEX, new Object[]{"owner"}, this.localStorage).orElseThrow());
        assertTrue(this.changeTracker.isTracked(refreshedTestEntity));
        assertTrue(this.changeTracker.diff(refreshedTestEntity).isEmpty());
    }

    @Test
    void refreshesUnpinnedEntityForLocalOnlyReaders() {
        final TestEntity localTestEntity = this.cacheEntity(false);
        final TestEntity refreshedTestEntity = this.remoteEntity(localTestEntity.getId());

        this.holderWriter.refreshLocal(localTestEntity.getId(), this.holderLookups);

        assertSame(refreshedTestEntity, this.localStorage.get(localTestEntity.getId()).orElseThrow());
        assertFalse(this.localStorage.isPinned(localTestEntity.getId()));
        assertEquals("local-session", refreshedTestEntity.getSession());
    }

    @Test
    void removesDeletedEntityAndItsPin() {
        final TestEntity localTestEntity = this.cacheEntity(true);
        when(this.tieredLookup.lookup(localTestEntity.getId(), EnumSet.of(LookupTier.REDIS, LookupTier.DATABASE))).thenReturn(Optional.empty());

        this.holderWriter.refreshLocal(localTestEntity.getId(), this.holderLookups);

        assertTrue(this.localStorage.get(localTestEntity.getId()).isEmpty());
        assertFalse(this.localStorage.isPinned(localTestEntity.getId()));
        assertFalse(this.changeTracker.isTracked(localTestEntity.getId()));
        assertTrue(this.localEntityIndex.get(NAME_INDEX, new Object[]{"default"}, this.localStorage).isEmpty());
    }

    @Test
    void failedReloadKeepsTheExistingPinnedEntity() {
        final TestEntity localTestEntity = this.cacheEntity(true);
        when(this.tieredLookup.lookup(localTestEntity.getId(), EnumSet.of(LookupTier.REDIS, LookupTier.DATABASE))).thenThrow(new IllegalStateException("unavailable"));

        assertThrows(IllegalStateException.class, () -> this.holderWriter.refreshLocal(localTestEntity.getId(), this.holderLookups));

        assertSame(localTestEntity, this.localStorage.get(localTestEntity.getId()).orElseThrow());
        assertTrue(this.localStorage.isPinned(localTestEntity.getId()));
        assertTrue(this.changeTracker.isTracked(localTestEntity));
    }

    @Test
    void ignoresInvalidationsForEntitiesThatAreNotCached() {
        this.holderWriter.refreshLocal(UUID.randomUUID(), this.holderLookups);

        verifyNoInteractions(this.tieredLookup);
    }

    @Test
    void preservesLocalChangesWhenReloading() {
        final TestEntity localTestEntity = this.cacheEntity(true);
        localTestEntity.setName("local-edit");
        this.remoteEntity(localTestEntity.getId());

        this.holderWriter.refreshLocal(localTestEntity.getId(), this.holderLookups);

        final TestEntity refreshedTestEntity = this.localStorage.get(localTestEntity.getId()).orElseThrow();
        assertEquals("local-edit", refreshedTestEntity.getName());
        assertEquals("local-session", refreshedTestEntity.getSession());
        assertTrue(this.changeTracker.diff(refreshedTestEntity).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void subscriptionReloadsFromRedisAndReconnectFallsBackToDatabase() {
        final CoreDatabase coreDatabase = mock(CoreDatabase.class);
        final RedisDriver redisDriver = mock(RedisDriver.class);
        final BatchQueue batchQueue = mock(BatchQueue.class);
        final TableSchema<TestEntity> tableSchema = mock(TableSchema.class);
        final RedisCommands<String, String> redisCommands = mock(RedisCommands.class);
        when(this.repository.getCoreDatabase()).thenReturn(coreDatabase);
        when(this.repository.getEntityType()).thenReturn(TestEntity.class);
        when(this.repository.getTable()).thenReturn("holder_refresh");
        when(this.repository.getTableSchema()).thenReturn(tableSchema);
        when(this.repository.getTenantId()).thenReturn(Optional.empty());
        when(this.repository.getEntityIndexes()).thenReturn(List.of());
        when(coreDatabase.getRedisDriver()).thenReturn(Optional.of(redisDriver));
        when(coreDatabase.getEntityRevisionStore()).thenReturn(new EntityRevisionStore());
        when(coreDatabase.getBatchQueue()).thenReturn(batchQueue);
        when(redisDriver.getCommands()).thenReturn(redisCommands);

        final HolderComponents<TestEntity> holderComponents = HolderComponents.create(this.repository, InstanceMode.MULTI_INSTANCE, RedisMode.ENABLED, Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(5));
        final ArgumentCaptor<Consumer<String>> consumerArgumentCaptor = ArgumentCaptor.forClass(Consumer.class);
        final RedisNamespace redisNamespace = RedisNamespace.of("holder_refresh");
        verify(redisDriver).subscribe(eq(redisNamespace.getInvalidationChannel()), consumerArgumentCaptor.capture());

        final TestEntity testEntity = new TestEntity(UUID.randomUUID());
        testEntity.setName("default");
        testEntity.setSession("local-session");
        holderComponents.getLocalEntityIndex().pin(testEntity, holderComponents.getLocalStorage());
        holderComponents.getChangeTracker().snapshot(testEntity);
        when(redisCommands.hgetall(redisNamespace.getKey(testEntity.getId()))).thenReturn(Map.of("name", "owner"));

        consumerArgumentCaptor.getValue().accept("remote|%s".formatted(testEntity.getId()));

        final TestEntity redisTestEntity = holderComponents.getLocalStorage().get(testEntity.getId()).orElseThrow();
        assertEquals("owner", redisTestEntity.getName());
        assertEquals("local-session", redisTestEntity.getSession());
        assertTrue(holderComponents.getLocalStorage().isPinned(testEntity.getId()));

        final TestEntity databaseTestEntity = new TestEntity(testEntity.getId());
        databaseTestEntity.setName("admin");
        when(redisCommands.hgetall(anyString())).thenThrow(new ConnectionException("Redis unavailable"));
        when(this.repository.findById(testEntity.getId())).thenReturn(Optional.of(databaseTestEntity));

        consumerArgumentCaptor.getValue().accept("*");

        assertSame(databaseTestEntity, holderComponents.getLocalStorage().get(testEntity.getId()).orElseThrow());
        assertEquals("admin", databaseTestEntity.getName());
        assertEquals("local-session", databaseTestEntity.getSession());
        assertTrue(holderComponents.getLocalStorage().isPinned(testEntity.getId()));
        verifyNoInteractions(batchQueue);
    }

    private TestEntity cacheEntity(final boolean pinned) {
        final TestEntity testEntity = new TestEntity(UUID.randomUUID());
        testEntity.setName("default");
        testEntity.setSession("local-session");

        if (pinned) {
            this.localEntityIndex.pin(testEntity, this.localStorage);
        } else {
            this.localEntityIndex.put(testEntity, this.localStorage);
        }

        this.changeTracker.snapshot(testEntity);

        return testEntity;
    }

    private TestEntity remoteEntity(final UUID id) {
        final TestEntity testEntity = new TestEntity(id);
        testEntity.setName("owner");
        testEntity.setSession("remote-session");
        when(this.tieredLookup.lookup(id, EnumSet.of(LookupTier.REDIS, LookupTier.DATABASE))).thenReturn(Optional.of(testEntity));

        return testEntity;
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    @Setter
    private static final class TestEntity implements Entity {

        @EqualsAndHashCode.Include
        private final UUID id;

        private String name, session;
    }
}