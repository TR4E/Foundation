package me.trae.foundation.database.core.batch;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.entity.RevisionedEntity;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.tenant.TenantScope;
import me.trae.foundation.database.core.schema.TableSchema;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        pendingWriteStore.queueUpsert(TABLE_SCHEMA, null, id, this.value("first"), List.of(firstCallback, secondCallback));
        final PendingWrite inFlight = pendingWriteStore.drain().getFirst();
        pendingWriteStore.queueUpsert(TABLE_SCHEMA, null, id, this.value("second"), List.of(thirdCallback));

        assertThrows(DatabaseException.class, () -> pendingWriteStore.queueUpsert(TABLE_SCHEMA, null, id, this.value("overflow"), List.of(() -> {})));
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
                        pendingWriteStore.queueUpsert(TABLE_SCHEMA, null, id, this.value("shared"), List.of(() -> {}));
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

        pendingWriteStore.queueUpsert(revisionSchema, null, entity.getId(), this.value("first"), List.of(), null, 0L, () -> entity.setRevision(1L), entity);
        final PendingWrite inFlight = pendingWriteStore.drain().getFirst();
        pendingWriteStore.queueUpsert(revisionSchema, null, entity.getId(), this.value("newer"), List.of(), null, 0L, () -> entity.setRevision(1L), entity);

        entity.setRevision(1L);
        pendingWriteStore.complete(inFlight);

        final PendingWrite rebased = pendingWriteStore.drain().getFirst();
        assertEquals(1L, rebased.getExpectedRevision());
        assertEquals(2L, rebased.getValueMap().get(TableSchema.REVISION_FIELD));
        rebased.getRevisionCallback().run();
        pendingWriteStore.complete(rebased);
        assertEquals(2L, entity.getRevision());

        final RevisionTestEntity currentEntity = new RevisionTestEntity(UUID.randomUUID());
        final RevisionTestEntity secondStaleCopy = new RevisionTestEntity(currentEntity.getId());
        pendingWriteStore.queueUpsert(revisionSchema, null, currentEntity.getId(), this.value("current"), List.of(), null, 0L, () -> currentEntity.setRevision(1L), currentEntity);
        final PendingWrite secondInFlight = pendingWriteStore.drain().getFirst();
        pendingWriteStore.queueUpsert(revisionSchema, null, secondStaleCopy.getId(), this.value("stale"), List.of(), null, 0L, () -> secondStaleCopy.setRevision(1L), secondStaleCopy);

        currentEntity.setRevision(1L);
        pendingWriteStore.complete(secondInFlight);

        final PendingWrite stalePending = pendingWriteStore.drain().getFirst();
        assertEquals(0L, stalePending.getExpectedRevision());
    }

    @Test
    void coalescedDeleteDoesNotAdvanceRevisionOfDeletedEntity() {
        final TableSchema<RevisionTestEntity> revisionSchema = new TableSchema<>(RevisionTestEntity.class, "pending_write_revision_delete_test", TenantScope.NONE, List.of(), true);
        final RevisionTestEntity entity = new RevisionTestEntity(UUID.randomUUID());
        final PendingWriteStore pendingWriteStore = new PendingWriteStore(1, 1);

        pendingWriteStore.queueUpsert(revisionSchema, null, entity.getId(), this.value("value"), List.of(), null, 0L, () -> entity.setRevision(1L), entity);
        pendingWriteStore.queueDelete(revisionSchema, null, entity.getId(), List.of(), null, 0L, entity);

        assertNull(pendingWriteStore.drain().getFirst().getRevisionCallback());
    }

    private Map<Field<?>, Object> value(final String value) {
        return Map.of(VALUE_FIELD, value);
    }

    private static final class TestEntity implements Entity {

        @Override
        public UUID getId() {
            return null;
        }
    }

    private static final class RevisionTestEntity implements RevisionedEntity {

        private final UUID id;
        private long revision;

        private RevisionTestEntity(final UUID id) {
            this.id = id;
        }

        @Override
        public UUID getId() {
            return this.id;
        }

        @Override
        public long getRevision() {
            return this.revision;
        }

        @Override
        public void setRevision(final long revision) {
            this.revision = revision;
        }
    }
}
