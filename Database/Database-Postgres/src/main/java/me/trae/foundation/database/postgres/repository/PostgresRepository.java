package me.trae.foundation.database.postgres.repository;

import lombok.Getter;
import me.trae.foundation.database.api.declaration.DeclarationLoader;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.entity.EntityInstantiator;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.property.PropertyRegistry;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.repository.Repository;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteResult;
import me.trae.foundation.database.postgres.driver.PostgresDriver;
import me.trae.foundation.database.postgres.index.PostgresIndexType;
import me.trae.foundation.database.postgres.query.PostgresQuery;
import me.trae.foundation.database.postgres.row.PostgresRow;
import me.trae.foundation.database.postgres.schema.SchemaDefinition;
import me.trae.foundation.database.postgres.write.PostgresWrite;
import me.trae.foundation.database.tenant.Tenant;
import me.trae.foundation.database.tenant.TenantScope;
import org.jooq.SelectQuery;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Getter
public abstract class PostgresRepository<EntityType extends Entity> implements Repository<EntityType> {

    private final Class<EntityType> entityType;
    private final String table;
    private final PostgresDriver driver;
    private final TenantScope tenantScope;
    private final List<Property<?, ?>> properties;
    private final PostgresRow<EntityType> row;

    protected PostgresRepository(final Class<EntityType> entityType, final String table, final PostgresDriver driver, final TenantScope tenantScope, final Class<?>... declarations) {
        DeclarationLoader.load(declarations);

        this.entityType = entityType;
        this.table = table;
        this.driver = driver;
        this.tenantScope = tenantScope;
        this.properties = PropertyRegistry.getProperties(entityType);

        if (this.properties.isEmpty()) {
            throw new SchemaException("No properties registered for %s, pass the class declaring them to this repository".formatted(entityType.getName()));
        }

        this.row = new PostgresRow<>(new EntityInstantiator<>(entityType), this.properties);
    }

    public Map<Property<?, ?>, PostgresIndexType> getIndexes() {
        return Collections.emptyMap();
    }

    public List<String> getStatements() {
        return SchemaDefinition.statements(this.table, this.tenantScope.getTenant().isPresent(), this.properties, this.getIndexes());
    }

    @Override
    public boolean claim(final String tenant) {
        return this.driver.claim(this.table, tenant);
    }

    @Override
    public void verify() {
        this.driver.verify();
    }

    @Override
    public WriteResult apply(final Write write) {
        return PostgresWrite.apply(this.driver.getDslContext(), this.table(), this.properties, write);
    }

    @Override
    public void apply(final List<Write> writes) {
        PostgresWrite.applyAll(this.driver.getDslContext(), this.table(), this.properties, writes);
    }

    @Override
    public Optional<EntityType> findById(final UUID id) {
        return Optional.ofNullable(this.driver.getDslContext()
                        .select(this.row.getFields())
                        .from(this.table())
                        .where(PostgresQuery.conditions(Query.all(), this.tenant(), this.properties))
                        .and(PostgresWrite.ID.eq(id))
                        .fetchOne())
                .map(this.row::map);
    }

    @Override
    public List<EntityType> find(final Query query) {
        final SelectQuery<org.jooq.Record> select = this.driver.getDslContext().selectQuery();

        select.addSelect(this.row.getFields());
        select.addFrom(this.table());
        select.addConditions(PostgresQuery.conditions(query, this.tenant(), this.properties));
        select.addOrderBy(PostgresQuery.orders(query));

        if (query.getLimit() > 0) {
            select.addLimit(query.getLimit());
        }

        if (query.getOffset() > 0) {
            select.addOffset(query.getOffset());
        }

        return select.fetch().map(this.row::map);
    }

    @Override
    public long count(final Query query) {
        return this.driver.getDslContext()
                .selectCount()
                .from(this.table())
                .where(PostgresQuery.conditions(query, this.tenant(), this.properties))
                .fetchOne(0, long.class);
    }

    private String tenant() {
        return this.tenantScope.getTenant().map(Tenant::getName).orElse(null);
    }

    private Table<?> table() {
        return DSL.table(DSL.name(this.table));
    }
}