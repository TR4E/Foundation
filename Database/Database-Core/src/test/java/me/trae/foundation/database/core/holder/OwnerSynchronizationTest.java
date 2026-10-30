package me.trae.foundation.database.core.holder;

import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.ConnectionException;
import me.trae.foundation.database.api.exception.SynchronizationException;
import me.trae.foundation.database.api.holder.InstanceMode;
import me.trae.foundation.database.api.holder.RedisMode;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.tenant.TenantScope;
import me.trae.foundation.database.core.CoreDatabase;
import me.trae.foundation.database.core.batch.BatchQueue;
import me.trae.foundation.database.core.batch.PendingWrite;
import me.trae.foundation.database.core.batch.PendingWriteStore;
import me.trae.foundation.database.core.batch.WriteCommitCallback;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.core.repository.RepositoryWriter;
import me.trae.foundation.database.core.schema.TableSchema;
import me.trae.foundation.database.storage.driver.RedisDriver;
import me.trae.foundation.database.storage.redis.RedisEntityCoordinator;
import me.trae.foundation.database.storage.redis.RedisInvalidation;
import me.trae.foundation.database.storage.redis.RedisNamespace;
import me.trae.foundation.database.storage.redis.RedisStorage;
import me.trae.foundation.database.storage.revision.EntityRevisionStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class OwnerSynchronizationTest {

    private static final EntityProperty<TestEntity, String> NAME = EntityProperty.register(TestEntity.class, "name", TestEntity::getName, TestEntity::setName, String.class, true);
    private static final EntityProperty<TestEntity, Boolean> ADMINISTRATING = EntityProperty.register(TestEntity.class, "administrating", TestEntity::isAdministrating, TestEntity::setAdministrating, Boolean.class, false);

    private static final RedisNamespace NAMESPACE = RedisNamespace.of("owner_test");

    private final Transport transport = new Transport();

    private final List<RedisEntityCoordinator> coordinatorList = new ArrayList<>();

    @AfterEach
    void closeCoordinators() {
        this.coordinatorList.forEach(RedisEntityCoordinator::close);
    }

    @Test
    void noOwnersCompleteImmediatelyWithoutNotifications() throws Exception {
        final RedisEntityCoordinator coordinator = this.driver().getEntityCoordinator();
        final RedisInvalidation invalidation = NAMESPACE.getInvalidation(UUID.randomUUID(), "source");

        coordinator.notifyOwnersNow(invalidation, invalidation.getNotificationId(), true).get(1L, TimeUnit.SECONDS);

        assertEquals(0, this.transport.notificationCount.get());
    }

    @Test
    void lateCacheRegistrationSeesTheGenerationPublishedBeforeItJoined() throws Exception {
        final RedisEntityCoordinator coordinator = this.driver().getEntityCoordinator();
        final UUID id = UUID.randomUUID();
        final RedisInvalidation invalidation = NAMESPACE.getInvalidation(id, "source");
        coordinator.notifyOwnersNow(invalidation, invalidation.getNotificationId(), true).get(1L, TimeUnit.SECONDS);

        final RedisEntityCoordinator.Admission admission = coordinator.admit(NAMESPACE.getKey(""), id, "late-owner");
        assertEquals(1L, admission.getGeneration());
        admission.getReadyFuture().complete(null);
    }

    @Test
    void initialSaveValuesDoNotOverwriteANewerSharedUpdate() throws Exception {
        final RedisStorage<TestEntity> redisStorage = new RedisStorage<>(this.driver(), NAMESPACE, TestEntity.class, Duration.ofMinutes(5));
        final UUID id = UUID.randomUUID();

        redisStorage.synchronizeProperties(id, Map.of("administrating", "true"), "source", false).get(1L, TimeUnit.SECONDS);
        redisStorage.synchronizeProperties(id, Map.of("administrating", "false"), "source", false, true).get(1L, TimeUnit.SECONDS);

        assertEquals("1true", this.transport.hashMap.get(NAMESPACE.getKey("cache:shared:%s".formatted(id))).get("administrating"));
    }

    @Test
    void originatingHolderDoesNotWaitForItsOwnRefresh() throws Exception {
        final RedisEntityCoordinator coordinator = this.driver().getEntityCoordinator();
        final UUID id = UUID.randomUUID();
        coordinator.bind(NAMESPACE.getKey(""), "source", _ -> {
            throw new AssertionError("Originating holder must not refresh itself");
        }, () -> {}, _ -> {});
        coordinator.admit(NAMESPACE.getKey(""), id, "source").getReadyFuture().complete(null);
        final RedisInvalidation invalidation = NAMESPACE.getInvalidation(id, "source");

        coordinator.notifyOwnersNow(invalidation, invalidation.getNotificationId(), true).get(1L, TimeUnit.SECONDS);

        assertEquals(0, this.transport.notificationCount.get());
    }

    @Test
    void expiredJvmLeaseIsNotIncludedInTheAcknowledgementWait() throws Exception {
        final RedisEntityCoordinator coordinator = this.driver().getEntityCoordinator();
        final UUID id = UUID.randomUUID();
        final RedisEntityCoordinator.Admission admission = coordinator.admit(NAMESPACE.getKey(""), id, "dead-owner");
        admission.getReadyFuture().complete(null);
        this.transport.stringMap.remove("foundation:entity-sync:lease:%s".formatted(admission.getRegistration().substring(0, 36)));
        final RedisInvalidation invalidation = NAMESPACE.getInvalidation(id, "source");

        coordinator.notifyOwnersNow(invalidation, invalidation.getNotificationId(), true).get(1L, TimeUnit.SECONDS);

        assertEquals(0, this.transport.notificationCount.get());
    }

    @Test
    void reconnectBlocksCacheReadsUntilRevalidationCompletes() throws Exception {
        final RedisEntityCoordinator coordinator = this.driver().getEntityCoordinator();
        final CountDownLatch recoveryStarted = new CountDownLatch(1);
        final CountDownLatch releaseRecovery = new CountDownLatch(1);
        coordinator.bind(NAMESPACE.getKey(""), "receiver", _ -> {}, () -> {
            recoveryStarted.countDown();
            this.await(releaseRecovery);
        }, _ -> {});
        coordinator.admit(NAMESPACE.getKey(""), UUID.randomUUID(), "receiver").getReadyFuture().complete(null);
        coordinator.connectionLost();
        assertThrows(ConnectionException.class, coordinator::checkLease);

        coordinator.reconnect();
        assertTrue(recoveryStarted.await(1L, TimeUnit.SECONDS));
        assertThrows(ConnectionException.class, coordinator::checkLease);
        releaseRecovery.countDown();
        final long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (true) {
            try {
                coordinator.checkLease();
                break;
            } catch (final ConnectionException exception) {
                assertTrue(System.nanoTime() < deadline);
                Thread.sleep(1L);
            }
        }
    }

    @Test
    void waitsForRefreshAndTargetsOnlyOwnersOfTheEntity() throws Exception {
        final RedisEntityCoordinator sender = this.driver().getEntityCoordinator();
        final RedisEntityCoordinator receiver = this.driver().getEntityCoordinator();
        final RedisEntityCoordinator unrelated = this.driver().getEntityCoordinator();
        final UUID id = UUID.randomUUID();
        final CountDownLatch refreshStarted = new CountDownLatch(1);
        final CountDownLatch releaseRefresh = new CountDownLatch(1);
        final AtomicInteger unrelatedCount = new AtomicInteger();
        receiver.bind(NAMESPACE.getKey(""), "receiver", _ -> {
            refreshStarted.countDown();
            this.await(releaseRefresh);
        }, () -> {}, _ -> {});
        unrelated.bind(NAMESPACE.getKey(""), "unrelated", _ -> unrelatedCount.incrementAndGet(), () -> {}, _ -> {});
        receiver.admit(NAMESPACE.getKey(""), id, "receiver").getReadyFuture().complete(null);
        unrelated.admit(NAMESPACE.getKey(""), UUID.randomUUID(), "unrelated").getReadyFuture().complete(null);

        final RedisInvalidation invalidation = NAMESPACE.getInvalidation(id, "source");
        final CompletableFuture<Void> future = sender.notifyOwnersNow(invalidation, invalidation.getNotificationId(), true);
        assertTrue(refreshStarted.await(1L, TimeUnit.SECONDS));
        assertFalse(future.isDone());
        releaseRefresh.countDown();
        future.get(1L, TimeUnit.SECONDS);

        assertEquals(1, this.transport.notificationCount.get());
        assertEquals(0, unrelatedCount.get());
    }

    @Test
    void groupsMultipleLocalHoldersIntoOneJvmNotification() throws Exception {
        final RedisEntityCoordinator coordinator = this.driver().getEntityCoordinator();
        final UUID id = UUID.randomUUID();
        final AtomicInteger refreshCount = new AtomicInteger();
        for (final String holder : List.of("first", "second")) {
            coordinator.bind(NAMESPACE.getKey(""), holder, _ -> refreshCount.incrementAndGet(), () -> {}, _ -> {});
            coordinator.admit(NAMESPACE.getKey(""), id, holder).getReadyFuture().complete(null);
        }

        final RedisInvalidation invalidation = NAMESPACE.getInvalidation(id, "source");
        coordinator.notifyOwnersNow(invalidation, invalidation.getNotificationId(), true).get(1L, TimeUnit.SECONDS);

        assertEquals(2, refreshCount.get());
        assertEquals(1, this.transport.notificationCount.get());
        assertEquals(1, this.transport.acknowledgementCount.get());
    }

    @Test
    void doesNotAcknowledgeUntilAnInFlightCacheAdmissionFinishes() throws Exception {
        final RedisEntityCoordinator coordinator = this.driver().getEntityCoordinator();
        final UUID id = UUID.randomUUID();
        final AtomicInteger refreshCount = new AtomicInteger();
        coordinator.bind(NAMESPACE.getKey(""), "receiver", _ -> refreshCount.incrementAndGet(), () -> {}, _ -> {});
        final RedisEntityCoordinator.Admission admission = coordinator.admit(NAMESPACE.getKey(""), id, "receiver");
        final RedisInvalidation invalidation = NAMESPACE.getInvalidation(id, "source");

        final CompletableFuture<Void> future = coordinator.notifyOwnersNow(invalidation, invalidation.getNotificationId(), true);

        assertFalse(future.isDone());
        assertEquals(0, refreshCount.get());
        admission.getReadyFuture().complete(null);
        future.get(1L, TimeUnit.SECONDS);
        assertEquals(1, refreshCount.get());
    }

    @Test
    void refreshFailureDoesNotCountAsSuccessfulSynchronization() throws Exception {
        final RedisEntityCoordinator coordinator = this.driver().getEntityCoordinator();
        final UUID id = UUID.randomUUID();
        coordinator.bind(NAMESPACE.getKey(""), "receiver", _ -> {
            throw new IllegalStateException("reload failed");
        }, () -> {}, _ -> {});
        coordinator.admit(NAMESPACE.getKey(""), id, "receiver").getReadyFuture().complete(null);
        final RedisInvalidation invalidation = NAMESPACE.getInvalidation(id, "source");

        final ExecutionException exception = assertThrows(ExecutionException.class, () -> coordinator.notifyOwnersNow(invalidation, invalidation.getNotificationId(), true).get(1L, TimeUnit.SECONDS));

        assertInstanceOf(SynchronizationException.class, exception.getCause());
    }

    @Test
    void unreachableOwnerTimesOutInsteadOfReportingSuccess() throws Exception {
        final RedisEntityCoordinator sender = this.driver().getEntityCoordinator();
        final RedisEntityCoordinator receiver = this.driver().getEntityCoordinator();
        final UUID id = UUID.randomUUID();
        receiver.bind(NAMESPACE.getKey(""), "receiver", _ -> {}, () -> {}, _ -> {});
        receiver.admit(NAMESPACE.getKey(""), id, "receiver").getReadyFuture().complete(null);
        this.transport.dropNotifications = true;
        final RedisInvalidation invalidation = NAMESPACE.getInvalidation(id, "source");

        final ExecutionException exception = assertThrows(ExecutionException.class, () -> sender.notifyOwnersNow(invalidation, invalidation.getNotificationId(), true).get(6L, TimeUnit.SECONDS));

        assertInstanceOf(SynchronizationException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("receiver"));
    }

    @Test
    void duplicateOutboxDeliveryRefreshesOnlyOnce() throws Exception {
        final RedisEntityCoordinator coordinator = this.driver().getEntityCoordinator();
        final UUID id = UUID.randomUUID();
        final AtomicInteger refreshCount = new AtomicInteger();
        coordinator.bind(NAMESPACE.getKey(""), "receiver", _ -> refreshCount.incrementAndGet(), () -> {}, _ -> {});
        coordinator.admit(NAMESPACE.getKey(""), id, "receiver").getReadyFuture().complete(null);
        final RedisInvalidation invalidation = NAMESPACE.getInvalidation(id, "source");

        coordinator.notifyOwnersNow(invalidation, invalidation.getNotificationId(), false).get(1L, TimeUnit.SECONDS);
        coordinator.notifyOwnersNow(invalidation, invalidation.getNotificationId(), true).get(1L, TimeUnit.SECONDS);

        assertEquals(1, refreshCount.get());
    }

    @Test
    void lateEvictionCannotRemoveANewerCacheRegistration() throws Exception {
        final RedisEntityCoordinator coordinator = this.driver().getEntityCoordinator();
        final UUID id = UUID.randomUUID();
        coordinator.bind(NAMESPACE.getKey(""), "receiver", _ -> {}, () -> {}, _ -> {});
        coordinator.admit(NAMESPACE.getKey(""), id, "receiver").getReadyFuture().complete(null);
        this.transport.blockRemoval = new CountDownLatch(1);
        coordinator.remove(NAMESPACE.getKey(""), id, "receiver");
        final RedisEntityCoordinator.Admission replacement = coordinator.admit(NAMESPACE.getKey(""), id, "receiver");
        replacement.getReadyFuture().complete(null);
        this.transport.blockRemoval.countDown();
        final RedisInvalidation invalidation = NAMESPACE.getInvalidation(id, "source");

        coordinator.notifyOwnersNow(invalidation, invalidation.getNotificationId(), true).get(1L, TimeUnit.SECONDS);

        assertEquals(replacement.getRegistration(), this.transport.hashMap.get(NAMESPACE.getKey("cache:owners:%s".formatted(id))).get("receiver"));
    }

    @Test
    void nonPersistentHolderCallbackWaitsUntilPinnedPeerHasRefreshed() throws Exception {
        final RedisDriver proxyDriver = this.driver();
        final RedisDriver paperDriver = this.driver();
        final PendingWriteStore pendingWriteStore = new PendingWriteStore();
        final HolderComponents<TestEntity> proxy = this.holder(proxyDriver, pendingWriteStore);
        final HolderComponents<TestEntity> paper = this.holder(paperDriver, pendingWriteStore);
        final UUID id = UUID.randomUUID();
        this.transport.hashMap.put(NAMESPACE.getKey(id), new ConcurrentHashMap<>(Map.of("name", "initial")));
        final TestEntity proxyEntity = new TestEntity(id);
        final TestEntity paperEntity = new TestEntity(id);
        proxy.getLocalEntityIndex().pin(proxyEntity, proxy.getLocalStorage());
        paper.getLocalEntityIndex().pin(paperEntity, paper.getLocalStorage());
        proxy.getChangeTracker().snapshot(proxyEntity);
        paper.getChangeTracker().snapshot(paperEntity);
        final CompletableFuture<Void> completed = new CompletableFuture<>();
        proxyEntity.setAdministrating(true);

        proxy.getHolderWriter().updateAndSynchronize(proxyEntity, () -> {
            if (paper.getLocalStorage().get(id).orElseThrow().isAdministrating()) {
                completed.complete(null);
            } else {
                completed.completeExceptionally(new AssertionError("Callback ran before the Paper cache refreshed"));
            }
        }, () -> completed.completeExceptionally(new AssertionError("Unexpected write failure")), completed::completeExceptionally);
        completed.get(2L, TimeUnit.SECONDS);

        assertTrue(paper.getLocalStorage().isPinned(id));
        assertTrue(pendingWriteStore.isEmpty());
    }

    @Test
    void ordinaryCommitCallbackDoesNotWaitForABlockedOwner() throws Exception {
        final RedisDriver proxyDriver = this.driver();
        final RedisDriver paperDriver = this.driver();
        final PendingWriteStore pendingWriteStore = new PendingWriteStore();
        final HolderComponents<TestEntity> proxy = this.holder(proxyDriver, pendingWriteStore);
        final HolderComponents<TestEntity> paper = this.holder(paperDriver, pendingWriteStore);
        final UUID id = UUID.randomUUID();
        this.transport.hashMap.put(NAMESPACE.getKey(id), new ConcurrentHashMap<>(Map.of("name", "initial")));
        final TestEntity entity = new TestEntity(id);
        proxy.getLocalEntityIndex().pin(entity, proxy.getLocalStorage());
        paper.getLocalEntityIndex().pin(new TestEntity(id), paper.getLocalStorage());
        proxy.getChangeTracker().snapshot(entity);
        final CountDownLatch releaseOwner = new CountDownLatch(1);
        paperDriver.getEntityCoordinator().bind(NAMESPACE.getKey(""), paper.getHolderWriter().getSourceId(), _ -> this.await(releaseOwner), () -> {}, _ -> {});
        final CompletableFuture<Thread> callbackFuture = new CompletableFuture<>();
        entity.setAdministrating(true);

        proxy.getHolderWriter().update(entity, () -> callbackFuture.complete(Thread.currentThread()));
        final Thread callbackThread = callbackFuture.get(1L, TimeUnit.SECONDS);
        assertTrue(callbackThread.isVirtual());
        assertEquals("database-callback", callbackThread.getName());
        releaseOwner.countDown();
    }

    @Test
    void configurableTimeoutUsesSynchronizationFailureAndLateAckCannotRunSuccess() throws Exception {
        final RedisDriver proxyDriver = this.driver();
        final RedisDriver paperDriver = this.driver();
        final HolderComponents<TestEntity> proxy = this.holder(proxyDriver, new PendingWriteStore());
        final HolderComponents<TestEntity> paper = this.holder(paperDriver, new PendingWriteStore());
        final UUID id = UUID.randomUUID();
        this.transport.hashMap.put(NAMESPACE.getKey(id), new ConcurrentHashMap<>(Map.of("name", "initial")));
        final TestEntity entity = new TestEntity(id);
        proxy.getLocalEntityIndex().pin(entity, proxy.getLocalStorage());
        paper.getLocalEntityIndex().pin(new TestEntity(id), paper.getLocalStorage());
        proxy.getChangeTracker().snapshot(entity);
        final CountDownLatch releaseOwner = new CountDownLatch(1);
        final CountDownLatch refreshed = new CountDownLatch(1);
        paperDriver.getEntityCoordinator().bind(NAMESPACE.getKey(""), paper.getHolderWriter().getSourceId(), _ -> {
            this.await(releaseOwner);
            refreshed.countDown();
        }, () -> {}, _ -> {});
        final AtomicInteger successCount = new AtomicInteger();
        final AtomicInteger writeFailureCount = new AtomicInteger();
        final CompletableFuture<Thread> failed = new CompletableFuture<>();
        entity.setAdministrating(true);

        proxy.getHolderWriter().updateAndSynchronize(entity, Duration.ofMillis(40), successCount::incrementAndGet, writeFailureCount::incrementAndGet, exception -> failed.complete(Thread.currentThread()));
        assertTrue(failed.get(1L, TimeUnit.SECONDS).isVirtual());
        releaseOwner.countDown();
        assertTrue(refreshed.await(1L, TimeUnit.SECONDS));
        assertEquals(0, successCount.get());
        assertEquals(0, writeFailureCount.get());
    }

    @Test
    void coalescedWaitersHaveIndependentDeadlinesAndDoNotDelayOrdinaryCommitCallbacks() throws Exception {
        final RedisDriver driver = this.driver();
        final RedisEntityCoordinator receiver = this.driver().getEntityCoordinator();
        final PendingWriteStore pendingStore = new PendingWriteStore();
        final HolderComponents<TestEntity> holder = this.holder(driver, pendingStore);
        final UUID id = UUID.randomUUID();
        this.transport.hashMap.put(NAMESPACE.getKey(id), new ConcurrentHashMap<>(Map.of("name", "initial")));
        final TestEntity entity = new TestEntity(id);
        holder.getLocalEntityIndex().pin(entity, holder.getLocalStorage());
        holder.getChangeTracker().snapshot(entity);
        final CountDownLatch release = new CountDownLatch(1);
        receiver.bind(NAMESPACE.getKey(""), "receiver", _ -> this.await(release), () -> {}, _ -> {});
        receiver.admit(NAMESPACE.getKey(""), id, "receiver").getReadyFuture().complete(null);
        final CompletableFuture<Void> shortFailure = new CompletableFuture<>();
        final CompletableFuture<Void> longSuccess = new CompletableFuture<>();
        final CompletableFuture<Void> ordinarySuccess = new CompletableFuture<>();
        final AtomicInteger shortSuccess = new AtomicInteger();
        entity.setName("first");
        holder.getHolderWriter().updateAndSynchronize(entity, Duration.ofMillis(40), shortSuccess::incrementAndGet, () -> shortFailure.completeExceptionally(new AssertionError("write failure")), exception -> shortFailure.complete(null));
        entity.setName("second");
        holder.getHolderWriter().updateAndSynchronize(entity, Duration.ofSeconds(1), () -> longSuccess.complete(null), () -> longSuccess.completeExceptionally(new AssertionError("write failure")), longSuccess::completeExceptionally);
        entity.setName("third");
        holder.getHolderWriter().update(entity, () -> ordinarySuccess.complete(null));
        final PendingWrite pendingWrite = pendingStore.drain().getFirst();
        pendingWrite.setSynchronizationTimeout(Duration.ofSeconds(1));
        pendingStore.complete(pendingWrite);

        for (final Runnable callback : pendingWrite.getCommitCallbackList()) {
            if (callback instanceof final WriteCommitCallback writeCommitCallback) {
                writeCommitCallback.run(pendingWrite);
            } else {
                callback.run();
            }
        }

        ordinarySuccess.get(1L, TimeUnit.SECONDS);
        shortFailure.get(1L, TimeUnit.SECONDS);
        assertFalse(longSuccess.isDone());
        release.countDown();
        longSuccess.get(1L, TimeUnit.SECONDS);
        assertEquals(0, shortSuccess.get());
        assertEquals(1, this.transport.notificationCount.get());
    }

    @Test
    void publicationFailureAfterSharedMutationIsNotAWriteFailure() throws Exception {
        final RedisDriver redisDriver = this.driver();
        final RedisEntityCoordinator receiver = this.driver().getEntityCoordinator();
        final HolderComponents<TestEntity> holder = this.holder(redisDriver, new PendingWriteStore());
        final UUID id = UUID.randomUUID();
        this.transport.hashMap.put(NAMESPACE.getKey(id), new ConcurrentHashMap<>(Map.of("name", "initial")));
        final TestEntity entity = new TestEntity(id);
        holder.getLocalEntityIndex().pin(entity, holder.getLocalStorage());
        holder.getChangeTracker().snapshot(entity);
        receiver.bind(NAMESPACE.getKey(""), "receiver", _ -> {}, () -> {}, _ -> {});
        receiver.admit(NAMESPACE.getKey(""), id, "receiver").getReadyFuture().complete(null);
        doThrow(new ConnectionException("publish failed")).when(redisDriver).publish(anyString(), anyString());
        final AtomicInteger writeFailureCount = new AtomicInteger();
        final CompletableFuture<Void> ordinaryCommitted = new CompletableFuture<>();
        entity.setAdministrating(true);
        holder.getHolderWriter().update(entity, () -> ordinaryCommitted.complete(null), writeFailureCount::incrementAndGet);
        ordinaryCommitted.get(1L, TimeUnit.SECONDS);
        assertEquals("1true", this.transport.hashMap.get(NAMESPACE.getKey("cache:shared:%s".formatted(id))).get("administrating"));
        final CompletableFuture<SynchronizationException> syncFailed = new CompletableFuture<>();
        entity.setAdministrating(false);

        holder.getHolderWriter().updateAndSynchronize(entity, () -> syncFailed.completeExceptionally(new AssertionError("Unexpected success")), writeFailureCount::incrementAndGet, syncFailed::complete);
        assertInstanceOf(SynchronizationException.class, syncFailed.get(1L, TimeUnit.SECONDS));
        assertEquals(0, writeFailureCount.get());
    }

    @Test
    void receiverRepairsAfterAnErrorEvenWhenItsApplicationReporterThrows() throws Exception {
        final RedisEntityCoordinator sender = this.driver().getEntityCoordinator();
        final RedisEntityCoordinator receiver = this.driver().getEntityCoordinator();
        final UUID id = UUID.randomUUID();
        final AtomicInteger attemptCount = new AtomicInteger();
        final CountDownLatch repaired = new CountDownLatch(1);
        receiver.bind(NAMESPACE.getKey(""), "receiver", _ -> {
            if (attemptCount.incrementAndGet() == 1) {
                throw new AssertionError("temporary refresh failure");
            }
            repaired.countDown();
        }, () -> {}, _ -> {
            throw new AssertionError("application reporter failed");
        });
        receiver.admit(NAMESPACE.getKey(""), id, "receiver").getReadyFuture().complete(null);
        final RedisInvalidation invalidation = NAMESPACE.getInvalidation(id, "source");

        assertThrows(ExecutionException.class, () -> sender.notifyOwnersNow(invalidation, invalidation.getNotificationId(), true).get(1L, TimeUnit.SECONDS));
        assertTrue(repaired.await(2L, TimeUnit.SECONDS));
        assertEquals(2, attemptCount.get());
    }

    @Test
    void firstCacheAdmissionRevalidatesAnEntityReadBeforeTheOwnershipMarker() {
        final HolderComponents<TestEntity> holder = this.holder(this.driver(), new PendingWriteStore());
        final UUID id = UUID.randomUUID();
        this.transport.hashMap.put(NAMESPACE.getKey(id), new ConcurrentHashMap<>(Map.of("name", "after")));
        final TestEntity staleEntity = new TestEntity(id);
        staleEntity.setName("before");

        holder.getLocalEntityIndex().pin(staleEntity, holder.getLocalStorage());

        assertEquals("after", holder.getLocalStorage().get(id).orElseThrow().getName());
        assertTrue(holder.getLocalStorage().isPinned(id));
    }

    @Test
    void admissionErrorCompletesWaitingRefreshesAndAllowsAnotherAdmission() throws Exception {
        final RedisDriver redisDriver = this.driver();
        final RedisEntityCoordinator coordinator = redisDriver.getEntityCoordinator();
        final HolderComponents<TestEntity> holder = this.holder(redisDriver, new PendingWriteStore());
        final UUID id = UUID.randomUUID();
        final AssertionError original = new AssertionError("converter failed during admission");
        final AtomicReference<RedisEntityCoordinator.Admission> admissionReference = new AtomicReference<>();
        this.transport.hashMap.put(NAMESPACE.getKey(id), new ConcurrentHashMap<>(Map.of("name", "current")));
        this.transport.readHook = key -> {
            if (key.equals(NAMESPACE.getKey(id))) {
                admissionReference.set(coordinator.admit(NAMESPACE.getKey(""), id, holder.getHolderWriter().getSourceId()));
                throw original;
            }
        };

        assertSame(original, assertThrows(AssertionError.class, () -> holder.getLocalEntityIndex().pin(new TestEntity(id), holder.getLocalStorage())));
        assertSame(original, assertThrows(ExecutionException.class, () -> admissionReference.get().getReadyFuture().get(1L, TimeUnit.SECONDS)).getCause());
        assertTrue(holder.getLocalStorage().get(id).isEmpty());

        this.transport.readHook = _ -> {};
        holder.getLocalEntityIndex().pin(new TestEntity(id), holder.getLocalStorage());

        assertEquals("current", holder.getLocalStorage().get(id).orElseThrow().getName());
        assertTrue(holder.getLocalStorage().isPinned(id));
        assertTrue(coordinator.admit(NAMESPACE.getKey(""), id, holder.getHolderWriter().getSourceId()).getReadyFuture().isDone());
    }

    @Test
    void repairLogsFailureAndRecoveryWithANoopApplicationHandler() throws Exception {
        final RedisEntityCoordinator sender = this.driver().getEntityCoordinator();
        final RedisEntityCoordinator receiver = this.driver().getEntityCoordinator();
        final UUID id = UUID.randomUUID();
        final AtomicInteger attemptCount = new AtomicInteger();
        final AssertionError original = new AssertionError("refresh failed before recovery");
        receiver.bind(NAMESPACE.getKey(""), "receiver", _ -> {
            if (attemptCount.incrementAndGet() == 1) {
                throw original;
            }
        }, () -> {}, _ -> {});
        receiver.admit(NAMESPACE.getKey(""), id, "receiver").getReadyFuture().complete(null);
        final RedisInvalidation invalidation = NAMESPACE.getInvalidation(id, "source");

        try (final RepairLogCapture repairLogCapture = new RepairLogCapture()) {
            assertThrows(ExecutionException.class, () -> sender.notifyOwnersNow(invalidation, invalidation.getNotificationId(), true).get(1L, TimeUnit.SECONDS));
            final LogRecord failureRecord = repairLogCapture.await(id, Level.SEVERE, Duration.ofSeconds(2));
            assertSame(original, failureRecord.getThrown());
            assertTrue(failureRecord.getMessage().contains("namespace=" + NAMESPACE.getKey("")));
            assertTrue(failureRecord.getMessage().contains("holder=receiver"));
            assertTrue(failureRecord.getMessage().contains("session="));

            final LogRecord recoveryRecord = repairLogCapture.await(id, Level.INFO, Duration.ofSeconds(2));
            assertTrue(recoveryRecord.getMessage().contains("elapsedMs="));
            assertTrue(recoveryRecord.getMessage().contains("retryCount=1"));
            assertEquals(0L, repairLogCapture.count(id, Level.WARNING));
        }
    }

    @Test
    void persistentRepairFailureProducesRateLimitedWarningsAndThenRecovers() throws Exception {
        final RedisEntityCoordinator sender = this.driver().getEntityCoordinator();
        final RedisEntityCoordinator receiver = this.driver().getEntityCoordinator();
        final UUID id = UUID.randomUUID();
        final AtomicBoolean failing = new AtomicBoolean(true);
        receiver.bind(NAMESPACE.getKey(""), "receiver", _ -> {
            if (failing.get()) {
                throw new IllegalStateException("refresh continues failing");
            }
        }, () -> {}, _ -> {
            throw new AssertionError("application handler failed too");
        });
        receiver.admit(NAMESPACE.getKey(""), id, "receiver").getReadyFuture().complete(null);
        final RedisInvalidation invalidation = NAMESPACE.getInvalidation(id, "source");

        try (final RepairLogCapture repairLogCapture = new RepairLogCapture()) {
            assertThrows(ExecutionException.class, () -> sender.notifyOwnersNow(invalidation, invalidation.getNotificationId(), true).get(1L, TimeUnit.SECONDS));
            repairLogCapture.await(id, Level.SEVERE, Duration.ofSeconds(2));
            Thread.sleep(2_500L);
            assertEquals(0L, repairLogCapture.count(id, Level.WARNING));

            final LogRecord warningRecord = repairLogCapture.await(id, Level.WARNING, Duration.ofSeconds(38));
            assertTrue(warningRecord.getMessage().contains("cached reads may be stale"));
            assertInstanceOf(IllegalStateException.class, warningRecord.getThrown());
            assertEquals(1L, repairLogCapture.count(id, Level.SEVERE));
            assertEquals(1L, repairLogCapture.count(id, Level.WARNING));

            failing.set(false);
            repairLogCapture.await(id, Level.INFO, Duration.ofSeconds(8));
            assertEquals(1L, repairLogCapture.count(id, Level.WARNING));
        }
    }

    @Test
    void generationChangeDuringCacheFillForcesRevalidationBeforeAdmission() throws Exception {
        final RedisEntityCoordinator sender = this.driver().getEntityCoordinator();
        final HolderComponents<TestEntity> holder = this.holder(this.driver(), new PendingWriteStore());
        final UUID id = UUID.randomUUID();
        final RedisInvalidation invalidation = NAMESPACE.getInvalidation(id, "source");
        final AtomicReference<CompletableFuture<Void>> notificationFuture = new AtomicReference<>();
        this.transport.hashMap.put(NAMESPACE.getKey(id), new ConcurrentHashMap<>(Map.of("name", "before")));
        this.transport.readHook = key -> {
            if (key.equals(NAMESPACE.getKey(id))) {
                this.transport.readHook = _ -> {};
                notificationFuture.set(sender.notifyOwnersNow(invalidation, invalidation.getNotificationId(), true));
                this.transport.hashMap.put(NAMESPACE.getKey(id), new ConcurrentHashMap<>(Map.of("name", "after")));
            }
        };

        final TestEntity entity = new TestEntity(id);
        holder.getLocalEntityIndex().pin(entity, holder.getLocalStorage());
        notificationFuture.get().get(1L, TimeUnit.SECONDS);

        assertEquals("after", entity.getName());
        assertEquals("after", holder.getLocalStorage().get(id).orElseThrow().getName());
    }

    @Test
    void expirationRemovesOwnershipAndUnpinRetainsItUntilTheEntryExpires() throws Exception {
        final RedisDriver redisDriver = this.driver();
        final HolderComponents<TestEntity> holder = this.holder(redisDriver, new PendingWriteStore());
        final UUID id = UUID.randomUUID();
        this.transport.hashMap.put(NAMESPACE.getKey(id), new ConcurrentHashMap<>(Map.of("name", "initial")));
        holder.getLocalEntityIndex().pin(new TestEntity(id), holder.getLocalStorage());
        holder.getLocalStorage().unpin(id);
        final String ownerKey = NAMESPACE.getKey("cache:owners:%s".formatted(id));
        assertFalse(this.transport.hashMap.get(ownerKey).isEmpty());
        holder.getLocalStorage().put(id, new TestEntity(id), Duration.ZERO);
        assertTrue(holder.getLocalStorage().get(id).isEmpty());
        final long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (!this.transport.hashMap.get(ownerKey).isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
        assertTrue(this.transport.hashMap.get(ownerKey).isEmpty());
    }

    @Test
    void expirationListenerCannotUnregisterAConcurrentRepin() throws Exception {
        final RedisDriver redisDriver = this.driver();
        final HolderComponents<TestEntity> holder = this.holder(redisDriver, new PendingWriteStore());
        final UUID id = UUID.randomUUID();
        this.transport.hashMap.put(NAMESPACE.getKey(id), new ConcurrentHashMap<>(Map.of("name", "initial")));
        holder.getLocalEntityIndex().pin(new TestEntity(id), holder.getLocalStorage());
        holder.getLocalStorage().unpin(id);
        holder.getLocalStorage().put(id, new TestEntity(id), Duration.ZERO);
        final CountDownLatch removalStarted = new CountDownLatch(1);
        final CountDownLatch releaseRemoval = new CountDownLatch(1);
        holder.getLocalStorage().setRemovalListener(removedId -> {
            removalStarted.countDown();
            this.await(releaseRemoval);
            redisDriver.getEntityCoordinator().remove(NAMESPACE.getKey(""), removedId, holder.getHolderWriter().getSourceId());
        });
        final Thread expirationThread = Thread.ofVirtual().start(() -> holder.getLocalStorage().get(id));
        assertTrue(removalStarted.await(1L, TimeUnit.SECONDS));
        final CompletableFuture<Void> repinnedFuture = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try {
                holder.getLocalEntityIndex().pin(new TestEntity(id), holder.getLocalStorage());
                repinnedFuture.complete(null);
            } catch (final RuntimeException exception) {
                repinnedFuture.completeExceptionally(exception);
            }
        });
        assertFalse(repinnedFuture.isDone());
        releaseRemoval.countDown();
        expirationThread.join(1_000L);
        repinnedFuture.get(1L, TimeUnit.SECONDS);

        assertTrue(holder.getLocalStorage().isPinned(id));
        assertTrue(this.transport.hashMap.get(NAMESPACE.getKey("cache:owners:%s".formatted(id))).containsKey(holder.getHolderWriter().getSourceId()));
    }

    @Test
    void coalescedPersistentCallbacksShareOneWaitAndPreserveCommittedStateOnSyncFailure() throws Exception {
        final RedisDriver redisDriver = this.driver();
        final PendingWriteStore pendingWriteStore = new PendingWriteStore();
        final HolderComponents<TestEntity> holder = this.holder(redisDriver, pendingWriteStore);
        final RedisEntityCoordinator peer = this.driver().getEntityCoordinator();
        final UUID id = UUID.randomUUID();
        peer.bind(NAMESPACE.getKey(""), "bad-peer", _ -> {
            throw new IllegalStateException("reload failed");
        }, () -> {}, _ -> {});
        peer.admit(NAMESPACE.getKey(""), id, "bad-peer").getReadyFuture().complete(null);
        this.transport.hashMap.put(NAMESPACE.getKey(id), new ConcurrentHashMap<>(Map.of("name", "initial")));
        final TestEntity entity = new TestEntity(id);
        holder.getLocalEntityIndex().pin(entity, holder.getLocalStorage());
        holder.getChangeTracker().snapshot(entity);
        final AtomicInteger commitCount = new AtomicInteger();
        final CountDownLatch failureLatch = new CountDownLatch(2);
        final List<Integer> failureOrderList = new CopyOnWriteArrayList<>();
        entity.setName("first");
        holder.getHolderWriter().updateAndSynchronize(entity, commitCount::incrementAndGet, () -> {
            throw new AssertionError("Unexpected write failure");
        }, exception -> {
            failureOrderList.add(1);
            failureLatch.countDown();
        });
        entity.setName("second");
        holder.getHolderWriter().updateAndSynchronize(entity, commitCount::incrementAndGet, () -> {
            throw new AssertionError("Unexpected write failure");
        }, exception -> {
            failureOrderList.add(2);
            failureLatch.countDown();
        });
        final PendingWrite pendingWrite = pendingWriteStore.drain().getFirst();
        pendingWriteStore.complete(pendingWrite);

        for (final Runnable callback : pendingWrite.getCommitCallbackList()) {
            if (callback instanceof final WriteCommitCallback writeCommitCallback) {
                writeCommitCallback.run(pendingWrite);
            } else {
                callback.run();
            }
        }

        assertTrue(failureLatch.await(2L, TimeUnit.SECONDS));
        assertEquals(0, commitCount.get());
        assertEquals(1, this.transport.notificationCount.get());
        assertSame(entity, holder.getLocalStorage().get(id).orElseThrow());
        assertTrue(holder.getLocalStorage().isPinned(id));
        assertTrue(holder.getChangeTracker().isTracked(entity));
        assertEquals("second", entity.getName());
        assertEquals(Set.of(1, 2), Set.copyOf(failureOrderList));
    }

    private RedisDriver driver() {
        final RedisDriver redisDriver = this.transport.driver();
        this.coordinatorList.add(redisDriver.getEntityCoordinator());
        return redisDriver;
    }

    @SuppressWarnings("unchecked")
    private HolderComponents<TestEntity> holder(final RedisDriver redisDriver, final PendingWriteStore pendingWriteStore) {
        final AbstractEntityRepository<TestEntity> repository = mock(AbstractEntityRepository.class);
        final CoreDatabase coreDatabase = mock(CoreDatabase.class);
        final BatchQueue batchQueue = mock(BatchQueue.class);
        final TableSchema<TestEntity> tableSchema = new TableSchema<>(TestEntity.class, "owner_test", TenantScope.NONE, List.of(NAME, ADMINISTRATING));
        when(repository.getProperties()).thenReturn(List.of(NAME, ADMINISTRATING));
        when(repository.getEntityType()).thenReturn(TestEntity.class);
        when(repository.getTable()).thenReturn("owner_test");
        when(repository.getTableSchema()).thenReturn(tableSchema);
        when(repository.getTenantId()).thenReturn(Optional.empty());
        when(repository.getEntityIndexes()).thenReturn(List.of());
        when(repository.getCoreDatabase()).thenReturn(coreDatabase);
        when(coreDatabase.getRedisDriver()).thenReturn(Optional.of(redisDriver));
        when(coreDatabase.getBatchQueue()).thenReturn(batchQueue);
        when(coreDatabase.getEntityRevisionStore()).thenReturn(new EntityRevisionStore());
        when(batchQueue.getPendingWriteStore()).thenReturn(pendingWriteStore);
        final RepositoryWriter<TestEntity> repositoryWriter = new RepositoryWriter<>(pendingWriteStore, tableSchema, null, NAMESPACE, "proxy", new EntityRevisionStore());
        doAnswer(invocation -> {
            repositoryWriter.update(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4), invocation.getArgument(5));
            return null;
        }).when(repository).update(any(), anyCollection(), anyList(), anyList(), eq(true), anyString());
        return HolderComponents.create(repository, InstanceMode.MULTI_INSTANCE, RedisMode.ENABLED, Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(5));
    }

    private void await(final CountDownLatch latch) {
        try {
            assertTrue(latch.await(2L, TimeUnit.SECONDS));
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @Getter
    @Setter
    private static final class TestEntity implements Entity {

        @EqualsAndHashCode.Include
        private final UUID id;

        private String name;

        private boolean administrating;
    }

    private static final class RepairLogCapture extends Handler implements AutoCloseable {

        private final CopyOnWriteArrayList<LogRecord> logRecordList = new CopyOnWriteArrayList<>();

        private final Logger logger = Logger.getLogger(RedisEntityCoordinator.class.getName());

        private RepairLogCapture() {
            this.logger.addHandler(this);
        }

        @Override
        public void publish(final LogRecord logRecord) {
            this.logRecordList.add(logRecord);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            this.logger.removeHandler(this);
        }

        private long count(final UUID id, final Level level) {
            return this.logRecordList.stream()
                    .filter(logRecord -> logRecord.getLevel().equals(level) && logRecord.getMessage().contains(id.toString()))
                    .count();
        }

        private LogRecord await(final UUID id, final Level level, final Duration timeout) throws InterruptedException {
            final long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                final LogRecord logRecord = this.logRecordList.stream()
                        .filter(value -> value.getLevel().equals(level) && value.getMessage().contains(id.toString()))
                        .findFirst().orElse(null);
                if (logRecord != null) {
                    return logRecord;
                }
                Thread.sleep(10L);
            }
            throw new AssertionError("Missing repair log: entity=%s level=%s".formatted(id, level));
        }
    }

    /**
     * In-memory Redis transport; protocol tests exercise real coordinators and holder code.
     */
    private static final class Transport {

        private final ConcurrentHashMap<String, Map<String, String>> hashMap = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> stringMap = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, List<Consumer<String>>> subscriberMap = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Set<String>> membershipMap = new ConcurrentHashMap<>();

        private final AtomicInteger notificationCount = new AtomicInteger();
        private final AtomicInteger acknowledgementCount = new AtomicInteger();

        private volatile boolean dropNotifications;

        private Consumer<String> readHook = _ -> {};

        private CountDownLatch blockRemoval;

        @SuppressWarnings("unchecked")
        private RedisDriver driver() {
            final RedisDriver redisDriver = mock(RedisDriver.class);
            final RedisCommands<String, String> redisCommands = mock(RedisCommands.class, this.answer());
            final RedisAsyncCommands<String, String> redisAsyncCommands = mock(RedisAsyncCommands.class, this.answer());
            when(redisDriver.getCommands()).thenReturn(redisCommands);
            when(redisDriver.getAsyncCommands()).thenReturn(redisAsyncCommands);
            when(redisDriver.isConnected()).thenReturn(true);
            final RedisEntityCoordinator coordinator = new RedisEntityCoordinator(redisDriver);
            when(redisDriver.getEntityCoordinator()).thenReturn(coordinator);
            doAnswer(invocation -> {
                this.subscriberMap.computeIfAbsent(invocation.getArgument(0), _ -> new CopyOnWriteArrayList<>()).add(invocation.getArgument(1));
                return null;
            }).when(redisDriver).subscribe(anyString(), any());
            doAnswer(invocation -> {
                this.publish(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(redisDriver).publish(anyString(), anyString());
            return redisDriver;
        }

        private Answer<Object> answer() {
            return invocation -> {
                final Object[] argumentArray = invocation.getRawArguments();
                return switch (invocation.getMethod().getName()) {
                    case "scriptLoad" -> argumentArray[0];
                    case "evalsha" -> this.script((String) argumentArray[0], (String[]) argumentArray[2], (String[]) argumentArray[3]);
                    case "get" -> this.stringMap.get((String) argumentArray[0]);
                    case "hgetall" -> {
                        final Map<String, String> valueMap = new HashMap<>(this.hashMap.getOrDefault((String) argumentArray[0], Map.of()));
                        this.readHook.accept((String) argumentArray[0]);
                        yield valueMap;
                    }
                    case "psetex" -> {
                        this.stringMap.put((String) argumentArray[0], (String) argumentArray[2]);
                        yield "OK";
                    }
                    case "publish" -> {
                        this.publish((String) argumentArray[0], (String) argumentArray[1]);
                        yield null;
                    }
                    case "del" -> {
                        for (final String key : (String[]) argumentArray[0]) {
                            this.stringMap.remove(key);
                            this.hashMap.remove(key);
                        }
                        yield 1L;
                    }
                    case "zrangebyscore" -> List.of();
                    default -> null;
                };
            };
        }

        private Object script(final String source, final String[] keyArray, final String[] valueArray) throws InterruptedException {
            if (!source.contains("redis.call")) {
                throw new RedisNoScriptException("NOSCRIPT");
            }

            if (source.contains("redis.call('TIME')")) {
                this.stringMap.put(keyArray[0], "1");
                return System.currentTimeMillis();
            }

            if (source.contains("local generation")) {
                synchronized (this) {
                    if (!this.stringMap.containsKey(keyArray[6])) {
                        if (valueArray[0].equals("1")) {
                            this.hashMap.remove(keyArray[0]);
                        }
                        if (valueArray[1].equals("1")) {
                            this.hashMap.remove(keyArray[2]);
                        }
                        for (int index = 9; index < valueArray.length; index += 2) {
                            final Map<String, String> sharedMap = this.hashMap.computeIfAbsent(keyArray[2], _ -> new ConcurrentHashMap<>());
                            if (valueArray[8].equals("1")) {
                                sharedMap.putIfAbsent(valueArray[index], valueArray[index + 1]);
                            } else {
                                sharedMap.put(valueArray[index], valueArray[index + 1]);
                            }
                        }
                        final long nextGeneration = Long.parseLong(this.stringMap.getOrDefault(keyArray[4], "0")) + 1L;
                        this.stringMap.put(keyArray[4], Long.toString(nextGeneration));
                        this.stringMap.put(keyArray[6], "1");
                    }
                    final long generation = Long.parseLong(this.stringMap.getOrDefault(keyArray[4], "0"));
                    final Map<String, List<String>> groupMap = new HashMap<>();
                    final List<String> ownerList = new ArrayList<>();
                    this.hashMap.getOrDefault(keyArray[3], Map.of()).forEach((holder, registration) -> {
                        final String session = registration.substring(0, 36);
                        if (!holder.equals(valueArray[5]) && this.stringMap.containsKey("%slease:%s".formatted(valueArray[2], session))) {
                            ownerList.add(holder);
                            groupMap.computeIfAbsent(session, _ -> new ArrayList<>()).add(holder);
                        }
                    });
                    final List<String> receipts = new ArrayList<>();
                    receipts.add(Long.toString(generation));
                    groupMap.forEach((session, holderList) -> holderList.forEach(holder -> receipts.add("%s:%s".formatted(holder, session))));
                    return receipts;
                }
            }

            if (source.contains("'SADD'")) {
                this.hashMap.computeIfAbsent(keyArray[0], _ -> new ConcurrentHashMap<>()).put(valueArray[0], valueArray[1]);
                this.membershipMap.computeIfAbsent(keyArray[1], _ -> ConcurrentHashMap.newKeySet()).add("%s|%s|%s".formatted(keyArray[0], valueArray[0], valueArray[1]));
                return this.stringMap.getOrDefault(keyArray[2], "0");
            }

            if (source.contains("'SREM'")) {
                if (this.blockRemoval != null) {
                    this.blockRemoval.await(2L, TimeUnit.SECONDS);
                }
                this.hashMap.getOrDefault(keyArray[0], new ConcurrentHashMap<>()).remove(valueArray[0], valueArray[1]);
                return 1L;
            }

            if (source.contains("local setCount")) {
                final Map<String, String> valueMap = this.hashMap.computeIfAbsent(keyArray[0], _ -> new ConcurrentHashMap<>());
                final int count = Integer.parseInt(valueArray[0]);
                for (int index = 0; index < count; index++) {
                    valueMap.put(valueArray[1 + index * 2], valueArray[2 + index * 2]);
                }
                return 1L;
            }

            throw new AssertionError("Unexpected Redis script");
        }

        private void publish(final String channel, final String message) {
            if (channel.contains(":notify:")) {
                this.notificationCount.incrementAndGet();
                if (this.dropNotifications) {
                    return;
                }
            } else if (channel.contains(":ack:")) {
                this.acknowledgementCount.incrementAndGet();
            }
            this.subscriberMap.getOrDefault(channel, List.of()).forEach(consumer -> consumer.accept(message));
        }
    }
}