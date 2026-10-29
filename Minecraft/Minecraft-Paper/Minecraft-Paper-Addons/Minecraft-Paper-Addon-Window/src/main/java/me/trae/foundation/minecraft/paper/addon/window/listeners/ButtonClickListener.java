package me.trae.foundation.minecraft.paper.addon.window.listeners;

import me.trae.foundation.minecraft.paper.addon.window.Window;
import me.trae.foundation.minecraft.paper.addon.window.element.Button;
import me.trae.foundation.minecraft.paper.addon.window.events.ButtonPostClickEvent;
import me.trae.foundation.minecraft.paper.addon.window.events.ButtonPreClickEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.utilities.UtilJava;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.inventory.Inventory;

@AddonSingleton
public final class ButtonClickListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (event instanceof InventoryCreativeEvent) {
            return;
        }

        if (!(event.getWhoClicked() instanceof final Player player)) {
            return;
        }

        final Inventory inventory = event.getInventory();

        if (!(inventory.getHolder() instanceof final Window<?> window)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() != inventory) {
            return;
        }

        if (window.isRendering()) {
            return;
        }

        if (!(window.getLayout().getElementBySlot(event.getSlot()).orElse(null) instanceof final Button<?> button)) {
            return;
        }

        final ClickType clickType = event.getClick();

        if (UtilEvent.supply(new ButtonPreClickEvent(window, button, player, clickType)).isCancelled() || !button.canClick(player, clickType)) {
            return;
        }

        button.onClick(player, clickType);

        UtilJava.runIfNotNull(button.getClickSound(player, clickType), soundStash -> soundStash.play(player));

        UtilEvent.dispatch(new ButtonPostClickEvent(window, button, player, clickType));
    }
}