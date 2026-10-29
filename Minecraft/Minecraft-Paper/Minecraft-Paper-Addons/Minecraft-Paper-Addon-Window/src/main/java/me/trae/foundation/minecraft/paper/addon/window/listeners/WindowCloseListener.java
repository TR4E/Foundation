package me.trae.foundation.minecraft.paper.addon.window.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.window.Window;
import me.trae.foundation.minecraft.paper.addon.window.events.WindowCloseEvent;
import me.trae.foundation.minecraft.paper.addon.window.services.WindowService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilTask;
import me.trae.foundation.utilities.UtilJava;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

@AllArgsConstructor
@AddonSingleton
public final class WindowCloseListener implements Listener {

    private final WindowService windowService;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof final Player player)) {
            return;
        }

        final UUID uniqueId = player.getUniqueId();

        if (!this.windowService.getInventoryChangeMap().containsKey(uniqueId)) {
            this.windowService.cancelRequests(uniqueId);
        }

        final Inventory inventory = event.getInventory();

        if (!(inventory.getHolder() instanceof final Window<?> window)) {
            return;
        }

        this.windowService.getWindowByPlayerMap().remove(uniqueId, window);
        this.windowService.getWindowByInventoryMap().remove(inventory, window);
        this.windowService.getWindowLastScheduleRefreshedAtMap().remove(window);

        if (this.isVetoable(event.getReason()) && (UtilEvent.supply(new WindowCloseEvent(window, player)).isCancelled() || !window.canClose(player))) {
            if (this.windowService.getRequestIdMap().containsKey(uniqueId)) {
                return;
            }

            final UUID requestId = this.windowService.beginRequest(uniqueId);

            this.windowService.getReopeningMap().put(uniqueId, inventory);

            try {
                UtilTask.executeLaterSynchronously(50, ChronoUnit.MILLIS, () -> {
                    if (!this.windowService.isCurrentRequest(uniqueId, requestId) || this.windowService.getReopeningMap().get(uniqueId) != inventory) {
                        return;
                    }

                    try {
                        UtilServer.getOnlinePlayerById(uniqueId).ifPresent(value -> {
                            if (value.getOpenInventory().getType() == InventoryType.CRAFTING) {
                                this.windowService.openInventory(uniqueId, requestId, inventory);
                            }
                        });
                    } finally {
                        this.windowService.completeRequest(uniqueId, requestId);
                    }
                });
            } catch (final RuntimeException | Error throwable) {
                this.windowService.completeRequest(uniqueId, requestId);

                throw throwable;
            }
            return;
        }

        UtilJava.runIfNotNull(window.getCloseSoundStash(), soundStash -> soundStash.play(player));

        window.onClose(player);
    }

    private boolean isVetoable(final InventoryCloseEvent.Reason reason) {
        if (reason == InventoryCloseEvent.Reason.PLAYER) {
            return true;
        }

        if (reason == InventoryCloseEvent.Reason.PLUGIN) {
            return true;
        }

        return false;
    }
}