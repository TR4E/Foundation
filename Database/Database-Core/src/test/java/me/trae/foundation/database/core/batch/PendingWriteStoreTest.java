package me.trae.foundation.database.core.batch;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.tenant.TenantScope;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.storage.redis.RedisNamespace;
import me.trae.foundation.database.storage.revision.EntityRevisionStore;
import me.trae.foundation.database.storage.revision.RevisionState;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PendingWriteStoreTest {

    private static final TableSchema<TestEntity> TABLE_SCHEMA = new TableSchema<>(TestEntity.class, "pending_write_callback_test", TenantScope.NONE, List.of());
    private static final Field<String> VALUE_FIELD = DSL.field("value", String.class);

    @Test
    void callbacksRemainBoundedAcrossCoalescingAndRetries() {
        final PendingWriteStore pendingWriteStore = new PendingWriteStore(1, 3);

        final UUID id = UUID.randomUUID();

        final List<Integer> callbackOrder = new ArrayList<>();

        final Runnable firstCallback = () -> callbackOrder.add(1);

        final Runnable secondCallback = () -> callbackOrder.add(2);

        final Runnable thirdCallback = () -> callbackOrder.add(3);

        pendingWriteStore.queueUpdate(TABLE_SCHEMA, null, id, this.value("first"), List.of(firstCallback, secondCallback));
        final PendingWrite inFlight = pendingWriteStore.drain().getFirst();
        pendingWriteStore.queueUpdate(TABLE_SCHEMA, null, id, this.value("second"), List.of(thirdCallback));

        assertThrows(DatabaseException.class, () -> pendingWriteStore.queueUpdate(TABLE_SCHEMA, null, id, this.value("overflow"), List.of(() -> {})));
        assertEquals(3, pendingWriteStore.getPendingCallbackCount());

        pendingWriteStore.requeue(inFlight);
        final PendingWrite retried = pendingWriteStore.drain().getFirst();
        assertEquals(List.of(firstCallback, secondCallback, thirdCallback), retried.getCommitCallbackList());

        pendingWriteStore.requeue(retried);
        final PendingWrite completed = pendingWriteStore.drain().getFirst();
        completed.getCommitCallbackList().forEach(Runnable::run);
        pendingWriteStore.complete(completed);
        pendingWriteStore.complete(completed);

        assertEquals(List.of(1, 2, 3), callbackOrder);
        assertEquals(0, pendingWriteStore.getPendingCallbackCount());
    }

    @Test
    void simultaneousCallbackRegistrationsCannotExceedCapacity() throws Exception {
        final int callbackCapacity = 200;

        final int registrationCount = 500;

        final PendingWriteStore pendingWriteStore = new PendingWriteStore(1, callbackCapacity);

        final UUID id = UUID.randomUUID();

        final CountDownLatch readyLatch = new CountDownLatch(registrationCount);

        final CountDownLatch startLatch = new CountDownLatch(1);

        final AtomicInteger acceptedCount = new AtomicInteger();

        try (final var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < registrationCount; index++) {
                executorService.submit(() -> {
                    readyLatch.countDown();

                    try {
                        startLatch.await();
                        pendingWriteStore.queueUpdate(TABLE_SCHEMA, null, id, this.value("shared"), List.of(() -> {}));
                        acceptedCount.incrementAndGet();
                    } catch (final DatabaseException _) {
                        // Capacity rejection is expected for registrations beyond the limit.
                    } catch (final InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();
        }

        assertEquals(callbackCapacity, acceptedCount.get());
        assertEquals(callbackCapacity, pendingWriteStore.getPendingCallbackCount());
        assertEquals(callbackCapacity, pendingWriteStore.drain().getFirst().getCommitCallbackList().size());
    }

    @Test
    void completedWriteRebasesOnlyQueuedWritesFromTheSameEntity() {
        final TableSchema<RevisionTestEntity> revisionSchema = new TableSchema<>(RevisionTestEntity.class, "pending_write_revision_test", TenantScope.NONE, List.of(), true);

        final PendingWriteStore pendingWriteStore = new PendingWriteStore(2, 4);

        final RevisionTestEntity entity = new RevisionTestEntity(UUID.randomUUID());

        final EntityRevisionStore revisionStore = new EntityRevisionStore();

        final RevisionState revisionState = revisionStore.getState(entity);

        pendingWriteStore.queueUpdate(revisionSchema, null, entity.getId(), this.value("first"), List.of(), null, 0L, () -> revisionState.setRevision(1L), revisionState);
        final PendingWrite inFlight = pendingWriteStore.drain().getFirst();
        pendingWriteStore.queueUpdate(revisionSchema, null, entity.getId(), this.value("newer"), List.of(), null, 0L, () -> revisionState.setRevision(1L), revisionState);

        revisionState.setRevision(1L);
        pendingWriteStore.complete(inFlight);

        final PendingWrite rebased = pendingWriteStore.drain().getFirst();
        assertEquals(1L, rebased.getExpectedRevision());
        assertEquals(2L, rebased.getValueMap().get(TableSchema.REVISION_FIELD));
        rebased.getRevisionCallback().run();
        pendingWriteStore.complete(rebased);
        assertEquals(2L, revisionState.getRevision());

        final RevisionTestEntity currentEntity = new RevisionTestEntity(UUID.randomUUID());

        final RevisionTestEntity secondStaleCopy = new RevisionTestEntity(currentEntity.getId());

        final RevisionState currentRevisionState = revisionStore.getState(currentEntity);

        final RevisionState staleRevisionState = revisionStore.getState(secondStaleCopy);
        pendingWriteStore.queueUpdate(revisionSchema, null, currentEntity.getId(), this.value("current"), List.of(), null, 0L, () -> currentRevisionState.setRevision(1L), currentRevisionState);
        final PendingWrite secondInFlight = pendingWriteStore.drain().getFirst();
        pendingWriteStore.queueUpdate(revisionSchema, null, secondStaleCopy.getId(), this.value("stale"), List.of(), null, 0L, () -> staleRevisionState.setRevision(1L), staleRevisionState);

        currentRevisionState.setRevision(1L);
        pendingWriteStore.complete(secondInFlight);

        final PendingWrite stalePending = pendingWriteStore.drain().getFirst();
        assertEquals(0L, stalePending.getExpectedRevision());
    }

    @Test
    void coalescedDeleteDoesNotAdvanceRevisionOfDeletedEntity() {
        final TableSchema<RevisionTestEntity> revisionSchema = new TableSchema<>(RevisionTestEntity.class, "pending_write_revision_delete_test", TenantScope.NONE, List.of(), true);

        final RevisionTestEntity entity = new RevisionTestEntity(UUID.randomUUID());

        final PendingWriteStore pendingWriteStore = new PendingWriteStore(1, 1);

        final RevisionState revisionState = new EntityRevisionStore().getState(entity);

        pendingWriteStore.queueUpdate(revisionSchema, null, entity.getId(), this.value("value"), List.of(), null, 0L, () -> revisionState.setRevision(1L), revisionState);
        pendingWriteStore.queueDelete(revisionSchema, null, entity.getId(), List.of(), null, 0L, revisionState);

        assertNull(pendingWriteStore.drain().getFirst().getRevisionCallback());
    }

    @Test
    void createAndUpdateCoalesceIntoOneInsert() {
        final PendingWriteStore pendingWriteStore = new PendingWriteStore();

        final UUID id = UUID.randomUUID();

        pendingWriteStore.queueInsert(TABLE_SCHEMA, null, id, this.value("created"), List.of(), null);
        pendingWriteStore.queueUpdate(TABLE_SCHEMA, null, id, this.value("updated"), List.of(), List.of(), null, null, null, null);

        final PendingWrite pendingWrite = pendingWriteStore.drain().getFirst();
        assertEquals(WriteType.INSERT, pendingWrite.getWriteType());
        assertEquals("updated", pendingWrite.getValueMap().get(VALUE_FIELD));
    }

    @Test
    void failedInFlightCreateCancelsDependentUpdateAndRestoresCallbackCapacity() {
        final PendingWriteStore pendingWriteStore = new PendingWriteStore(1, 2);

        final UUID id = UUID.randomUUID();

        final AtomicInteger failureCount = new AtomicInteger();

        pendingWriteStore.queueInsert(TABLE_SCHEMA, null, id, this.value("created"), List.of(), List.of(failureCount::incrementAndGet), null);
        final PendingWrite inFlight = pendingWriteStore.drain().getFirst();
        pendingWriteStore.queueUpdate(TABLE_SCHEMA, null, id, this.value("updated"), List.of(), List.of(failureCount::incrementAndGet), null, null, null, null);

        final List<PendingWrite> failedWriteList = pendingWriteStore.fail(inFlight, new DatabaseException("insert rejected"));
        failedWriteList.forEach(failedWrite -> failedWrite.getFailureCallbackList().forEach(Runnable::run));

        assertEquals(2, failureCount.get());
        assertEquals(0, pendingWriteStore.getPendingCallbackCount());
        assertEquals(0, pendingWriteStore.size());
        assertThrows(DatabaseException.class, () -> pendingWriteStore.queueInsert(TABLE_SCHEMA, null, id, this.value("retry"), List.of(), List.of(() -> {}, () -> {}, () -> {}), null));
    }

    @Test
    void rejectsUnsafeWriteOrderingWithoutChangingQueuedWrite() {
        final PendingWriteStore pendingWriteStore = new PendingWriteStore();

        final UUID id = UUID.randomUUID();

        pendingWriteStore.queueInsert(TABLE_SCHEMA, null, id, this.value("created"), List.of(), null);
        assertThrows(DatabaseException.class, () -> pendingWriteStore.queueDelete(TABLE_SCHEMA, null, id));

        final PendingWrite pendingWrite = pendingWriteStore.drain().getFirst();
        assertEquals(WriteType.INSERT, pendingWrite.getWriteType());
        assertEquals("created", pendingWrite.getValueMap().get(VALUE_FIELD));
    }

    private Map<Field<?>, Object> value(final String value) {
        return Map.of(VALUE_FIELD, value);
    }

    @Test
    void coalescedWritesKeepTheirOriginOnlyWhenEveryWriteHasTheSameSource() {
        final PendingWriteStore pendingWriteStore = new PendingWriteStore();
        final UUID id = UUID.randomUUID();
        final RedisNamespace redisNamespace = RedisNamespace.of(TABLE_SCHEMA.getTableName());
        pendingWriteStore.queueUpdate(TABLE_SCHEMA, null, id, this.value("first"), List.of(), redisNamespace.getInvalidation(id, "holder-a"));
        pendingWriteStore.queueUpdate(TABLE_SCHEMA, null, id, this.value("second"), List.of(), redisNamespace.getInvalidation(id, "holder-a"));

        final PendingWrite firstWrite = pendingWriteStore.drain().getFirst();
        assertEquals("holder-a", firstWrite.getInvalidation().getInstanceId());

        pendingWriteStore.queueUpdate(TABLE_SCHEMA, null, id, this.value("third"), List.of(), redisNamespace.getInvalidation(id, "holder-b"));
        pendingWriteStore.requeue(firstWrite);

        final PendingWrite mergedWrite = pendingWriteStore.drain().getFirst();
        assertEquals("mixed", mergedWrite.getInvalidation().getInstanceId());
        assertEquals("third", mergedWrite.getValueMap().get(VALUE_FIELD));
    }

    @Test
    void mergingAnUnattributedWriteDoesNotSkipAnyHolder() {
        final PendingWriteStore pendingWriteStore = new PendingWriteStore();
        final UUID id = UUID.randomUUID();
        pendingWriteStore.queueUpdate(TABLE_SCHEMA, null, id, this.value("first"), List.of());
        pendingWriteStore.queueUpdate(TABLE_SCHEMA, null, id, this.value("second"), List.of(), RedisNamespace.of(TABLE_SCHEMA.getTableName()).getInvalidation(id, "holder-a"));

        assertEquals("mixed", pendingWriteStore.drain().getFirst().getInvalidation().getInstanceId());
    }

    @Test
    void pendingValuesIncludeInFlightWritesAndNewerNullsWithoutCrossingTenants() {
        final PendingWriteStore pendingWriteStore = new PendingWriteStore();
        final UUID id = UUID.randomUUID();
        pendingWriteStore.queueUpdate(TABLE_SCHEMA, "tenant-a", id, this.value("in-flight"), List.of());

        final PendingWrite pendingWrite = pendingWriteStore.drain().getFirst();
        assertEquals("in-flight", pendingWriteStore.getPendingValues(TABLE_SCHEMA, "tenant-a", id).get(VALUE_FIELD));

        final Map<Field<?>, Object> valueMap = new HashMap<>();
        valueMap.put(VALUE_FIELD, null);
        pendingWriteStore.queueUpdate(TABLE_SCHEMA, "tenant-a", id, valueMap, List.of());

        final Map<Field<?>, Object> pendingValueMap = pendingWriteStore.getPendingValues(TABLE_SCHEMA, "tenant-a", id);
        assertTrue(pendingValueMap.containsKey(VALUE_FIELD));
        assertNull(pendingValueMap.get(VALUE_FIELD));
        assertTrue(pendingWriteStore.getPendingValues(TABLE_SCHEMA, "tenant-b", id).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> pendingValueMap.put(VALUE_FIELD, "modified"));
        pendingWriteStore.complete(pendingWrite);
        assertTrue(pendingWriteStore.getPendingValues(TABLE_SCHEMA, "tenant-a", id).containsKey(VALUE_FIELD));
    }

    private static final class TestEntity implements Entity {

        @Override
        public UUID getId() {
            return null;
        }
    }

    private static final class RevisionTestEntity implements Entity {

        private final UUID id;

        private RevisionTestEntity(final UUID id) {
            this.id = id;
        }

        @Override
        public UUID getId() {
            return this.id;
        }
    }
}