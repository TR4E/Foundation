package me.trae.foundation.minecraft.paper.addon.item.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.item.services.ItemService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

import java.util.stream.Stream;

@AllArgsConstructor
@AddonSingleton
public final class ItemStationListener implements Listener {

    private final ItemService itemService;

    private boolean isRefused(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        return this.itemService.getItemByItemStack(itemStack).isPresent();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockCook(final BlockCookEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!this.isRefused(event.getSource())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(final PrepareSmithingEvent event) {
        final SmithingInventory smithingInventory = event.getInventory();

        if (Stream.of(smithingInventory.getInputTemplate(), smithingInventory.getInputEquipment(), smithingInventory.getInputMineral()).noneMatch(this::isRefused)) {
            return;
        }

        event.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBrew(final BrewEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!this.isRefused(event.getContents().getIngredient())) {
            return;
        }

        event.setCancelled(true);
    }
}