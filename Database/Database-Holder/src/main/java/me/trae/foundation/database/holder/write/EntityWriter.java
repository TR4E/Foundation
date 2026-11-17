package me.trae.foundation.database.holder.write;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.key.EntityKey;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.write.Write;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.change.Change;
import me.trae.foundation.database.api.write.change.ChangeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
public final class EntityWriter<EntityType extends Entity> {

    private final ConcurrentHashMap<UUID, Long> versionMap = new ConcurrentHashMap<>();

    private final String table, tenant;
    private final List<Property<?, ?>> properties;
    private final ToLongFunction<Write> authority;
    private final Consumer<Write> persistence;

    public void save(final EntityType entity) {
        final List<Change> changes = this.properties.stream()
                .filter(Property::isPersistent)
                .map(property -> change(property, entity))
                .collect(Collectors.toCollection(ArrayList::new));

        this.apply(entity, WriteKind.SAVE, changes);
    }

    public <Value> void set(final EntityType entity, final Property<EntityType, Value> property, final Value value) {
        final Value previous = property.get(entity);

        property.set(entity, value);

        try {
            this.apply(entity, WriteKind.UPDATE, List.of(new Change(property.getColumn(), ChangeType.SET, value)));
        } catch (final RuntimeException exception) {
            property.set(entity, previous);

            throw exception;
        }
    }

    public long increment(final EntityType entity, final Property<EntityType, Long> property, final long delta) {
        final long previous = property.get(entity) == null ? 0L : property.get(entity);

        property.set(entity, previous + delta);

        try {
            this.apply(entity, WriteKind.UPDATE, List.of(new Change(property.getColumn(), ChangeType.INCREMENT, delta)));

            return previous + delta;
        } catch (final RuntimeException exception) {
            property.set(entity, previous);

            throw exception;
        }
    }

    public void delete(final EntityType entity) {
        final Write write = new Write(this.key(entity.getId()), WriteKind.DELETE, Collections.emptyList(), this.getVersion(entity.getId()));

        this.authority.applyAsLong(write);
        this.persistence.accept(write);

        this.versionMap.remove(entity.getId());
    }

    public long getVersion(final UUID id) {
        return this.versionMap.getOrDefault(id, 0L);
    }

    public void setVersion(final UUID id, final long version) {
        this.versionMap.put(id, version);
    }

    public void clearVersion(final UUID id) {
        this.versionMap.remove(id);
    }

    private void apply(final EntityType entity, final WriteKind kind, final List<Change> changes) {
        final Write write = new Write(this.key(entity.getId()), kind, changes, this.getVersion(entity.getId()));
        final long version = this.authority.applyAsLong(write);

        if (version > 0) {
            this.versionMap.put(entity.getId(), version);
        }

        this.persistence.accept(write);
    }

    private EntityKey key(final UUID id) {
        return new EntityKey(this.table, this.tenant, id);
    }

    private static <Owner, Value> Change change(final Property<Owner, Value> property, final Object entity) {
        return new Change(property.getColumn(), ChangeType.SET, property.get(property.getEntityType().cast(entity)));
    }
}