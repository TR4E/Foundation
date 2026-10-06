package me.trae.foundation.database.core.batch;

import lombok.AllArgsConstructor;
import me.trae.foundation.database.api.exception.ConnectionException;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.core.schema.TableSchema;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.SQLStateClass;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@AllArgsConstructor
public final class BatchExecutor {

    private final PostgresDriver postgresDriver;
    private final Consumer<DatabaseException> failureHandler;

    public List<PendingWrite> execute(final List<PendingWrite> writeList) {
        try {
            this.postgresDriver.getDslContext().transaction(configuration -> {
                final DSLContext dslContext = DSL.using(configuration);

                dslContext.batch(writeList.stream().map(pendingWrite -> this.render(dslContext, pendingWrite)).toList()).execute();
            });
        } catch (final ConnectionException exception) {
            return writeList;
        } catch (final DataAccessException exception) {
            return this.isIntegrityViolation(exception) ? this.executeIndividually(writeList) : writeList;
        }

        writeList.forEach(this::complete);

        return Collections.emptyList();
    }

    private List<PendingWrite> executeIndividually(final List<PendingWrite> writeList) {
        final List<PendingWrite> retryList = new ArrayList<>();
        final DSLContext dslContext = this.postgresDriver.getDslContext();

        for (final PendingWrite pendingWrite : writeList) {
            try {
                this.render(dslContext, pendingWrite).execute();

                this.complete(pendingWrite);
            } catch (final DataAccessException exception) {
                if (!this.isIntegrityViolation(exception)) {
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
            return dslContext.deleteFrom(tableSchema.getTable()).where(tableSchema.getKeyCondition(pendingWrite.getTenantId(), pendingWrite.getId()));
        }

        final Map<Field<?>, Object> insertMap = new LinkedHashMap<>(pendingWrite.getValueMap());

        insertMap.put(TableSchema.ID_FIELD, pendingWrite.getId());

        if (tableSchema.isTenantScoped()) {
            insertMap.put(TableSchema.TENANT_FIELD, pendingWrite.getTenantId());
        }

        if (pendingWrite.getValueMap().isEmpty()) {
            return dslContext.insertInto(tableSchema.getTable()).set(insertMap).onConflict(tableSchema.getKeyFields()).doNothing();
        }

        return dslContext.insertInto(tableSchema.getTable()).set(insertMap).onConflict(tableSchema.getKeyFields()).doUpdate().set(pendingWrite.getValueMap());
    }

    private void complete(final PendingWrite pendingWrite) {
        for (final Runnable callback : pendingWrite.getCommitCallbackList()) {
            try {
                callback.run();
            } catch (final RuntimeException exception) {
                this.failureHandler.accept(new DatabaseException("Commit callback failed for %s".formatted(pendingWrite.getId()), exception));
            }
        }
    }

    private boolean isIntegrityViolation(final DataAccessException exception) {
        return exception.sqlStateClass() == SQLStateClass.C23_INTEGRITY_CONSTRAINT_VIOLATION;
    }
}