package me.trae.foundation.minecraft.paper.addon.window.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.window.Window;
import me.trae.foundation.minecraft.paper.addon.window.services.WindowService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.utilities.UtilJava;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

@AllArgsConstructor
@AddonSingleton
public final class WindowOpenListener implements Listener {

    private final WindowService windowService;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(final InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof final Player player)) {
            return;
        }

        final UUID viewerId = player.getUniqueId();

        final Inventory inventory = event.getInventory();

        final boolean expected = this.windowService.getInventoryChangeMap().get(viewerId) == inventory;

        final boolean reopening = expected && this.windowService.getReopeningMap().get(viewerId) == inventory;

        if (!expected) {
            this.windowService.cancelRequests(viewerId);
        }

        if (!(inventory.getHolder() instanceof final Window<?> window)) {
            return;
        }

        this.windowService.getWindowByPlayerMap().put(viewerId, window);
        this.windowService.getWindowByInventoryMap().put(inventory, window);

        if (reopening) {
            this.windowService.getReopeningMap().remove(viewerId, inventory);
            return;
        }

        UtilJava.runIfNotNull(window.getOpenSoundStash(), soundStash -> soundStash.play(player));

        window.onOpen(player);
    }
}