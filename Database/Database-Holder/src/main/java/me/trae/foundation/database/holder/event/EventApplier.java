package me.trae.foundation.database.holder.event;

import lombok.AllArgsConstructor;
import lombok.CustomLog;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.event.ChangeEvent;
import me.trae.foundation.database.api.property.Property;
import me.trae.foundation.database.api.storage.Storage;
import me.trae.foundation.database.api.write.WriteKind;
import me.trae.foundation.database.api.write.change.Change;
import me.trae.foundation.database.api.write.change.ChangeType;
import me.trae.foundation.database.holder.write.EntityWriter;
import me.trae.foundation.database.index.IndexUpdater;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

@CustomLog
@AllArgsConstructor
@Getter
public final class EventApplier<EntityType extends Entity> {

    private final ConcurrentHashMap<String, Property<?, ?>> columnMap = new ConcurrentHashMap<>();

    private final List<Property<?, ?>> properties;
    private final Storage<UUID, EntityType> storage;
    private final EntityWriter<EntityType> writer;
    private final IndexUpdater updater;
    private final BiFunction<Property<?, ?>, String, Object> decoder;
    private final Consumer<UUID> refetch, release;

    public void apply(final ChangeEvent event) {
        if (this.storage == null) {
            return;
        }

        synchronized (this.writer.state(event.getKey().getId())) {
            if (!this.storage.contains(event.getKey().getId())) {
                return;
            }

            this.storage.get(event.getKey().getId()).ifPresent(entity -> this.apply(entity, event));
        }
    }

    private void apply(final EntityType entity, final ChangeEvent event) {
        if (event.getKind() == WriteKind.DELETE) {
            this.evict(entity);
            return;
        }

        final long current = this.writer.getVersion(entity.getId());

        if (event.getVersion() <= current) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Event for {} arrived at or below the tracked version, tracked {}, received {}", entity.getId(), current, event.getVersion());
            }

            this.refetch.accept(entity.getId());
            return;
        }

        if (event.getVersion() > current + 1) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Version gap on {}, tracked {}, received {}", entity.getId(), current, event.getVersion());
            }

            this.refetch.accept(entity.getId());
            return;
        }

        this.writer.setVersion(entity.getId(), event.getVersion());

        try {
            event.getChanges().forEach(change -> this.change(entity, change));
        } catch (final RuntimeException exception) {
            this.writer.clearVersion(entity.getId());

            this.refetch.accept(entity.getId());

            throw exception;
        }

        if (!this.storage.contains(entity.getId())) {
            this.writer.clearVersion(entity.getId());

            this.updater.unindex(entity.getId());
            return;
        }

        this.updater.index(entity);
    }

    private void evict(final EntityType entity) {
        this.release.accept(entity.getId());

        this.writer.markDeleted(entity.getId());

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Evicted {} after remote delete", entity.getId());
        }
    }

    private void change(final EntityType entity, final Change change) {
        final Property<?, ?> property = this.property(change.getColumn());

        if (property == null) {
            return;
        }

        this.set(property, entity, change);
    }

    private Property<?, ?> property(final String column) {
        return this.columnMap.computeIfAbsent(column, name -> this.properties.stream()
                .filter(property -> property.getColumn().equals(name))
                .findFirst()
                .orElse(null));
    }

    private <Owner, Value> void set(final Property<Owner, Value> property, final Object entity, final Change change) {
        final Owner owner = property.getEntityType().cast(entity);

        if (change.getType() == ChangeType.INCREMENT) {
            increment(property, owner, change.getValue());
            return;
        }

        if (change.getType() != ChangeType.SET) {
            return;
        }

        property.set(owner, change.getValue() == null ? null : property.getType().cast(this.decoder.apply(property, String.valueOf(change.getValue()))));
    }

    private static <Owner, Value> void increment(final Property<Owner, Value> property, final Owner owner, final Object value) {
        final long current = property.get(owner) == null ? 0L : Long.class.cast(property.get(owner));

        property.set(owner, property.getType().cast(current + Long.parseLong(String.valueOf(value))));
    }
}