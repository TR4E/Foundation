package me.trae.foundation.minecraft.paper.addon.item.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.item.services.ItemService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
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
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

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

        final ItemStack itemStack = this.apply(item.getItemStack());

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

        craftingInventory.setResult(this.apply(craftingInventory.getResult()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFurnaceSmelt(final FurnaceSmeltEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final ItemStack itemStack = this.apply(event.getResult());

        if (itemStack == null) {
            event.setCancelled(true);
            return;
        }

        event.setResult(itemStack);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        this.updateInventory(event.getPlayer().getInventory());
        this.updateInventory(event.getPlayer().getEnderChest());
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

        this.updateInventory(topInventory);
    }

    private void updateInventory(final Inventory inventory) {
        final ItemStack[] contents = inventory.getContents();

        for (int i = 0; i < contents.length; i++) {
            final ItemStack existingItemStack = contents[i];
            final ItemStack updatedItemStack = this.apply(existingItemStack);

            if (updatedItemStack != existingItemStack) {
                inventory.setItem(i, updatedItemStack);
            }
        }
    }

    private ItemStack apply(final ItemStack itemStack) {
        final ItemStack updatedItemStack = this.itemService.apply(itemStack);

        this.applyContents(updatedItemStack);

        return updatedItemStack;
    }

    private void applyContents(final ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        itemStack.editMeta(itemMeta -> {
            if (itemMeta instanceof final BlockStateMeta blockStateMeta) {
                this.applyBlockState(blockStateMeta);
                return;
            }

            if (itemMeta instanceof final BundleMeta bundleMeta) {
                this.applyBundle(bundleMeta);
            }
        });
    }

    private void applyBlockState(final BlockStateMeta blockStateMeta) {
        if (!blockStateMeta.hasBlockState()) {
            return;
        }

        final BlockState blockState = blockStateMeta.getBlockState();

        if (!(blockState instanceof final Container container)) {
            return;
        }

        final Inventory inventory = container.getInventory();

        final ItemStack[] contents = inventory.getContents();

        for (int i = 0; i < contents.length; i++) {
            inventory.setItem(i, this.itemService.apply(contents[i]));
        }

        blockStateMeta.setBlockState(blockState);
    }

    private void applyBundle(final BundleMeta bundleMeta) {
        if (!bundleMeta.hasItems()) {
            return;
        }

        bundleMeta.setItems(bundleMeta.getItems().stream()
                .map(this.itemService::apply)
                .filter(itemStack -> itemStack != null && !itemStack.isEmpty())
                .toList()
        );
    }
}