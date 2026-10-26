package me.trae.foundation.minecraft.paper.addon.recipe.listeners;

import me.trae.foundation.minecraft.paper.addon.recipe.services.RecipeService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

@AddonSingleton
public final class RecipeDisabledPlaceholderListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final InventoryType inventoryType = event.getInventory().getType();

        if (inventoryType != InventoryType.CRAFTING && inventoryType != InventoryType.WORKBENCH && inventoryType != InventoryType.CRAFTER) {
            return;
        }

        if (event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }

        if (!this.isDisabled(event.getCurrentItem())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraftItem(final CraftItemEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!this.isDisabled(event.getCurrentItem())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCrafterCraft(final CrafterCraftEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!this.isDisabled(event.getResult())) {
            return;
        }

        event.setCancelled(true);
    }

    private boolean isDisabled(final ItemStack itemStack) {
        return UtilItemStack.getPersistentData(itemStack, RecipeService.DISABLED_RECIPE_KEY, PersistentDataType.BOOLEAN).isPresent();
    }
}