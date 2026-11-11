package me.trae.foundation.minecraft.paper.addon.item.listeners.block;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.item.CustomItem;
import me.trae.foundation.minecraft.paper.addon.item.block.CustomBlockData;
import me.trae.foundation.minecraft.paper.addon.item.services.BlockService;
import me.trae.foundation.minecraft.paper.addon.item.services.ItemService;
import me.trae.foundation.minecraft.paper.addon.item.types.PlaceableCustomItem;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilItemStack;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

@AllArgsConstructor
@AddonSingleton
public final class CustomBlockInteractionListener implements Listener {

    private final ItemService itemService;
    private final BlockService blockService;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final CustomItem customItem = this.itemService.getItemByItemStack(event.getItemInHand()).orElse(null);
        if (customItem == null) {
            return;
        }

        if (!(customItem instanceof final PlaceableCustomItem<?> placeableCustomItem)) {
            return;
        }

        final Block block = event.getBlock();

        final Player player = event.getPlayer();

        if (!placeableCustomItem.canPlace(player, block)) {
            event.setCancelled(true);
            return;
        }

        this.blockService.addBlockData(block, placeableCustomItem.createBlockData(player, block));

        this.blockService.writeBlockData(block);

        placeableCustomItem.onPlace(player, block);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(final BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final Block block = event.getBlock();

        final CustomBlockData blockData = this.blockService.getBlockDataByBlock(CustomBlockData.class, block).orElse(null);
        if (blockData == null) {
            return;
        }

        final PlaceableCustomItem<CustomBlockData> placeableCustomItem = this.blockService.getItemByBlock(block).orElse(null);
        if (placeableCustomItem == null) {
            return;
        }

        final Player player = event.getPlayer();

        if (!placeableCustomItem.canBreak(player, block, blockData)) {
            event.setCancelled(true);
            return;
        }

        this.blockService.deleteBlockData(block);

        event.setDropItems(false);

        this.drop(player, block, blockData, placeableCustomItem);

        placeableCustomItem.onBreak(player, block, blockData);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDamage(final BlockDamageEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final Block block = event.getBlock();

        final CustomBlockData blockData = this.blockService.getBlockDataByBlock(CustomBlockData.class, block).orElse(null);
        if (blockData == null) {
            return;
        }

        final PlaceableCustomItem<CustomBlockData> placeableCustomItem = this.blockService.getItemByBlock(block).orElse(null);
        if (placeableCustomItem == null) {
            return;
        }

        final Player player = event.getPlayer();

        final ItemStack itemInHand = event.getItemInHand();

        if (!placeableCustomItem.canDamage(player, block, itemInHand, blockData)) {
            event.setCancelled(true);
            return;
        }

        blockData.setLastDamagedAt(System.currentTimeMillis());

        this.blockService.updateBlockData(block);

        placeableCustomItem.onDamage(player, block, itemInHand, blockData);
    }

    private void drop(final Player player, final Block block, final CustomBlockData blockData, final PlaceableCustomItem<CustomBlockData> placeableCustomItem) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (!placeableCustomItem.canDrop(player, block, blockData)) {
            return;
        }

        if (!placeableCustomItem.getToolRequirement().matches(player, block, UtilItemStack.getItemInMainHand(player).orElse(null))) {
            return;
        }

        final Set<ItemStack> itemStackSet = placeableCustomItem.getDrops(player, block, blockData);
        if (itemStackSet.isEmpty()) {
            return;
        }

        final Location location = block.getLocation().toCenterLocation();

        if (placeableCustomItem.insertOnDrop(player, block, blockData)) {
            itemStackSet.forEach(itemStack -> UtilItemStack.insert(player, itemStack, leftover -> block.getWorld().dropItemNaturally(location, leftover)));
        } else {
            itemStackSet.forEach(itemStack -> block.getWorld().dropItemNaturally(location, itemStack));
        }
    }
}