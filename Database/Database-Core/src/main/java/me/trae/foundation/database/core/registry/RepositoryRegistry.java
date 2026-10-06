package me.trae.foundation.database.core.registry;

import lombok.AllArgsConstructor;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.repository.EntityRepository;
import me.trae.foundation.database.core.driver.PostgresDriver;
import me.trae.foundation.database.core.repository.AbstractEntityRepository;
import me.trae.foundation.database.core.schema.IndexSynchronizer;
import me.trae.foundation.database.core.schema.SchemaSynchronizer;
import org.jooq.DSLContext;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@AllArgsConstructor
public final class RepositoryRegistry {

    private final List<AbstractEntityRepository<?>> repositoryList = new CopyOnWriteArrayList<>();

    private final PostgresDriver postgresDriver;

    public void register(final AbstractEntityRepository<?> repository, final boolean synchronizeNow) {
        if (this.find(repository.getEntityType()).isPresent()) {
            throw new SchemaException("A repository for %s is already registered".formatted(repository.getEntityType().getName()));
        }

        this.repositoryList.add(repository);

        if (synchronizeNow) {
            this.synchronize(repository);
        }
    }

    public void synchronizeAll() {
        this.repositoryList.forEach(this::synchronize);
    }

    public List<EntityRepository<?>> getRepositories() {
        return List.copyOf(this.repositoryList);
    }

    @SuppressWarnings("unchecked")
    public <E extends Entity> Optional<EntityRepository<E>> getRepository(final Class<E> entityType) {
        return this.find(entityType).map(repository -> (EntityRepository<E>) repository);
    }

    private Optional<AbstractEntityRepository<?>> find(final Class<?> entityType) {
        return this.repositoryList.stream()
                .filter(repository -> repository.getEntityType() == entityType)
                .findFirst();
    }

    private void synchronize(final AbstractEntityRepository<?> repository) {
        final DSLContext dslContext = this.postgresDriver.getDslContext();

        SchemaSynchronizer.synchronize(dslContext, repository.getTableSchema());

        IndexSynchronizer.synchronize(dslContext, repository.getTableSchema(), repository.getIndexes());
    }
}