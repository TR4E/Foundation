package me.trae.foundation.minecraft.paper.addon.item.listeners.block;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.item.CustomBlock;
import me.trae.foundation.minecraft.paper.addon.item.block.CustomBlockData;
import me.trae.foundation.minecraft.paper.addon.item.services.BlockService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;

@AllArgsConstructor
@AddonSingleton
public final class CustomBlockChunkListener implements Listener {

    private final BlockService blockService;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(final ChunkLoadEvent event) {
        if (event.isNewChunk()) {
            return;
        }

        final Chunk chunk = event.getChunk();

        final PersistentDataContainer blockContainer = this.blockService.getBlockContainerByChunk(chunk).orElse(null);
        if (blockContainer == null) {
            return;
        }

        for (final NamespacedKey positionKey : blockContainer.getKeys()) {
            this.loadBlockData(chunk, blockContainer, positionKey);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(final ChunkUnloadEvent event) {
        this.blockService.unloadChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(final WorldUnloadEvent event) {
        if (event.isCancelled()) {
            return;
        }

        this.blockService.unloadWorld(event.getWorld());
    }

    private void loadBlockData(final Chunk chunk, final PersistentDataContainer blockContainer, final NamespacedKey positionKey) {
        final PersistentDataContainer positionContainer = this.blockService.getPositionContainer(blockContainer, positionKey).orElse(null);
        if (positionContainer == null) {
            return;
        }

        final Block block = this.blockService.getBlockByPositionKey(chunk, positionKey).orElse(null);
        if (block == null) {
            return;
        }

        final CustomBlock<?> customBlock = CustomBlockData.getItemUniqueId(positionContainer).flatMap(this.blockService.getItemService()::getItemByUniqueId).filter(CustomBlock.class::isInstance).map(CustomBlock.class::cast).orElse(null);
        if (customBlock == null) {
            this.blockService.resetBlock(block);
            return;
        }

        if (block.getType() != customBlock.getMaterial()) {
            this.blockService.deleteBlockData(block);
            return;
        }

        this.registerBlockData(customBlock, block, positionContainer);
    }

    private <BlockData extends CustomBlockData> void registerBlockData(final CustomBlock<BlockData> customBlock, final Block block, final PersistentDataContainer positionContainer) {
        final BlockData blockData = customBlock.createBlockData(positionContainer);

        if (blockData.hasExpired()) {
            customBlock.onExpire(block, blockData);

            this.blockService.deleteBlockData(block);
            return;
        }

        this.blockService.addBlockData(block, blockData);

        customBlock.onChunkLoad(block, blockData);
    }
}