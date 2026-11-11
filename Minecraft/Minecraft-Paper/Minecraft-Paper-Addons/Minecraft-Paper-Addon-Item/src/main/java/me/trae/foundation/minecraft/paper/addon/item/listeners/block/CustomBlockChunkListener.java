package me.trae.foundation.minecraft.paper.addon.item.listeners.block;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.item.block.CustomBlockData;
import me.trae.foundation.minecraft.paper.addon.item.services.BlockService;
import me.trae.foundation.minecraft.paper.addon.item.types.PlaceableCustomItem;
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

        final PlaceableCustomItem<?> placeableCustomItem = CustomBlockData.getItemUniqueId(positionContainer).flatMap(this.blockService.getItemService()::getItemByUniqueId).filter(PlaceableCustomItem.class::isInstance).map(PlaceableCustomItem.class::cast).orElse(null);
        if (placeableCustomItem == null) {
            this.blockService.resetBlock(block);
            return;
        }

        if (block.getType() != placeableCustomItem.getMaterial()) {
            this.blockService.deleteBlockData(block);
            return;
        }

        this.registerBlockData(placeableCustomItem, block, positionContainer);
    }

    private <BlockData extends CustomBlockData> void registerBlockData(final PlaceableCustomItem<BlockData> placeableCustomItem, final Block block, final PersistentDataContainer positionContainer) {
        final BlockData blockData = placeableCustomItem.createBlockData(positionContainer);

        if (blockData.hasExpired()) {
            placeableCustomItem.onExpire(block, blockData);

            this.blockService.deleteBlockData(block);
            return;
        }

        this.blockService.addBlockData(block, blockData);

        placeableCustomItem.onChunkLoad(block, blockData);
    }
}