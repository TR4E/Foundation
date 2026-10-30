package me.trae.foundation.database.core.holder;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.holder.InstanceMode;
import me.trae.foundation.database.api.holder.RedisMode;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.tenant.TenantScope;
import me.trae.foundation.database.core.CoreDatabase;
import me.trae.foundation.database.core.batch.BatchQueue;
import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.storage.revision.EntityRevisionStore;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class HolderCallbackTest {

    private static final EntityProperty<TestEntity, String> NAME = EntityProperty.register(TestEntity.class, "name", TestEntity::getName, TestEntity::setName, String.class, true);
    private static final EntityProperty<TestEntity, String> SESSION = EntityProperty.register(TestEntity.class, "session", TestEntity::getSession, TestEntity::setSession, String.class, false);

    private final AtomicReference<SQLException> sqlExceptionReference = new AtomicReference<>();

    private final AtomicInteger sqlStatementCount = new AtomicInteger();

    private final List<DatabaseException> failureList = new ArrayList<>();

    private final Connection connection = spy(new MockConnection(this::execute));

    private final PostgresDriver postgresDriver = mock(PostgresDriver.class);

    private final BatchQueue batchQueue = new BatchQueue(this.postgresDriver, null, Duration.ofHours(1), 100);

    private final CoreDatabase coreDatabase = mock(CoreDatabase.class);

    private final EntityRevisionStore entityRevisionStore = new EntityRevisionStore();

    private final TestHolder testHolder;

    HolderCallbackTest() {
        when(this.postgresDriver.getDslContext()).thenReturn(DSL.using(this.connection, SQLDialect.POSTGRES));
        when(this.coreDatabase.getPostgresDriver()).thenReturn(this.postgresDriver);
        when(this.coreDatabase.getRedisDriver()).thenReturn(Optional.empty());
        when(this.coreDatabase.getBatchQueue()).thenReturn(this.batchQueue);
        when(this.coreDatabase.getEntityRevisionStore()).thenReturn(this.entityRevisionStore);
        when(this.coreDatabase.resolveTenantId(TenantScope.NONE)).thenReturn(Optional.empty());
        this.batchQueue.setFailureHandler(this.failureList::add);
        this.testHolder = new TestHolder(new TestRepository(this.coreDatabase));
    }

    @Test
    void unchangedUpdateCommitsImmediatelyWithoutSqlOrQueuedWrites() {
        final TestEntity testEntity = this.cachedEntity();
        final AtomicInteger commitCount = new AtomicInteger();
        final AtomicInteger failureCount = new AtomicInteger();

        this.testHolder.update(testEntity, commitCount::incrementAndGet, failureCount::incrementAndGet);

        assertEquals(1, commitCount.get());
        assertEquals(0, failureCount.get());
        assertEquals(0, this.sqlStatementCount.get());
        assertTrue(this.batchQueue.getPendingWriteStore().isEmpty());
    }

    @Test
    void localOnlyChangeCommitsImmediatelyAndIsNotWrittenOnFlush() {
        final TestEntity testEntity = this.cachedEntity();
        testEntity.setSession("new-session");
        final AtomicInteger commitCount = new AtomicInteger();

        this.testHolder.update(testEntity, commitCount::incrementAndGet);
        this.batchQueue.flush();

        assertEquals(1, commitCount.get());
        assertEquals(0, this.sqlStatementCount.get());
        assertTrue(this.batchQueue.getPendingWriteStore().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "update", "delete"})
    void callbackWaitsForDatabaseCommit(final String operation) {
        final TestEntity testEntity = operation.equals("save") ? new TestEntity(UUID.randomUUID()) : this.cachedEntity();
        testEntity.setName("updated");
        final AtomicInteger commitCount = new AtomicInteger();
        final AtomicInteger failureCount = new AtomicInteger();

        this.write(operation, testEntity, () -> {
            assertDoesNotThrow(() -> verify(this.connection).commit());
            commitCount.incrementAndGet();
        }, failureCount::incrementAndGet);

        assertEquals(0, commitCount.get());
        assertEquals(0, failureCount.get());
        assertFalse(this.batchQueue.getPendingWriteStore().isEmpty());

        this.batchQueue.flush();

        assertEquals(1, commitCount.get());
        assertEquals(0, failureCount.get());
        assertTrue(this.failureList.isEmpty());
        assertTrue(this.batchQueue.getPendingWriteStore().isEmpty());

        this.batchQueue.flush();

        assertEquals(1, commitCount.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "update", "delete"})
    void permanentDatabaseFailureRunsOnlyFailureCallback(final String operation) {
        final TestEntity testEntity = operation.equals("save") ? new TestEntity(UUID.randomUUID()) : this.cachedEntity();
        testEntity.setName("rejected");
        final AtomicInteger commitCount = new AtomicInteger();
        final AtomicInteger failureCount = new AtomicInteger();
        this.sqlExceptionReference.set(new SQLException("constraint failed", "23514"));

        this.write(operation, testEntity, commitCount::incrementAndGet, failureCount::incrementAndGet);
        this.batchQueue.flush();
        this.batchQueue.flush();

        assertEquals(0, commitCount.get());
        assertEquals(1, failureCount.get());
        assertFalse(this.failureList.isEmpty());
        assertTrue(this.batchQueue.getPendingWriteStore().isEmpty());
    }

    @Test
    void transientFailureKeepsCallbacksPendingUntilSuccessfulRetry() {
        final TestEntity testEntity = this.cachedEntity();
        testEntity.setName("updated");
        final AtomicInteger commitCount = new AtomicInteger();
        final AtomicInteger failureCount = new AtomicInteger();
        this.sqlExceptionReference.set(new SQLException("retry transaction", "40001"));
        this.testHolder.update(testEntity, commitCount::incrementAndGet, failureCount::incrementAndGet);

        this.batchQueue.flush();

        assertEquals(0, commitCount.get());
        assertEquals(0, failureCount.get());
        assertFalse(this.batchQueue.getPendingWriteStore().isEmpty());

        this.sqlExceptionReference.set(null);
        this.batchQueue.flush();

        assertEquals(1, commitCount.get());
        assertEquals(0, failureCount.get());
        assertTrue(this.batchQueue.getPendingWriteStore().isEmpty());
    }

    @Test
    void coalescedUpdatesRunEachCallbackExactlyOnce() {
        final TestEntity testEntity = this.cachedEntity();
        final AtomicInteger firstCommitCount = new AtomicInteger();
        final AtomicInteger secondCommitCount = new AtomicInteger();
        testEntity.setName("first");
        this.testHolder.update(testEntity, firstCommitCount::incrementAndGet);
        testEntity.setName("second");
        this.testHolder.update(testEntity, secondCommitCount::incrementAndGet);

        assertEquals(0, firstCommitCount.get());
        assertEquals(0, secondCommitCount.get());

        this.batchQueue.flush();

        assertEquals(1, firstCommitCount.get());
        assertEquals(1, secondCommitCount.get());
        assertEquals(1, this.sqlStatementCount.get());
    }

    @Test
    void synchronousSaveFailureRunsFailureCallbackAndKeepsExceptionBehavior() {
        final TestEntity testEntity = this.cachedEntity();
        final AtomicInteger commitCount = new AtomicInteger();
        final AtomicInteger failureCount = new AtomicInteger();

        assertThrows(DatabaseException.class, () -> this.testHolder.save(testEntity, commitCount::incrementAndGet, failureCount::incrementAndGet));

        assertEquals(0, commitCount.get());
        assertEquals(1, failureCount.get());
        assertEquals(0, this.sqlStatementCount.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void commitCallbackFailureDoesNotRunWriteFailureCallback(final boolean changed) {
        final TestEntity testEntity = this.cachedEntity();
        if (changed) {
            testEntity.setName("updated");
        }

        final AtomicInteger failureCount = new AtomicInteger();

        this.testHolder.update(testEntity, () -> {
            throw new IllegalStateException("callback failed");
        }, failureCount::incrementAndGet);
        this.batchQueue.flush();

        assertEquals(0, failureCount.get());
        assertEquals(1, this.failureList.size());
        assertTrue(this.batchQueue.getPendingWriteStore().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "delete"})
    void commitOnlyOverloadsWaitForTheWrite(final String operation) {
        final TestEntity testEntity = operation.equals("save") ? new TestEntity(UUID.randomUUID()) : this.cachedEntity();
        final AtomicInteger commitCount = new AtomicInteger();

        if (operation.equals("save")) {
            this.testHolder.save(testEntity, commitCount::incrementAndGet);
        } else {
            this.testHolder.delete(testEntity, commitCount::incrementAndGet);
        }

        assertEquals(0, commitCount.get());
        this.batchQueue.flush();

        assertEquals(1, commitCount.get());
    }

    @Test
    void committedRevisionIsVisibleInsideTheCallback() {
        final TestHolder revisionTestHolder = new TestHolder(new TestRepository(this.coreDatabase, true));
        final TestEntity testEntity = new TestEntity(UUID.randomUUID());
        testEntity.setName("initial");
        revisionTestHolder.cache(testEntity);
        testEntity.setName("updated");
        final AtomicInteger commitCount = new AtomicInteger();

        revisionTestHolder.update(testEntity, () -> {
            assertEquals(1L, this.entityRevisionStore.getState(testEntity).getRevision());
            commitCount.incrementAndGet();
        });
        this.batchQueue.flush();

        assertEquals(1, commitCount.get());
        assertTrue(this.failureList.isEmpty());
    }

    @Test
    void immediateAndQueuedCallbacksUseTheirRespectiveCompletionThreads() throws InterruptedException {
        final TestEntity testEntity = this.cachedEntity();
        final AtomicReference<Thread> callbackThreadReference = new AtomicReference<>();
        this.testHolder.update(testEntity, () -> callbackThreadReference.set(Thread.currentThread()));

        assertEquals(Thread.currentThread(), callbackThreadReference.get());

        testEntity.setName("updated");
        callbackThreadReference.set(null);
        this.testHolder.update(testEntity, () -> callbackThreadReference.set(Thread.currentThread()));

        final Thread thread = Thread.ofVirtual().start(this.batchQueue::flush);
        thread.join();

        assertEquals(thread, callbackThreadReference.get());
    }

    private TestEntity cachedEntity() {
        final TestEntity testEntity = new TestEntity(UUID.randomUUID());
        testEntity.setName("initial");
        testEntity.setSession("session");
        this.testHolder.cache(testEntity);

        return testEntity;
    }

    private void write(final String operation, final TestEntity testEntity, final Runnable commitRunnable, final Runnable failureRunnable) {
        switch (operation) {
            case "save" -> this.testHolder.save(testEntity, commitRunnable, failureRunnable);
            case "update" -> this.testHolder.update(testEntity, commitRunnable, failureRunnable);
            case "delete" -> this.testHolder.delete(testEntity, commitRunnable, failureRunnable);
            default -> throw new IllegalArgumentException(operation);
        }
    }

    private MockResult[] execute(final MockExecuteContext mockExecuteContext) throws SQLException {
        final SQLException sqlException = this.sqlExceptionReference.get();
        if (sqlException != null) {
            throw sqlException;
        }

        this.sqlStatementCount.addAndGet(mockExecuteContext.batchSQL().length);

        return Arrays.stream(mockExecuteContext.batchSQL()).map(_ -> new MockResult(1, null)).toArray(MockResult[]::new);
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

    private static final class TestRepository extends AbstractEntityRepository<TestEntity> {

        private TestRepository(final CoreDatabase coreDatabase, final boolean optimisticLocking) {
            super(coreDatabase, TestEntity.class, "holder_callback_test", TenantScope.NONE, optimisticLocking);
        }

        private TestRepository(final CoreDatabase coreDatabase) {
            this(coreDatabase, false);
        }
    }

    private static final class TestHolder extends AbstractEntityHolder<TestEntity, TestRepository> {

        private TestHolder(final TestRepository testRepository) {
            super(testRepository, InstanceMode.SINGLE_INSTANCE, RedisMode.DISABLED, Duration.ofMinutes(5), Duration.ofMinutes(5));
        }
    }
}