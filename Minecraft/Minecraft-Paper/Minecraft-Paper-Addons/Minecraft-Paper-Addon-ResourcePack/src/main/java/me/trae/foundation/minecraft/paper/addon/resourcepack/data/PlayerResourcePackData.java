package me.trae.foundation.minecraft.paper.addon.resourcepack.data;

import me.trae.foundation.minecraft.paper.addon.resourcepack.ResourcePack;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerResourcePackData {

    private final ConcurrentHashMap<UUID, String> loadedMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> pendingMap = new ConcurrentHashMap<>();

    public void addLoaded(final ResourcePack resourcePack) {
        this.loadedMap.put(resourcePack.getUniqueId(), resourcePack.getHash());
    }

    public void removeLoaded(final ResourcePack resourcePack) {
        this.loadedMap.remove(resourcePack.getUniqueId());
    }

    public void addPending(final ResourcePack resourcePack) {
        this.pendingMap.put(resourcePack.getUniqueId(), resourcePack.getHash());
    }

    public void removePending(final ResourcePack resourcePack) {
        this.pendingMap.remove(resourcePack.getUniqueId());
    }

    public boolean isLoaded(final ResourcePack resourcePack) {
        return this.loadedMap.containsKey(resourcePack.getUniqueId());
    }

    public boolean isPending(final ResourcePack resourcePack) {
        return this.pendingMap.containsKey(resourcePack.getUniqueId());
    }

    public boolean isAnyLoaded() {
        return !this.loadedMap.isEmpty();
    }

    public boolean isAnyPending() {
        return !this.pendingMap.isEmpty();
    }

    public boolean isCurrent(final ResourcePack resourcePack) {
        return resourcePack.getHash().equals(this.loadedMap.getOrDefault(resourcePack.getUniqueId(), this.pendingMap.get(resourcePack.getUniqueId())));
    }
}