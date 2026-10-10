package me.trae.foundation.database.core.batch;

import lombok.AllArgsConstructor;
import me.trae.foundation.database.api.exception.ConnectionException;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.exception.OptimisticLockException;
import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.core.schema.TableSchema;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@AllArgsConstructor
public final class BatchExecutor {

    private static final List<String> TRANSIENT_STATE_LIST = List.of("00", "08", "40", "53", "57", "58");

    private final PostgresDriver postgresDriver;
    private final Consumer<DatabaseException> failureHandler;

    public List<PendingWrite> execute(final List<PendingWrite> writeList) {
        final List<PendingWrite> staleWriteList = new ArrayList<>();
        final List<PendingWrite> changedWriteList = new ArrayList<>();

        try {
            this.postgresDriver.getDslContext().transaction(configuration -> {
                final DSLContext dslContext = DSL.using(configuration);

                final List<Query> queryList = new ArrayList<>();

                for (final PendingWrite pendingWrite : writeList) {
                    queryList.add(this.render(dslContext, pendingWrite));
                }

                final int[] resultList = dslContext.batch(queryList).execute();
                for (int index = 0; index < writeList.size(); index++) {
                    final PendingWrite pendingWrite = writeList.get(index);

                    if (resultList[index] == 0 && this.isStaleWrite(dslContext, pendingWrite)) {
                        staleWriteList.add(pendingWrite);
                    } else if (resultList[index] != 0 || pendingWrite.getWriteType() == WriteType.DELETE) {
                        changedWriteList.add(pendingWrite);
                    }
                }

                CacheInvalidationOutbox.insert(dslContext, changedWriteList);
            });
        } catch (final ConnectionException exception) {
            return writeList;
        } catch (final DataAccessException exception) {
            return this.isTransient(exception) ? writeList : this.executeIndividually(writeList);
        }

        for (final PendingWrite pendingWrite : writeList) {
            if (staleWriteList.contains(pendingWrite)) {
                this.reportStaleWrite(pendingWrite);
            } else {
                this.complete(pendingWrite, changedWriteList.contains(pendingWrite));
            }
        }

        return Collections.emptyList();
    }

    private List<PendingWrite> executeIndividually(final List<PendingWrite> writeList) {
        final List<PendingWrite> retryList = new ArrayList<>();

        for (final PendingWrite pendingWrite : writeList) {
            try {
                final int[] result = new int[1];
                final boolean[] stale = new boolean[1];

                this.postgresDriver.getDslContext().transaction(configuration -> {
                    final DSLContext dslContext = DSL.using(configuration);

                    result[0] = this.render(dslContext, pendingWrite).execute();
                    stale[0] = result[0] == 0 && this.isStaleWrite(dslContext, pendingWrite);

                    if (!stale[0] && (result[0] != 0 || pendingWrite.getWriteType() == WriteType.DELETE)) {
                        CacheInvalidationOutbox.insert(dslContext, List.of(pendingWrite));
                    }
                });

                if (stale[0]) {
                    this.reportStaleWrite(pendingWrite);
                } else {
                    this.complete(pendingWrite, result[0] != 0);
                }
            } catch (final ConnectionException exception) {
                retryList.add(pendingWrite);
            } catch (final DataAccessException exception) {
                if (this.isTransient(exception)) {
                    retryList.add(pendingWrite);
                    continue;
                }

                this.failureHandler.accept(new DatabaseException("Dropped write to %s for %s".formatted(pendingWrite.getTableSchema().getTableName(), pendingWrite.getId()), exception));
            }
        }

        return retryList;
    }

    private Query render(final DSLContext dslContext, final PendingWrite pendingWrite) {
        final TableSchema<?> tableSchema = pendingWrite.getTableSchema();

        if (pendingWrite.getWriteType() == WriteType.DELETE) {
            Condition condition = tableSchema.getKeyCondition(pendingWrite.getTenantId(), pendingWrite.getId());

            if (pendingWrite.getExpectedRevision() != null) {
                condition = condition.and(TableSchema.REVISION_FIELD.eq(pendingWrite.getExpectedRevision()));
            }

            return dslContext.deleteFrom(tableSchema.getTable()).where(condition);
        }

        final Map<Field<?>, Object> insertMap = new LinkedHashMap<>(pendingWrite.getValueMap());

        insertMap.put(TableSchema.ID_FIELD, pendingWrite.getId());

        if (tableSchema.isTenantScoped()) {
            insertMap.put(TableSchema.TENANT_FIELD, pendingWrite.getTenantId());
        }

        if (pendingWrite.getValueMap().isEmpty()) {
            return dslContext.insertInto(tableSchema.getTable()).set(insertMap).onConflict(tableSchema.getKeyFields()).doNothing();
        }

        Condition changedCondition = DSL.falseCondition();
        for (final Field<?> field : pendingWrite.getValueMap().keySet()) {
            if (field.equals(TableSchema.REVISION_FIELD)) {
                continue;
            }

            final Field<?> storedField = DSL.field(DSL.name(tableSchema.getTableName(), field.getName()), field.getDataType());
            changedCondition = changedCondition.or(DSL.condition("{0} is distinct from {1}", storedField, DSL.excluded(field)));
        }

        if (pendingWrite.getExpectedRevision() != null) {
            final Field<Long> storedRevision = DSL.field(DSL.name(tableSchema.getTableName(), TableSchema.REVISION_FIELD.getName()), TableSchema.REVISION_FIELD.getDataType());
            changedCondition = changedCondition.and(storedRevision.eq(pendingWrite.getExpectedRevision()));
        }

        return dslContext.insertInto(tableSchema.getTable()).set(insertMap)
                .onConflict(tableSchema.getKeyFields())
                .doUpdate()
                .set(pendingWrite.getValueMap())
                .where(changedCondition);
    }

    private boolean isStaleWrite(final DSLContext dslContext, final PendingWrite pendingWrite) {
        if (pendingWrite.getExpectedRevision() == null) {
            return false;
        }

        final Long storedRevision = dslContext.select(TableSchema.REVISION_FIELD)
                .from(pendingWrite.getTableSchema().getTable())
                .where(pendingWrite.getTableSchema().getKeyCondition(pendingWrite.getTenantId(), pendingWrite.getId()))
                .fetchOne(TableSchema.REVISION_FIELD);

        return storedRevision != null && !storedRevision.equals(pendingWrite.getExpectedRevision());
    }

    private void reportStaleWrite(final PendingWrite pendingWrite) {
        this.failureHandler.accept(new OptimisticLockException("Rejected stale write for %s at revision %s".formatted(pendingWrite.getId(), pendingWrite.getExpectedRevision())));
    }

    private void complete(final PendingWrite pendingWrite, final boolean changed) {
        for (final Runnable callback : pendingWrite.getCommitCallbackList()) {
            try {
                callback.run();
            } catch (final RuntimeException exception) {
                this.failureHandler.accept(new DatabaseException("Commit callback failed for %s".formatted(pendingWrite.getId()), exception));
            }
        }

        if (changed && pendingWrite.getRevisionCallback() != null) {
            try {
                pendingWrite.getRevisionCallback().run();
            } catch (final RuntimeException exception) {
                this.failureHandler.accept(new DatabaseException("Revision callback failed for %s".formatted(pendingWrite.getId()), exception));
            }
        }
    }

    private boolean isTransient(final DataAccessException exception) {
        final String sqlState = exception.sqlState();

        return sqlState == null || TRANSIENT_STATE_LIST.stream().anyMatch(sqlState::startsWith);
    }
}