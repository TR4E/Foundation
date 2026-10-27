package me.trae.foundation.minecraft.paper.addon.window.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.window.Window;
import me.trae.foundation.minecraft.paper.addon.window.services.WindowService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

@AllArgsConstructor
@AddonSingleton
public final class WindowOpenListener implements Listener {

    private final WindowService windowService;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(final InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof final Player player)) {
            return;
        }

        final Inventory inventory = event.getInventory();

        if (!(inventory.getHolder() instanceof final Window<?> window)) {
            return;
        }

        this.windowService.getWindowByPlayerMap().put(player.getUniqueId(), window);
        this.windowService.getWindowByInventoryMap().put(inventory, window);

        if (this.windowService.getReopeningMap().get(player.getUniqueId()) == inventory) {
            this.windowService.getReopeningMap().remove(player.getUniqueId());
            return;
        }

        window.onOpen(player);
    }
}