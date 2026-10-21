package me.trae.foundation.minecraft.paper.addon.item.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.item.CustomItem;
import me.trae.foundation.minecraft.paper.addon.item.events.ItemIngredientEvent;
import me.trae.foundation.minecraft.paper.addon.item.services.ItemService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Crafter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.stream.Stream;

@AllArgsConstructor
@AddonSingleton
public final class ItemCraftListener implements Listener {

    private final ItemService itemService;

    private boolean isRefused(final ItemStack itemStack, final NamespacedKey namespacedKey) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        final CustomItem customItem = this.itemService.getItemByItemStack(itemStack).orElse(null);
        if (customItem == null) {
            return false;
        }

        return !UtilEvent.supply(new ItemIngredientEvent(customItem, itemStack, namespacedKey, false)).isAllowed();
    }

    private NamespacedKey getKeyByRecipe(final Recipe recipe) {
        return recipe instanceof final Keyed keyed ? keyed.getKey() : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemCraft(final PrepareItemCraftEvent event) {
        final NamespacedKey namespacedKey = this.getKeyByRecipe(event.getRecipe());

        if (Stream.of(event.getInventory().getMatrix()).noneMatch(itemStack -> this.isRefused(itemStack, namespacedKey))) {
            return;
        }

        event.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraftItem(final CraftItemEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final NamespacedKey namespacedKey = this.getKeyByRecipe(event.getRecipe());

        if (Stream.of(event.getInventory().getMatrix()).noneMatch(itemStack -> this.isRefused(itemStack, namespacedKey))) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCrafterCraft(final CrafterCraftEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!(event.getBlock().getState(false) instanceof final Crafter crafter)) {
            return;
        }

        final NamespacedKey namespacedKey = this.getKeyByRecipe(event.getRecipe());

        if (Stream.of(crafter.getInventory().getContents()).noneMatch(itemStack -> this.isRefused(itemStack, namespacedKey))) {
            return;
        }

        event.setCancelled(true);
    }
}