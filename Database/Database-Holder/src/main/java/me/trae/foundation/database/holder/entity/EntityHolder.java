package me.trae.foundation.database.holder.entity;

import lombok.CustomLog;
import lombok.Getter;
import me.trae.foundation.database.api.declaration.DeclarationLoader;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.event.ChangeEvent;
import me.trae.foundation.database.api.exception.OwnershipException;
import me.trae.foundation.database.api.holder.Holder;
import me.trae.foundation.database.api.index.GroupedIndex;
import me.trae.foundation.database.api.index.UniqueIndex;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.property.PropertyRegistry;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.repository.Repository;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.api.storage.VersionedEntity;
import me.trae.foundation.database.api.tier.Tier;
import me.trae.foundation.database.holder.context.HolderContext;
import me.trae.foundation.database.holder.context.HolderContextFactory;
import me.trae.foundation.database.holder.event.EventApplier;
import me.trae.foundation.database.holder.refresh.HoldRefresher;
import me.trae.foundation.database.holder.write.EntityWriter;
import me.trae.foundation.database.index.GroupedIndexStore;
import me.trae.foundation.database.index.IndexUpdater;
import me.trae.foundation.database.index.UniqueIndexStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    private volatile boolean owned = true;

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
        this.writer = new EntityWriter<>(this.context.getTable(), this.context.getTenant(), this.properties, this.context.getAuthority(), this.context.getPersistence(), this::refetch, this.context.getGate(), tiers.contains(Tier.LOCAL), this::holds, id -> this.local(id).orElse(null));
        this.applier = new EventApplier<>(this.properties, this.context.getStorageMap().get(Tier.LOCAL), this.writer, this.updater, this.context.getDecoder(), this::refetch, this::release);
        this.refresher = new HoldRefresher<>(this::getHeld, this::refreshAll, this.context.getRefreshInterval());

        Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL)).ifPresent(storage -> storage.onEviction(this::release));

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
    public List<EntityType> getAll(final Collection<UUID> ids) {
        return this.collect(ids);
    }

    @Override
    public <Value> Optional<EntityType> getByProperty(final Property<EntityType, Value> property, final Value value) {
        final UniqueIndexStore store = this.context.getIndexStores().byProperty(property);

        if (store == null) {
            final Optional<EntityType> scanned = this.scan(property, value);

            return scanned.isEmpty() ? this.query(property, value) : scanned;
        }

        final Optional<EntityType> indexed = this.byStore(store, value);

        return indexed.isEmpty() ? this.query(property, value) : indexed;
    }

    @Override
    public <Value> Optional<EntityType> getByIndex(final UniqueIndex<EntityType, Value> index, final Value key) {
        final UniqueIndexStore store = this.context.getIndexStores().unique(index);

        return store == null ? Optional.empty() : this.byStore(store, key);
    }

    @Override
    public <Value> List<EntityType> getAllByIndex(final GroupedIndex<EntityType, Value> index, final Value key) {
        final GroupedIndexStore store = this.context.getIndexStores().grouped(index);

        if (store == null) {
            return new ArrayList<>();
        }

        final Set<UUID> members = store.members(key);

        final List<EntityType> entities = new ArrayList<>(members.size());

        members.forEach(id -> this.get(id).ifPresent(entities::add));

        return entities;
    }

    @Override
    public List<EntityType> getHeld() {
        return Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL))
                .map(storage -> new ArrayList<>(storage.values()))
                .orElseGet(ArrayList::new);
    }

    @Override
    public void holdAll() {
        this.claim();

        final List<EntityType> entities = this.repository.find(Query.all());

        final List<UUID> keys = entities.stream().map(Entity::getId).collect(Collectors.toCollection(ArrayList::new));

        final Map<UUID, VersionedEntity<EntityType>> snapshots = this.context.getSnapshots().apply(keys);

        final List<EntityType> held = new ArrayList<>();

        entities.forEach(entity -> this.store(snapshots.get(entity.getId()), entity, held));

        this.updater.index(held);

        final List<UUID> ids = held.stream().map(Entity::getId).filter(this::holds).collect(Collectors.toCollection(ArrayList::new));

        this.context.getSubscriber().accept(ids, this::dispatch);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Held {} of {} entities on {}", ids.size(), entities.size(), this.context.getTable());
        }
    }

    @Override
    public void hold(final UUID id) {
        this.context.getSnapshot().apply(id).ifPresentOrElse(snapshot -> this.take(snapshot.getEntity(), snapshot.getVersion()), () -> this.get(id).ifPresent(this::take));
    }

    @Override
    public void release(final UUID id) {
        this.context.getGate().enter();

        try {
            synchronized (this.writer.state(id)) {
                this.updater.unindex(id);

                Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL)).ifPresent(storage -> storage.remove(id));

                this.context.getUnsubscriber().accept(id);

                this.writer.clearVersion(id);
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Released {}, held count {}", id, Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL)).map(storage -> storage.values().size()).orElse(0));
            }
        } finally {
            this.context.getGate().exit();
        }
    }

    @Override
    public boolean holds(final UUID id) {
        return Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL)).map(storage -> storage.contains(id)).orElse(false);
    }

    @Override
    public void save(final EntityType entity) {
        this.authorize();

        this.context.getGate().enter();

        try {
            this.writer.save(entity);

            this.updater.index(entity);
        } finally {
            this.context.getGate().exit();
        }
    }

    @Override
    public <Value> void set(final EntityType entity, final Property<EntityType, Value> property, final Value value) {
        this.authorize();

        this.context.getGate().enter();

        try {
            this.writer.set(entity, property, value);

            this.updater.index(entity, property);
        } finally {
            this.context.getGate().exit();
        }
    }

    @Override
    public long increment(final EntityType entity, final Property<EntityType, Long> property, final long delta) {
        this.authorize();

        this.context.getGate().enter();

        try {
            final long value = this.writer.increment(entity, property, delta);

            this.updater.index(entity, property);

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
        this.authorize();

        this.context.getGate().enter();

        try {
            final boolean decremented = this.writer.decrementIfAtLeast(entity, property, delta, floor);

            if (decremented) {
                this.updater.index(entity, property);
            }

            return decremented;
        } finally {
            this.context.getGate().exit();
        }
    }

    @Override
    public void delete(final EntityType entity) {
        this.authorize();

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

    private List<EntityType> collect(final Collection<UUID> ids) {
        final List<EntityType> entities = new ArrayList<>();

        final List<UUID> missing = new ArrayList<>();

        ids.forEach(id -> this.local(id).ifPresentOrElse(entities::add, () -> missing.add(id)));

        final Map<UUID, VersionedEntity<EntityType>> snapshots = this.context.getSnapshots().apply(missing);

        missing.forEach(id -> this.resolve(id, snapshots.get(id)).ifPresent(entities::add));

        return entities;
    }

    private Optional<EntityType> local(final UUID id) {
        return Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL)).flatMap(storage -> storage.get(id));
    }

    private Optional<EntityType> resolve(final UUID id, final VersionedEntity<EntityType> snapshot) {
        return snapshot == null ? this.get(id) : Optional.of(snapshot.getEntity());
    }

    private boolean subscribable(final UUID id) {
        final Storage<UUID, EntityType> storage = this.context.getStorageMap().get(Tier.LOCAL);

        if (storage == null) {
            return true;
        }

        return storage.contains(id);
    }

    private void take(final EntityType entity, final long version) {
        if (!this.store(entity, version)) {
            return;
        }

        this.context.getSubscriber().accept(List.of(entity.getId()), this::dispatch);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Held {} at version {}", entity.getId(), version);
        }
    }

    private void take(final EntityType entity) {
        this.take(entity, this.context.getVersion().applyAsLong(entity.getId()));
    }

    private boolean store(final EntityType entity, final long version, final boolean index) {
        synchronized (this.writer.state(entity.getId())) {
            if (!this.writer.install(entity.getId(), version)) {
                return false;
            }

            final Storage<UUID, EntityType> storage = this.context.getStorageMap().get(Tier.LOCAL);

            if (storage != null) {
                storage.put(entity.getId(), entity);

                if (!storage.contains(entity.getId())) {
                    return false;
                }
            }

            if (index) {
                this.updater.index(entity);
            }

            return true;
        }
    }

    private boolean store(final EntityType entity, final long version) {
        return this.store(entity, version, true);
    }

    private void store(final VersionedEntity<EntityType> snapshot, final EntityType entity, final List<EntityType> held) {
        if (snapshot == null) {
            if (this.store(entity, 0L, false)) {
                held.add(entity);
            }
            return;
        }

        if (this.store(snapshot.getEntity(), snapshot.getVersion(), false)) {
            held.add(snapshot.getEntity());
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

            final boolean installed = this.context.getSnapshot().apply(id)
                    .or(() -> this.fallback(id))
                    .map(snapshot -> this.install(id, snapshot, tracked))
                    .orElse(true);

            if (installed) {
                return;
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Gave up reloading {} after {} attempts, tracking kept moving", id, RELOAD_ATTEMPTS);
        }
    }

    private Optional<VersionedEntity<EntityType>> fallback(final UUID id) {
        return this.repository.findById(id)
                .map(entity -> new VersionedEntity<>(entity, this.context.getVersion().applyAsLong(id)));
    }

    private boolean install(final UUID id, final VersionedEntity<EntityType> snapshot, final long tracked) {
        final Storage<UUID, EntityType> storage = this.context.getStorageMap().get(Tier.LOCAL);

        synchronized (this.writer.state(id)) {
            if (storage != null && !storage.contains(id)) {
                return true;
            }

            if (!this.writer.adopt(id, snapshot.getVersion(), tracked)) {
                return false;
            }

            if (storage != null) {
                storage.put(id, snapshot.getEntity());
            }

            this.updater.index(snapshot.getEntity());

            return true;
        }
    }

    private Optional<EntityType> byStore(final UniqueIndexStore store, final Object key) {
        final UUID id = store.lookup(key);

        return id == null ? Optional.empty() : this.get(id);
    }

    private void refreshAll(final List<EntityType> entities) {
        this.context.getRefresher().accept(entities);

        this.sweep(entities);

        this.revalidate();
    }

    private void revalidate() {
        if (!this.exclusive()) {
            return;
        }

        this.repository.verify();

        this.owned = this.repository.claim(this.context.getTenant());

        if (this.owned) {
            return;
        }

        if (LOGGER.isErrorEnabled()) {
            LOGGER.error("Lost exclusive ownership of {} on tenant {}, refusing writes until it is regained", this.context.getTable(), this.context.getTenant());
        }
    }

    private void claim() {
        if (!this.exclusive()) {
            return;
        }

        this.owned = this.repository.claim(this.context.getTenant());

        if (this.owned) {
            return;
        }

        throw new OwnershipException("Another instance already holds %s on tenant %s. A holder without a Redis tier can only be held by one instance at a time".formatted(this.context.getTable(), this.context.getTenant()));
    }

    private void authorize() {
        if (this.owned) {
            return;
        }

        throw new OwnershipException("Lost exclusive ownership of %s on tenant %s, another instance holds it".formatted(this.context.getTable(), this.context.getTenant()));
    }

    private boolean exclusive() {
        if (this.context.getTenant() == null) {
            return false;
        }

        return !this.context.getTiers().contains(Tier.REDIS);
    }

    private void sweep(final List<EntityType> entities) {
        if (!this.context.getTiers().contains(Tier.REDIS)) {
            return;
        }

        final List<UUID> ids = entities.stream().map(Entity::getId).collect(Collectors.toCollection(ArrayList::new));

        this.context.getVersions().apply(ids).forEach(this::diverged);
    }

    private void diverged(final UUID id, final long version) {
        if (version == this.writer.getVersion(id)) {
            return;
        }

        this.refetch(id);
    }

    private <Value> Optional<EntityType> scan(final Property<EntityType, Value> property, final Value value) {
        return Optional.ofNullable(this.context.getStorageMap().get(Tier.LOCAL))
                .flatMap(storage -> storage.values().stream()
                        .filter(entity -> Objects.equals(property.get(entity), value))
                        .findFirst());
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