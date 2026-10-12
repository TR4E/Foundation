package me.trae.foundation.database.core.holder;

import lombok.AllArgsConstructor;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.DatabaseException;
import me.trae.foundation.database.api.holder.InstanceMode;
import me.trae.foundation.database.core.batch.BatchQueue;
import me.trae.foundation.database.storage.local.LocalStorage;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
public final class HolderCache<E extends Entity> {

    private final InstanceMode instanceMode;
    private final HolderComponents<E> holderComponents;
    private final BatchQueue batchQueue;

    public List<E> getPinned() {
        final LocalStorage<UUID, E> localStorage = this.holderComponents.getLocalStorage();

        return localStorage == null ? Collections.emptyList() : localStorage.getPinnedValues();
    }

    public void pin(final E entity) {
        final LocalStorage<UUID, E> localStorage = this.requireLocal("pin");

        this.holderComponents.getLocalEntityIndex().pin(entity, localStorage);

        this.track(entity);
    }

    public void unpin(final E entity) {
        final LocalStorage<UUID, E> localStorage = this.requireLocal("unpin");
        final E cachedEntity = localStorage.get(entity.getId()).orElse(null);

        if (cachedEntity == entity) {
            this.holderComponents.getHolderWriter().writeChanged(entity);
        }

        localStorage.unpin(entity.getId());
    }

    public void cache(final E entity) {
        if (this.holderComponents.getLocalStorage() != null) {
            this.holderComponents.getLocalEntityIndex().put(entity, this.holderComponents.getLocalStorage());
        }

        this.track(entity);
    }

    public void evict(final E entity) {
        if (this.holderComponents.getLocalStorage() != null) {
            this.holderComponents.getHolderWriter().writeChanged(entity);

            this.holderComponents.getLocalEntityIndex().remove(entity.getId(), this.holderComponents.getLocalStorage());
        }

        this.holderComponents.getChangeTracker().forget(entity.getId());
    }

    public void track(final E entity) {
        this.holderComponents.getChangeTracker().snapshotIfAbsent(entity);
    }

    public void flushAndEvict() {
        final LocalStorage<UUID, E> localStorage = this.holderComponents.getLocalStorage();

        if (localStorage != null) {
            localStorage.getValues().forEach(this::writeSafely);

            localStorage.evictExpired().forEach(this::writeAndForget);
        }

        this.holderComponents.getChangeTracker().evictExpired();
    }

    private void writeSafely(final E entity) {
        try {
            this.holderComponents.getHolderWriter().writeChanged(entity);
        } catch (final RuntimeException exception) {
            this.batchQueue.reportFailure(new DatabaseException("Failed to write changes for %s".formatted(entity.getId()), exception));
        }
    }

    private void writeAndForget(final E entity) {
        this.writeSafely(entity);
        this.holderComponents.getLocalEntityIndex().evict(entity.getId(), this.holderComponents.getLocalStorage());
        this.holderComponents.getChangeTracker().forget(entity.getId());
    }

    private LocalStorage<UUID, E> requireLocal(final String action) {
        final LocalStorage<UUID, E> localStorage = this.holderComponents.getLocalStorage();

        if (localStorage == null) {
            throw new UnsupportedOperationException("Cannot %s on a %s holder".formatted(action, this.instanceMode));
        }

        return localStorage;
    }
}