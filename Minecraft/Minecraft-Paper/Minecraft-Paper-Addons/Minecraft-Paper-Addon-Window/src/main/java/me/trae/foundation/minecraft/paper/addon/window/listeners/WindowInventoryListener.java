package me.trae.foundation.minecraft.paper.addon.window.listeners;

import me.trae.foundation.minecraft.paper.addon.window.Window;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

@AddonSingleton
public final class WindowInventoryListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryCreative(final InventoryCreativeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof final Player player)) {
            return;
        }

        if (!(event.getInventory().getHolder() instanceof Window<?>)) {
            return;
        }

        event.setCancelled(true);

        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!(event.getInventory().getHolder() instanceof Window<?>)) {
            return;
        }

        event.setCancelled(true);
    }
}