package me.trae.foundation.minecraft.paper.addon.item.listeners.block;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.item.CustomBlock;
import me.trae.foundation.minecraft.paper.addon.item.CustomItem;
import me.trae.foundation.minecraft.paper.addon.item.block.CustomBlockData;
import me.trae.foundation.minecraft.paper.addon.item.enums.InteractType;
import me.trae.foundation.minecraft.paper.addon.item.services.BlockService;
import me.trae.foundation.minecraft.paper.addon.item.services.ItemService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilItemStack;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilMaterial;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
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

        if (!(customItem instanceof final CustomBlock<?> customBlock)) {
            return;
        }

        final Block block = event.getBlock();

        final Player player = event.getPlayer();

        if (!customBlock.canPlace(player, block)) {
            event.setCancelled(true);
            return;
        }

        this.blockService.addBlockData(block, customBlock.createBlockData(player, block));

        this.blockService.writeBlockData(block);

        customBlock.onPlace(player, block);
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

        final CustomBlock<CustomBlockData> customBlock = this.blockService.getItemByBlock(block).orElse(null);
        if (customBlock == null) {
            return;
        }

        final Player player = event.getPlayer();

        if (!customBlock.canBreak(player, block, blockData)) {
            event.setCancelled(true);
            return;
        }

        this.blockService.deleteBlockData(block);

        event.setDropItems(false);

        this.drop(player, block, blockData, customBlock);

        customBlock.onBreak(player, block, blockData);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        final Block clickedBlock = event.getClickedBlock();

        if (clickedBlock == null || clickedBlock.isEmpty()) {
            return;
        }

        final InteractType interactType = InteractType.getByAction(event.getAction()).orElse(null);
        if (interactType == null) {
            return;
        }

        final CustomBlockData blockData = this.blockService.getBlockDataByBlock(CustomBlockData.class, clickedBlock).orElse(null);
        if (blockData == null) {
            return;
        }

        final CustomBlock<CustomBlockData> customBlock = this.blockService.getItemByBlock(clickedBlock).orElse(null);
        if (customBlock == null) {
            return;
        }

        final Player player = event.getPlayer();

        final ItemStack itemStack = event.getItem();

        if (UtilMaterial.isInteractableByHand(clickedBlock.getType(), itemStack)) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }

        if (!customBlock.canInteract(player, clickedBlock, itemStack, interactType, blockData)) {
            return;
        }

        customBlock.onInteract(player, clickedBlock, itemStack, interactType, blockData);
    }

    private void drop(final Player player, final Block block, final CustomBlockData blockData, final CustomBlock<CustomBlockData> customBlock) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (!customBlock.canDrop(player, block, blockData)) {
            return;
        }

        if (!customBlock.getToolRequirement().matches(player, block, UtilItemStack.getItemInMainHand(player).orElse(null))) {
            return;
        }

        final Set<ItemStack> itemStackSet = customBlock.getDrops(player, block, blockData);
        if (itemStackSet.isEmpty()) {
            return;
        }

        final Location location = block.getLocation().toCenterLocation();

        if (customBlock.insertOnDrop(player, block, blockData)) {
            itemStackSet.forEach(itemStack -> UtilItemStack.insert(player, itemStack, leftover -> block.getWorld().dropItemNaturally(location, leftover)));
        } else {
            itemStackSet.forEach(itemStack -> block.getWorld().dropItemNaturally(location, itemStack));
        }
    }
}