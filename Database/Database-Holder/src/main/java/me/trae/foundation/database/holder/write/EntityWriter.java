package me.trae.foundation.database.holder.write;

import lombok.AllArgsConstructor;
import lombok.CustomLog;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.QueryException;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.exception.VersionConflictException;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteGate;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.WriteResult;
import me.trae.foundation.database.api.write.change.Change;
import me.trae.foundation.database.api.write.change.ChangeType;
import me.trae.foundation.database.api.write.change.Decrement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CustomLog
@AllArgsConstructor
@Getter
public final class EntityWriter<EntityType extends Entity> {

    private static final long DELETED = -1L;
    private static final long GUARDED_LIMIT = 1L << 53;
    private static final int LOCKS = 1024;

    private final List<ReentrantReadWriteLock> lockList = Stream.generate(ReentrantReadWriteLock::new).limit(LOCKS).collect(Collectors.toCollection(ArrayList::new));
    private final List<Object> stateList = Stream.generate(Object::new).limit(LOCKS).collect(Collectors.toCollection(ArrayList::new));

    private final ConcurrentHashMap<UUID, Long> versionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> persistentMap = new ConcurrentHashMap<>();

    private final String table, tenant;
    private final List<Property<?, ?>> properties;
    private final Function<Write, WriteResult> authority;
    private final Consumer<Write> persistence;
    private final Consumer<UUID> refetch;
    private final WriteGate gate;
    private final boolean tracking;
    private final Predicate<UUID> held;
    private final Function<UUID, EntityType> resolver;

    public void save(final EntityType entity) {
        final Lock lock = this.lock(entity.getId()).writeLock();

        lock.lock();

        try {
            final List<Change> changes = this.properties.stream()
                    .filter(Property::isPersistent)
                    .map(property -> change(property, entity))
                    .collect(Collectors.toCollection(ArrayList::new));

            final WriteResult result = this.apply(entity, WriteKind.SAVE, changes);

            synchronized (this.state(entity.getId())) {
                this.recreate(entity.getId(), result.getVersion());

                this.copy(entity);
            }
        } finally {
            lock.unlock();
        }
    }

