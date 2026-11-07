package me.trae.foundation.minecraft.paper.addon.resourcepack.data;

import me.trae.foundation.minecraft.paper.addon.resourcepack.ResourcePack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerResourcePackData {

    private final Set<UUID> loadedSet = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingSet = ConcurrentHashMap.newKeySet();

    public void addLoaded(final ResourcePack resourcePack) {
        this.loadedSet.add(resourcePack.getUniqueId());
    }

    public void removeLoaded(final ResourcePack resourcePack) {
        this.loadedSet.remove(resourcePack.getUniqueId());
    }

    public void addPending(final ResourcePack resourcePack) {
        this.pendingSet.add(resourcePack.getUniqueId());
    }

    public void removePending(final ResourcePack resourcePack) {
        this.pendingSet.remove(resourcePack.getUniqueId());
    }

    public boolean isLoaded(final ResourcePack resourcePack) {
        return this.loadedSet.contains(resourcePack.getUniqueId());
    }

    public boolean isPending(final ResourcePack resourcePack) {
        return this.pendingSet.contains(resourcePack.getUniqueId());
    }

    public boolean isAnyLoaded() {
        return !this.loadedSet.isEmpty();
    }

    public boolean isAnyPending() {
        return !this.pendingSet.isEmpty();
    }
}