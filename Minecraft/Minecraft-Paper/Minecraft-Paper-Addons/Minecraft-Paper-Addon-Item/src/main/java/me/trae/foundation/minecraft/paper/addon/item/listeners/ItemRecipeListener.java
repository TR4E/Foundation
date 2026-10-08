package me.trae.foundation.minecraft.paper.addon.item.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.item.services.ItemService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.block.Crafter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

import java.util.stream.Stream;

@AllArgsConstructor
@AddonSingleton
public final class ItemRecipeListener implements Listener {

    private final ItemService itemService;

    private boolean isRefused(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        return this.itemService.getItemByItemStack(itemStack)
                .map(customItem -> !customItem.usableInRecipes())
                .orElse(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemCraft(final PrepareItemCraftEvent event) {
        if (Stream.of(event.getInventory().getMatrix()).anyMatch(this::isRefused)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftItem(final CraftItemEvent event) {
        if (Stream.of(event.getInventory().getMatrix()).anyMatch(this::isRefused)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafterCraft(final CrafterCraftEvent event) {
        if (event.getBlock().getState(false) instanceof final Crafter crafter && Stream.of(crafter.getInventory().getContents()).anyMatch(this::isRefused)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockCook(final BlockCookEvent event) {
        if (this.isRefused(event.getSource())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(final PrepareSmithingEvent event) {
        final SmithingInventory smithingInventory = event.getInventory();

        if (Stream.of(smithingInventory.getInputTemplate(), smithingInventory.getInputEquipment(), smithingInventory.getInputMineral()).anyMatch(this::isRefused)) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrew(final BrewEvent event) {
        if (this.isRefused(event.getContents().getIngredient())) {
            event.setCancelled(true);
        }
    }
}