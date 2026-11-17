package me.trae.foundation.database.holder.entity;

import lombok.CustomLog;
import lombok.Getter;
import me.trae.foundation.database.api.declaration.DeclarationLoader;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.holder.Holder;
import me.trae.foundation.database.api.index.GroupedIndex;
import me.trae.foundation.database.api.index.UniqueIndex;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.property.PropertyRegistry;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.repository.Repository;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.holder.context.HolderContext;
import me.trae.foundation.database.holder.context.HolderContextFactory;
import me.trae.foundation.database.holder.event.EventApplier;
import me.trae.foundation.database.holder.refresh.HoldRefresher;
import me.trae.foundation.database.holder.write.EntityWriter;
import me.trae.foundation.database.index.IndexUpdater;
import me.trae.foundation.database.index.UniqueIndexStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@CustomLog
@Getter
public abstract class EntityHolder<EntityType extends Entity, RepositoryType extends Repository<EntityType>> implements Holder<EntityType> {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "Database-Refetch");

        thread.setDaemon(true);

        return thread;
    });

    private final Class<EntityType> entityType;
    private final RepositoryType repository;
    private final HolderContext<EntityType> context;
    private final List<Property<?, ?>> properties;
    private final IndexUpdater updater;
    private final EntityWriter<EntityType> writer;
    private final EventApplier<EntityType> applier;
    private final HoldRefresher<EntityType> refresher;

    protected EntityHolder(final Class<EntityType> entityType, final RepositoryType repository, final HolderContextFactory contextFactory, final Set<Tier> tiers, final Class<?>... declarations) {
        DeclarationLoader.load(declarations);

        this.entityType = entityType;
        this.repository = repository;
        this.context = contextFactory.create(repository, tiers);
        this.properties = PropertyRegistry.getProperties(entityType);
        this.updater = new IndexUpdater(this.context.getIndexStores());
        this.writer = new EntityWriter<>(this.context.getTable(), this.context.getTenant(), this.properties, this.context.getAuthority(), this.context.getPersistence());
        this.applier = new EventApplier<>(this.properties, this.context.getStorageMap().get(Tier.LOCAL), this.writer, this.updater, this.context.getDecoder(), this::refetch);
        this.refresher = new HoldRefresher<>(this::getHeld, this.context.getRefresher(), this.context.getRefreshInterval());

        contextFactory.register(this);
    }

    @Override
    public Optional<EntityType> get(final UUID id, final Set<Tier> tiers) {
        return this.context.getLookup().find(id, tiers);
    }

    @Override
    public Optional<EntityType> get(final UUID id) {
        return this.context.getLookup().find(id);
    }

    @Override
    public <Value> Optional<EntityType> getByProperty(final Property<EntityType, Value> property, final Value value) {
        return this.context.getIndexStores().findByProperty(property)
                .map(store -> this.byStore(store, value).or(() -> this.query(property, value)))
                .orElseGet(() -> this.scan(property, value).or(() -> this.query(property, value)));
    }

    @Override
    public <Value> Optional<EntityType> getByIndex(final UniqueIndex<EntityType, Value> index, final Value key) {
        return this.context.getIndexStores().findUnique(index)
                .flatMap(store -> this.byStore(store, key));
    }

    @Override
    public <Value> List<EntityType> getAllByIndex(final GroupedIndex<EntityType, Value> index, final Value key) {
        return this.context.getIndexStores().findGrouped(index)
                .map(store -> store.lookup(key).stream()
                        .map(this::get)
                        .flatMap(Optional::stream)
                        .collect(Collectors.toCollection(ArrayList::new)))
                .orElseGet(ArrayList::new);
    }

    @Override
    public List<EntityType> getHeld() {
        return Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL))
                .map(storage -> new ArrayList<>(storage.values()))
                .orElseGet(ArrayList::new);
    }

    @Override
    public void holdAll() {
        this.repository.find(Query.all()).forEach(this::take);
    }

    @Override
    public void hold(final UUID id) {
        this.get(id).ifPresent(this::take);
    }

    @Override
    public void release(final UUID id) {
        this.updater.unindex(id);

        Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL)).ifPresent(storage -> storage.remove(id));

        this.context.getUnsubscriber().accept(id);
        this.writer.clearVersion(id);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Released {}, held count {}", id, Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL)).map(storage -> storage.values().size()).orElse(0));
        }
    }

    @Override
    public boolean holds(final UUID id) {
        return Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL))
                .map(storage -> storage.contains(id))
                .orElse(false);
    }

    @Override
    public void save(final EntityType entity) {
        this.writer.save(entity);

        this.updater.index(entity);
    }

    @Override
    public <Value> void set(final EntityType entity, final Property<EntityType, Value> property, final Value value) {
        this.writer.set(entity, property, value);

        this.updater.index(entity, property);
    }

    @Override
    public long increment(final EntityType entity, final Property<EntityType, Long> property, final long delta) {
        final long value = this.writer.increment(entity, property, delta);

        this.updater.index(entity, property);

        return value;
    }

    @Override
    public long decrement(final EntityType entity, final Property<EntityType, Long> property, final long delta) {
        return this.increment(entity, property, -delta);
    }

    @Override
    public void delete(final EntityType entity) {
        this.writer.delete(entity);

        this.release(entity.getId());
    }

    @Override
    public void reindex(final EntityType entity) {
        this.updater.index(entity);
    }

    public void close() {
        this.refresher.stop();
        this.executor.shutdownNow();
    }

    private void take(final EntityType entity) {
        Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL)).ifPresent(storage -> storage.put(entity.getId(), entity));

        this.writer.setVersion(entity.getId(), this.context.getVersion().applyAsLong(entity.getId()));
        this.updater.index(entity);
        this.context.getSubscriber().accept(entity.getId(), this.applier::apply);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Held {}, held count {}", entity.getId(), Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL)).map(storage -> storage.values().size()).orElse(0));
        }
    }

    private void refetch(final UUID id) {
        this.executor.execute(() -> {
            try {
                this.reload(id);
            } catch (final RuntimeException exception) {
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("Failed to refetch {}", id, exception);
                }
                throw exception;
            }
        });
    }

    private void reload(final UUID id) {
        this.context.getLookup().find(id, Set.of(Tier.REDIS, Tier.POSTGRES)).ifPresent(entity -> {
            Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL)).ifPresent(storage -> storage.put(id, entity));

            this.writer.setVersion(id, this.context.getVersion().applyAsLong(id));
            this.updater.index(entity);
        });
    }

    private Optional<EntityType> byStore(final UniqueIndexStore store, final Object key) {
        return store.lookup(key).flatMap(this::get);
    }

    private <Value> Optional<EntityType> scan(final Property<EntityType, Value> property, final Value value) {
        return this.getHeld().stream()
                .filter(entity -> Objects.equals(property.get(entity), value))
                .findFirst();
    }

    private <Value> Optional<EntityType> query(final Property<EntityType, Value> property, final Value value) {
        if (!this.context.getTiers().contains(Tier.POSTGRES)) {
            return Optional.empty();
        }

        final Optional<EntityType> entity = this.repository.find(Query.where(property, value)).stream().findFirst();

        entity.ifPresent(this.updater::index);

        return entity;
    }
}