package me.trae.foundation.minecraft.paper.addon.recipe.listeners;

import me.trae.foundation.minecraft.paper.addon.recipe.services.RecipeService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

@AddonSingleton
public final class RecipeDisabledPlaceholderListener implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!this.isDisabled(event.getCurrentItem()) && !this.isDisabled(event.getCursor()) && !this.isDisabled(this.getSwapItem(event))) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!this.isDisabled(event.getOldCursor()) && event.getNewItems().values().stream().noneMatch(this::isDisabled)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryMoveItem(final InventoryMoveItemEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!this.isDisabled(event.getItem())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCrafterCraft(final CrafterCraftEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!this.isDisabled(event.getResult())) {
            return;
        }

        event.setCancelled(true);
    }

    private ItemStack getSwapItem(final InventoryClickEvent event) {
        final PlayerInventory playerInventory = event.getWhoClicked().getInventory();

        return switch (event.getClick()) {
            case NUMBER_KEY -> playerInventory.getItem(event.getHotbarButton());
            case SWAP_OFFHAND -> playerInventory.getItemInOffHand();
            default -> null;
        };
    }

    private boolean isDisabled(final ItemStack itemStack) {
        return UtilItemStack.getPersistentData(itemStack, RecipeService.DISABLED_RECIPE_KEY, PersistentDataType.BOOLEAN).isPresent();
    }
}