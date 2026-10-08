package me.trae.foundation.database.core.repository;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.query.EntityPage;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.query.TenantSelection;
import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.core.schema.TableSchema;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectQuery;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class RepositoryReader<E extends Entity> {

    private final PostgresDriver postgresDriver;
    private final TableSchema<E> tableSchema;
    private final String tenantId;
    private final EntityRecordMapper<E> entityRecordMapper;

    public RepositoryReader(final PostgresDriver postgresDriver, final TableSchema<E> tableSchema, final String tenantId) {
        this.postgresDriver = postgresDriver;
        this.tableSchema = tableSchema;
        this.tenantId = tenantId;
        this.entityRecordMapper = new EntityRecordMapper<>(tableSchema);
    }

    public Optional<E> findById(final UUID id) {
        return this.getDslContext().selectFrom(this.tableSchema.getTable())
                .where(this.tableSchema.getKeyCondition(this.tenantId, id))
                .fetchOptional()
                .map(this.entityRecordMapper::map);
    }

    public List<E> findManyById(final Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        return this.getDslContext().selectFrom(this.tableSchema.getTable())
                .where(TableSchema.ID_FIELD.in(ids).and(QueryRenderer.toTenantCondition(this.tableSchema, TenantSelection.OWN, null, this.tenantId)))
                .fetch(this.entityRecordMapper::map);
    }

    public Optional<E> findOne(final Query<E> query) {
        final SelectQuery<Record> selectQuery = this.createSelect(query);

        selectQuery.addLimit(1);

        return selectQuery.fetchOptional().map(this.entityRecordMapper::map);
    }

    public List<E> findMany(final Query<E> query) {
        final SelectQuery<Record> selectQuery = this.createSelect(query);

        if (query.getLimit() > 0) {
            selectQuery.addLimit(query.getLimit());
        }

        if (query.getOffset() > 0) {
            selectQuery.addOffset(query.getOffset());
        }

        return selectQuery.fetch(this.entityRecordMapper::map);
    }

    public long count(final Query<E> query) {
        return this.getDslContext().fetchCount(this.tableSchema.getTable(), QueryRenderer.toCondition(this.tableSchema, query, this.tenantId));
    }

    public boolean exists(final Query<E> query) {
        return this.getDslContext().fetchExists(this.tableSchema.getTable(), QueryRenderer.toCondition(this.tableSchema, query, this.tenantId));
    }

    public EntityPage<E> findPage(final Query<E> query, final int page, final int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Page size must be at least 1");
        }

        final long totalCount = this.count(query);

        final int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / (double) size));

        final int current = Math.clamp(page, 1, totalPages);

        if (totalCount == 0L) {
            return new EntityPage<>(Collections.emptyList(), 0L, current, size);
        }

        final SelectQuery<Record> selectQuery = this.createSelect(query);

        selectQuery.addLimit(size);
        selectQuery.addOffset((current - 1) * size);

        return new EntityPage<>(selectQuery.fetch(this.entityRecordMapper::map), totalCount, current, size);
    }

    private SelectQuery<Record> createSelect(final Query<E> query) {
        final SelectQuery<Record> selectQuery = this.getDslContext().selectQuery(this.tableSchema.getTable());

        selectQuery.addConditions(QueryRenderer.toCondition(this.tableSchema, query, this.tenantId));
        selectQuery.addOrderBy(QueryRenderer.toSortFields(this.tableSchema, query));

        return selectQuery;
    }

    private DSLContext getDslContext() {
        return this.postgresDriver.getDslContext();
    }
}