    public <Value> void set(final EntityType entity, final Property<EntityType, Value> property, final Value value) {
        final Lock lock = this.lock(entity.getId()).writeLock();

        lock.lock();

        try {
            final WriteResult result = this.apply(entity, WriteKind.UPDATE, List.of(new Change(property.getColumn(), ChangeType.SET, value)));

            synchronized (this.state(entity.getId())) {
                this.gap(entity.getId(), result.getVersion());

                if (this.accept(entity.getId(), result.getVersion())) {
                    this.assign(entity, property, value);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public long increment(final EntityType entity, final Property<EntityType, Long> property, final long delta) {
        if (property.getConverter() != null) {
            throw new SchemaException("Cannot increment %s, a converted property has no stable unit across instances".formatted(property.getColumn()));
        }

        final Lock lock = this.lock(entity.getId()).readLock();

        lock.lock();

        try {
            final WriteResult result = this.apply(entity, WriteKind.UPDATE, List.of(new Change(property.getColumn(), ChangeType.INCREMENT, delta)));

            synchronized (this.state(entity.getId())) {
                final Long previous = property.get(entity);

                final long updated = result.getValue() == null ? (previous == null ? 0L : previous) + delta : result.getValue();

                if (this.accept(entity.getId(), result.getVersion())) {
                    this.assign(entity, property, updated);
                }

                return updated;
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean decrementIfAtLeast(final EntityType entity, final Property<EntityType, Long> property, final long delta, final long floor) {
        if (property.getConverter() != null) {
            throw new SchemaException("Cannot decrement %s, a converted property has no stable unit across instances".formatted(property.getColumn()));
        }

        if (outOfRange(delta) || outOfRange(floor)) {
            throw new QueryException("Guarded decrement of %s supports values below 2^53".formatted(property.getColumn()));
        }

        final List<Change> guarded = List.of(new Change(property.getColumn(), ChangeType.DECREMENT_IF, new Decrement(delta, floor)));

        final List<Change> persisted = List.of(new Change(property.getColumn(), ChangeType.INCREMENT, -delta));

        final Lock lock = this.lock(entity.getId()).readLock();

        lock.lock();

        try {
            final WriteResult result = this.apply(entity, WriteKind.UPDATE, guarded, persisted);

            if (result.getVersion() == Decrement.REFUSED) {
                return false;
            }

            synchronized (this.state(entity.getId())) {
                final Long previous = property.get(entity);

                final long updated = result.getValue() == null ? (previous == null ? 0L : previous) - delta : result.getValue();

                if (this.accept(entity.getId(), result.getVersion())) {
                    this.assign(entity, property, updated);
                }
            }

            return true;
        } finally {
            lock.unlock();
        }
    }

    public void delete(final EntityType entity) {
        final Lock lock = this.lock(entity.getId()).writeLock();

        lock.lock();

        this.gate.enter();

        try {
            final Write write = new Write(this.key(entity.getId()), WriteKind.DELETE, Collections.emptyList(), Math.max(this.getVersion(entity.getId()), 0L));

            this.authority.apply(write);

            this.persistence.accept(new Write(write.getKey(), WriteKind.DELETE, Collections.emptyList(), 0L));

            this.markDeleted(entity.getId());
        } finally {
            this.gate.exit();

            lock.unlock();
        }
    }

    public long getVersion(final UUID id) {
        final long version = this.versionMap.getOrDefault(id, 0L);

        return this.tracking || version == DELETED ? version : 0L;
    }

    public void setVersion(final UUID id, final long version) {
        if (!this.tracking) {
            return;
        }

        this.versionMap.put(id, version);
    }

    public void clearVersion(final UUID id) {
        this.versionMap.computeIfPresent(id, (_, version) -> version == DELETED ? DELETED : null);
    }

    public void markDeleted(final UUID id) {
        this.versionMap.put(id, DELETED);
    }

    private void copy(final EntityType entity) {
        final EntityType current = this.resolver.apply(entity.getId());

        if (current == null || current == entity) {
            return;
        }

        this.properties.forEach(property -> copy(property, entity, current));
    }

    private <Value> void assign(final EntityType entity, final Property<EntityType, Value> property, final Value value) {
        property.set(entity, value);

        final EntityType current = this.resolver.apply(entity.getId());

        if (current == null || current == entity) {
            return;
        }

        property.set(current, value);
    }

    private boolean accept(final UUID id, final long version) {
        final long current = this.getVersion(id);

        if (current == DELETED || version <= current) {
            return false;
        }

        if (this.held.test(id)) {
            this.versionMap.put(id, version);
        }

        return true;
    }

    public boolean install(final UUID id, final long version) {
        final long current = this.getVersion(id);

        if (current == DELETED || current > version) {
            return false;
        }

        if (version <= 0 || !this.tracking) {
            return true;
        }

        this.versionMap.put(id, version);

        return true;
    }

    public boolean adopt(final UUID id, final long version, final long tracked) {
        final long current = this.getVersion(id);

        if (current == DELETED || current != tracked) {
            return false;
        }

        if (version > 0 && this.tracking) {
            this.versionMap.put(id, version);
        }

        return true;
    }

    public ReentrantReadWriteLock lock(final UUID id) {
        return this.lockList.get(Math.floorMod(id.hashCode(), LOCKS));
    }

    public Object state(final UUID id) {
        return this.stateList.get(Math.floorMod(id.hashCode(), LOCKS));
    }

    private void recreate(final UUID id, final long version) {
        if (this.getVersion(id) == DELETED) {
            this.versionMap.remove(id);
        }

        if (!this.held.test(id)) {
            return;
        }

        this.install(id, version);
    }

    private void gap(final UUID id, final long version) {
        if (version <= 0 || !this.tracking) {
            return;
        }

        final long current = this.getVersion(id);

        if (current == DELETED || version <= current + 1) {
            return;
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Write on {} skipped versions, tracked {}, written {}", id, current, version);
        }

        this.refetch.accept(id);
    }

    private WriteResult apply(final EntityType entity, final WriteKind kind, final List<Change> changes, final List<Change> persisted) {
        this.gate.enter();

        try {
            final long expected = this.getVersion(entity.getId());

            if (expected == DELETED && kind != WriteKind.SAVE) {
                throw new VersionConflictException("%s no longer exists".formatted(entity.getId()));
            }

            final Write write = new Write(this.key(entity.getId()), kind, changes, Math.max(expected, 0L));

            final WriteResult result = this.authority.apply(write);

            final long version = result.getVersion();

            if (version == Decrement.UNSUPPORTED) {
                throw new QueryException("%s holds a value outside the supported guarded range of 2^53".formatted(entity.getId()));
            }

            if (version == Decrement.REFUSED) {
                return result;
            }

            if (version == 0 && guarded(changes)) {
                throw new VersionConflictException("%s is not cached, load it before a guarded decrement".formatted(entity.getId()));
            }

            if (kind != WriteKind.SAVE && version == 0 && (expected > 0 || this.getVersion(entity.getId()) == DELETED)) {
                this.versionMap.put(entity.getId(), DELETED);

                throw new VersionConflictException("%s no longer exists".formatted(entity.getId()));
            }

            if (kind != WriteKind.SAVE && this.getVersion(entity.getId()) == DELETED) {
                throw new VersionConflictException("%s no longer exists".formatted(entity.getId()));
            }

            final List<Change> durable = this.durable(persisted);

            if (!durable.isEmpty()) {
                this.persistence.accept(new Write(write.getKey(), kind, durable, 0L));
            }

            return result;
        } finally {
            this.gate.exit();
        }
    }

    private List<Change> durable(final List<Change> changes) {
        for (final Change change : changes) {
            if (!this.persistent(change.getColumn())) {
                return changes.stream()
                        .filter(entry -> this.persistent(entry.getColumn()))
                        .collect(Collectors.toCollection(ArrayList::new));
            }
        }

        return changes;
    }

    private boolean persistent(final String column) {
        return this.persistentMap.computeIfAbsent(column, name -> this.properties.stream()
                .filter(property -> property.getColumn().equals(name))
                .findFirst()
                .map(Property::isPersistent)
                .orElse(false));
    }

    private WriteResult apply(final EntityType entity, final WriteKind kind, final List<Change> changes) {
        return this.apply(entity, kind, changes, changes);
    }

    private EntityKey key(final UUID id) {
        return new EntityKey(this.table, this.tenant, id);
    }

    private static boolean outOfRange(final long value) {
        return value >= GUARDED_LIMIT || value <= -GUARDED_LIMIT;
    }

    private static boolean guarded(final List<Change> changes) {
        return changes.stream().anyMatch(change -> change.getValue() instanceof Decrement);
    }

    private static <Owner, Value> void copy(final Property<Owner, Value> property, final Object source, final Object target) {
        property.set(property.getEntityType().cast(target), property.get(property.getEntityType().cast(source)));
    }

    private static <Owner, Value> Change change(final Property<Owner, Value> property, final Object entity) {
        return new Change(property.getColumn(), ChangeType.SET, property.get(property.getEntityType().cast(entity)));
    }
}