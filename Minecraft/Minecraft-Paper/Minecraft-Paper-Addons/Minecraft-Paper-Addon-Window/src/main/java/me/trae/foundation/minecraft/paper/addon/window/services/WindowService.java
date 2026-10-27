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

    public void closeAll() {
        this.reopeningMap.clear();

        List.copyOf(this.windowByPlayerMap.keySet()).forEach(uuid -> UtilServer.getOnlinePlayerById(uuid).ifPresent(value -> value.closeInventory(InventoryCloseEvent.Reason.CANT_USE)));

        this.windowByPlayerMap.clear();
        this.windowByInventoryMap.clear();
        this.windowLastScheduleRefreshedAtMap.clear();
    }

    public void cleanup(final Player player) {
        final Window<?> window = this.windowByPlayerMap.remove(player.getUniqueId());

        if (window != null) {
            this.windowByInventoryMap.remove(window.getInventory());
            this.windowLastScheduleRefreshedAtMap.remove(window);
        }

        this.reopeningMap.remove(player.getUniqueId());
    }
}