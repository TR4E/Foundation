package me.trae.foundation.minecraft.paper.addon.window.services;

import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.window.Window;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@AddonSingleton
public final class WindowService {

    private final ConcurrentHashMap<UUID, Window<?>> windowByPlayerMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Inventory, Window<?>> windowByInventoryMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Window<?>, Long> windowLastScheduleRefreshedAtMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<UUID, Inventory> reopeningMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> requestIdMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Inventory> inventoryChangeMap = new ConcurrentHashMap<>();

    private boolean closingAll;

    public void closeAll() {
        if (this.closingAll) {
            return;
        }

        this.closingAll = true;

        this.requestIdMap.clear();
        this.reopeningMap.clear();

        try {
            for (final Window<?> window : List.copyOf(this.windowByPlayerMap.values())) {
                window.getViewer().ifPresent(player -> {
                    this.closeInventory(player, window.getInventory(), InventoryCloseEvent.Reason.CANT_USE);
                });
            }
        } finally {
            this.windowByPlayerMap.clear();
            this.windowByInventoryMap.clear();

            this.windowLastScheduleRefreshedAtMap.clear();

            this.requestIdMap.clear();
            this.reopeningMap.clear();

            this.closingAll = false;
        }
    }

    public void cleanup(final Player player) {
        final Window<?> window = this.windowByPlayerMap.remove(player.getUniqueId());

        if (window != null) {
            this.windowByInventoryMap.remove(window.getInventory());

            this.windowLastScheduleRefreshedAtMap.remove(window);
        }

        this.cancelRequests(player.getUniqueId());
    }

    public UUID beginRequest(final UUID viewerId) {
        final UUID requestId = UUID.randomUUID();

        this.requestIdMap.put(viewerId, requestId);

        this.reopeningMap.remove(viewerId);

        return requestId;
    }

    public boolean isCurrentRequest(final UUID viewerId, final UUID requestId) {
        return requestId.equals(this.requestIdMap.get(viewerId));
    }

    public void completeRequest(final UUID viewerId, final UUID requestId) {
        if (this.requestIdMap.remove(viewerId, requestId)) {
            this.reopeningMap.remove(viewerId);
        }
    }

    public void cancelRequests(final UUID viewerId) {
        this.requestIdMap.remove(viewerId);

        this.reopeningMap.remove(viewerId);
    }

    public void openInventory(final UUID viewerId, final UUID requestId, final Inventory inventory) {
        if (!this.isCurrentRequest(viewerId, requestId)) {
            return;
        }

        final Player viewer = UtilServer.getOnlinePlayerById(viewerId).orElse(null);

        if (viewer == null || viewer.isDead()) {
            this.completeRequest(viewerId, requestId);
            return;
        }

        this.inventoryChangeMap.put(viewerId, inventory);

        try {
            viewer.openInventory(inventory);

            if (!this.isCurrentRequest(viewerId, requestId) && !this.requestIdMap.containsKey(viewerId)) {
                this.closeInventory(viewer, inventory, InventoryCloseEvent.Reason.CANT_USE);
            }
        } finally {
            this.inventoryChangeMap.remove(viewerId, inventory);

            this.completeRequest(viewerId, requestId);
        }
    }

    public void closeInventory(final Player player, final Inventory inventory, final InventoryCloseEvent.Reason reason) {
        if (player.getOpenInventory().getTopInventory() != inventory) {
            return;
        }

        final UUID viewerId = player.getUniqueId();

        this.inventoryChangeMap.put(viewerId, inventory);

        try {
            player.closeInventory(reason);
        } finally {
            this.inventoryChangeMap.remove(viewerId, inventory);
        }
    }
}