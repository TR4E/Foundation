package me.trae.foundation.database.holder.entity;

import lombok.CustomLog;
import lombok.Getter;
import me.trae.foundation.database.api.declaration.DeclarationLoader;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.event.ChangeEvent;
import me.trae.foundation.database.api.holder.Holder;
import me.trae.foundation.database.api.index.GroupedIndex;
import me.trae.foundation.database.api.index.UniqueIndex;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.property.PropertyRegistry;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.repository.Repository;
import me.trae.foundation.database.api.storage.Storage;
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

    private static final int RELOAD_ATTEMPTS = 3;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "Database-Refetch");

        thread.setDaemon(true);

        return thread;
    });

    private final ExecutorService events = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "Database-Events");

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
        this.writer = new EntityWriter<>(this.context.getTable(), this.context.getTenant(), this.properties, this.context.getAuthority(), this.context.getPersistence(), this::refetch, this.context.getGate());
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
        return this.context.getIndexStores().findUnique(index).flatMap(store -> this.byStore(store, key));
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
        this.context.getGate().enter();

        try {
            this.updater.unindex(id);

            Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL)).ifPresent(storage -> storage.remove(id));

            this.context.getUnsubscriber().accept(id);

            this.writer.clearVersion(id);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Released {}, held count {}", id, Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL)).map(storage -> storage.values().size()).orElse(0));
            }
        } finally {
            this.context.getGate().exit();
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
        this.context.getGate().enter();

        try {
            this.writer.save(entity);

            this.updater.index(entity);

            this.reconcile(entity);
        } finally {
            this.context.getGate().exit();
        }
    }

    @Override
    public <Value> void set(final EntityType entity, final Property<EntityType, Value> property, final Value value) {
        this.context.getGate().enter();

        try {
            this.writer.set(entity, property, value);

            this.updater.index(entity, property);

            this.reconcile(entity);
        } finally {
            this.context.getGate().exit();
        }
    }

    @Override
    public long increment(final EntityType entity, final Property<EntityType, Long> property, final long delta) {
        this.context.getGate().enter();

        try {
            final long value = this.writer.increment(entity, property, delta);

            this.updater.index(entity, property);

            this.reconcile(entity);

            return value;
        } finally {
            this.context.getGate().exit();
        }
    }

    @Override
    public long decrement(final EntityType entity, final Property<EntityType, Long> property, final long delta) {
        return this.increment(entity, property, -delta);
    }

    @Override
    public boolean decrementIfAtLeast(final EntityType entity, final Property<EntityType, Long> property, final long delta, final long floor) {
        this.context.getGate().enter();

        try {
            final boolean decremented = this.writer.decrementIfAtLeast(entity, property, delta, floor);

            if (decremented) {
                this.updater.index(entity, property);

                this.reconcile(entity);
            }

            return decremented;
        } finally {
            this.context.getGate().exit();
        }
    }

    @Override
    public void delete(final EntityType entity) {
        this.context.getGate().enter();

        try {
            this.writer.delete(entity);

            this.release(entity.getId());
        } finally {
            this.context.getGate().exit();
        }
    }

    @Override
    public void reindex(final EntityType entity) {
        this.context.getGate().enter();

        try {
            this.updater.index(entity);
        } finally {
            this.context.getGate().exit();
        }
    }

    public void close() {
        this.refresher.stop();

        this.events.shutdownNow();

        this.executor.shutdownNow();
    }

    private void reconcile(final EntityType entity) {
        Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL))
                .flatMap(storage -> storage.get(entity.getId()))
                .filter(held -> held != entity)
                .ifPresent(_ -> this.refetch(entity.getId()));
    }

    private void take(final EntityType entity) {
        Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL)).ifPresent(storage -> storage.put(entity.getId(), entity));

        this.writer.setVersion(entity.getId(), this.context.getVersion().applyAsLong(entity.getId()));

        this.updater.index(entity);

        this.context.getSubscriber().accept(entity.getId(), this::dispatch);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Held {}, held count {}", entity.getId(), Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL)).map(storage -> storage.values().size()).orElse(0));
        }
    }

    private void dispatch(final ChangeEvent event) {
        this.events.execute(() -> this.apply(event));
    }

    private void apply(final ChangeEvent event) {
        try {
            this.applier.apply(event);
        } catch (final RuntimeException exception) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Failed to apply event for {}", event.getKey().getId(), exception);
            }
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
        for (int attempt = 0; attempt < RELOAD_ATTEMPTS; attempt++) {
            if (!this.holds(id)) {
                return;
            }

            final long tracked = this.writer.getVersion(id);

            final long before = this.context.getVersion().applyAsLong(id);

            final Optional<EntityType> entity = this.read(id);

            if (this.context.getVersion().applyAsLong(id) != before) {
                continue;
            }

            entity.ifPresent(found -> this.install(id, found, before, tracked));

            return;
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Gave up reloading {} after {} attempts, the backend kept changing", id, RELOAD_ATTEMPTS);
        }
    }

    private Optional<EntityType> read(final UUID id) {
        final Storage<UUID, EntityType> storage = this.context.getStorageMap().get(Tier.REDIS);

        if (storage == null) {
            return this.context.getLookup().find(id, Set.of(Tier.REDIS, Tier.POSTGRES));
        }

        return storage.get(id).or(() -> this.context.getLookup().find(id, Set.of(Tier.REDIS, Tier.POSTGRES)));
    }

    private void install(final UUID id, final EntityType entity, final long version, final long tracked) {
        synchronized (this.writer.state(id)) {
            if (!this.writer.adopt(id, version, tracked)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Abandoned reload of {}, tracked moved from {} to {}", id, tracked, this.writer.getVersion(id));
                }
                return;
            }

            final Storage<UUID, EntityType> storage = this.context.getStorageMap().get(Tier.LOCAL);

            if (storage != null) {
                storage.put(id, entity);
            }

            this.updater.index(entity);
        }
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