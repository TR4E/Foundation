package me.trae.foundation.database.holder.event;

import lombok.AllArgsConstructor;
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
import java.util.function.BiFunction;
import java.util.function.Consumer;

@AllArgsConstructor
@Getter
public final class EventApplier<EntityType extends Entity> {

    private final List<Property<?, ?>> properties;
    private final Storage<UUID, EntityType> storage;
    private final EntityWriter<EntityType> writer;
    private final IndexUpdater updater;
    private final BiFunction<Property<?, ?>, String, Object> decoder;
    private final Consumer<UUID> refetch;

    public void apply(final ChangeEvent event) {
        if (this.storage == null) {
            return;
        }

        this.storage.get(event.getKey().getId()).ifPresent(entity -> this.apply(entity, event));
    }

    private void apply(final EntityType entity, final ChangeEvent event) {
        if (event.getKind() == WriteKind.DELETE) {
            this.evict(entity);
            return;
        }

        final long current = this.writer.getVersion(entity.getId());

        if (event.getVersion() <= current) {
            return;
        }

        if (event.getVersion() > current + 1) {
            this.refetch.accept(entity.getId());
            return;
        }

        event.getChanges().forEach(change -> this.change(entity, change));

        this.writer.setVersion(entity.getId(), event.getVersion());

        this.updater.index(entity);
    }

    private void evict(final EntityType entity) {
        this.updater.unindex(entity.getId());
        this.storage.remove(entity.getId());
        this.writer.clearVersion(entity.getId());
    }

    private void change(final EntityType entity, final Change change) {
        this.properties.stream()
                .filter(property -> property.getColumn().equals(change.getColumn()))
                .findFirst()
                .ifPresent(property -> this.set(property, entity, change));
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