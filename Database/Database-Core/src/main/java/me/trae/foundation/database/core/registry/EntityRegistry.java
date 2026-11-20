package me.trae.foundation.database.core.registry;

import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.repository.Repository;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.holder.entity.EntityHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public final class EntityRegistry {

    private final ConcurrentHashMap<Class<?>, Repository<?>> repositoryMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, EntityHolder<?, ?>> holderMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Repository<?>> tableMap = new ConcurrentHashMap<>();

    public void register(final Repository<?> repository, final EntityHolder<?, ?> holder) {
        this.repositoryMap.put(repository.getEntityType(), repository);

        this.holderMap.put(repository.getEntityType(), holder);

        this.tableMap.put(repository.getTable(), repository);
    }

    public void apply(final Write write) {
        Optional.ofNullable(this.tableMap.get(write.getKey().getTable())).ifPresent(repository -> repository.apply(write));
    }

    public void apply(final List<Write> writes) {
        final LinkedHashMap<String, List<Write>> tableWrites = new LinkedHashMap<>();

        writes.forEach(write -> tableWrites.computeIfAbsent(write.getKey().getTable(), _ -> new ArrayList<>()).add(write));

        tableWrites.forEach(this::apply);
    }

    @SuppressWarnings("unchecked")
    public <EntityType extends Entity> Repository<EntityType> getRepository(final Class<EntityType> entityType) {
        return (Repository<EntityType>) this.repositoryMap.get(entityType);
    }

    @SuppressWarnings("unchecked")
    public <EntityType extends Entity> EntityHolder<EntityType, ?> getHolder(final Class<EntityType> entityType) {
        return (EntityHolder<EntityType, ?>) this.holderMap.get(entityType);
    }

    public Collection<EntityHolder<?, ?>> getHolders() {
        return this.holderMap.values();
    }

    private void apply(final String table, final List<Write> writes) {
        Optional.ofNullable(this.tableMap.get(table)).ifPresent(repository -> repository.apply(writes));
    }
}