package me.trae.foundation.minecraft.paper.addon.item.listeners.block;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.item.block.CustomBlockData;
import me.trae.foundation.minecraft.paper.addon.item.enums.BlockRemoveReason;
import me.trae.foundation.minecraft.paper.addon.item.services.BlockService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.List;

@AllArgsConstructor
@AddonSingleton
public final class CustomBlockRemovalListener implements Listener {

    private final BlockService blockService;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBurn(final BlockBurnEvent event) {
        this.handle(event, BlockRemoveReason.BURN);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockFade(final BlockFadeEvent event) {
        this.handle(event, BlockRemoveReason.FADE);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeavesDecay(final LeavesDecayEvent event) {
        this.handle(event, BlockRemoveReason.DECAY);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockFromTo(final BlockFromToEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final Block block = event.getToBlock();

        if (!this.canRemove(block, BlockRemoveReason.LIQUID)) {
            event.setCancelled(true);
            return;
        }

        this.removeBlock(block, BlockRemoveReason.LIQUID);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityChangeBlock(final EntityChangeBlockEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final Block block = event.getBlock();

        if (!this.canRemove(block, BlockRemoveReason.ENTITY)) {
            event.setCancelled(true);
            return;
        }

        this.removeBlock(block, BlockRemoveReason.ENTITY);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(final EntityExplodeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        this.removeBlockList(event.blockList(), BlockRemoveReason.EXPLOSION);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(final BlockExplodeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        this.removeBlockList(event.blockList(), BlockRemoveReason.EXPLOSION);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStructureGrow(final StructureGrowEvent event) {
        if (event.isCancelled()) {
            return;
        }

        this.removeBlockStateList(event.getBlocks(), BlockRemoveReason.GROWTH);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPistonExtend(final BlockPistonExtendEvent event) {
        this.handlePiston(event, event.getBlocks());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPistonRetract(final BlockPistonRetractEvent event) {
        this.handlePiston(event, event.getBlocks());
    }

    private <Event extends BlockEvent & Cancellable> void handle(final Event event, final BlockRemoveReason removeReason) {
        if (event.isCancelled()) {
            return;
        }

        final Block block = event.getBlock();

        if (!this.canRemove(block, removeReason)) {
            event.setCancelled(true);
            return;
        }

        this.removeBlock(block, removeReason);
    }

    private void handlePiston(final Cancellable event, final List<Block> blockList) {
        if (event.isCancelled()) {
            return;
        }

        if (blockList.stream().noneMatch(block -> this.blockService.getBlockDataByBlock(CustomBlockData.class, block).isPresent())) {
            return;
        }

        event.setCancelled(true);
    }

    private boolean canRemove(final Block block, final BlockRemoveReason removeReason) {
        final CustomBlockData blockData = this.blockService.getBlockDataByBlock(CustomBlockData.class, block).orElse(null);
        if (blockData == null) {
            return true;
        }

        return this.blockService.getItemByBlock(block).map(customBlock -> customBlock.canRemove(block, blockData, removeReason)).orElse(true);
    }

    private void removeBlock(final Block block, final BlockRemoveReason removeReason) {
        final CustomBlockData blockData = this.blockService.getBlockDataByBlock(CustomBlockData.class, block).orElse(null);
        if (blockData == null) {
            return;
        }

        this.blockService.getItemByBlock(block).ifPresent(customBlock -> customBlock.onRemove(block, blockData, removeReason));

        this.blockService.deleteBlockData(block);
    }

    private void removeBlockList(final List<Block> blockList, final BlockRemoveReason removeReason) {
        blockList.removeIf(block -> !this.canRemove(block, removeReason));

        blockList.forEach(block -> this.removeBlock(block, removeReason));
    }

    private void removeBlockStateList(final List<BlockState> blockStateList, final BlockRemoveReason removeReason) {
        blockStateList.removeIf(blockState -> !this.canRemove(blockState.getBlock(), removeReason));

        blockStateList.forEach(blockState -> this.removeBlock(blockState.getBlock(), removeReason));
    }
}