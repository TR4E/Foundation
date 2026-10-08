package me.trae.foundation.minecraft.paper.addon.item.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.item.services.ItemService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

@AllArgsConstructor
@AddonSingleton
public final class ItemApplyListener implements Listener {

    private final ItemService itemService;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityPickupItem(final EntityPickupItemEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final Item item = event.getItem();

        final ItemStack itemStack = this.itemService.apply(item.getItemStack());

        if (itemStack == null) {
            event.setCancelled(true);
            item.remove();
            return;
        }

        item.setItemStack(itemStack);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPrepareItemCraft(final PrepareItemCraftEvent event) {
        final CraftingInventory craftingInventory = event.getInventory();

        craftingInventory.setResult(this.itemService.apply(craftingInventory.getResult()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFurnaceSmelt(final FurnaceSmeltEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final ItemStack itemStack = this.itemService.apply(event.getResult());

        if (itemStack == null) {
            event.setCancelled(true);
            return;
        }

        event.setResult(itemStack);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        this.itemService.updateInventory(event.getPlayer().getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(final InventoryOpenEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final Inventory topInventory = event.getView().getTopInventory();

        if (!(topInventory.getHolder() instanceof BlockInventoryHolder) && !(topInventory.getHolder() instanceof DoubleChest)) {
            return;
        }

        this.itemService.updateInventory(topInventory);
    }
}