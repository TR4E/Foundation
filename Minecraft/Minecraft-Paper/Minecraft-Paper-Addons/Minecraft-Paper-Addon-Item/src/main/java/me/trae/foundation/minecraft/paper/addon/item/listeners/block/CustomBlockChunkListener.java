package me.trae.foundation.minecraft.paper.addon.item.listeners.block;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.item.CustomBlock;
import me.trae.foundation.minecraft.paper.addon.item.block.CustomBlockData;
import me.trae.foundation.minecraft.paper.addon.item.services.BlockService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilWorld;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;

@AllArgsConstructor
@AddonSingleton
public final class CustomBlockChunkListener implements Listener {

    private final BlockService blockService;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(final ServerLoadEvent event) {
        if (event.getType() != ServerLoadEvent.LoadType.STARTUP) {
            return;
        }

        for (final World world : UtilWorld.getWorlds()) {
            for (final Chunk chunk : world.getLoadedChunks()) {
                this.loadChunk(chunk);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(final ChunkLoadEvent event) {
        if (event.isNewChunk()) {
            return;
        }

        this.loadChunk(event.getChunk());
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

    private void loadChunk(final Chunk chunk) {
        final PersistentDataContainer blockPersistentDataContainer = this.blockService.getBlockContainerByChunk(chunk).orElse(null);

        if (blockPersistentDataContainer == null) {
            return;
        }

        for (final NamespacedKey positionNamespacedKey : blockPersistentDataContainer.getKeys()) {
            this.loadBlockData(chunk, blockPersistentDataContainer, positionNamespacedKey);
        }
    }

    private void loadBlockData(final Chunk chunk, final PersistentDataContainer blockPersistentDataContainer, final NamespacedKey positionNamespacedKey) {
        final PersistentDataContainer positionPersistentDataContainer = this.blockService.getPositionContainer(blockPersistentDataContainer, positionNamespacedKey).orElse(null);

        if (positionPersistentDataContainer == null) {
            return;
        }

        final Block block = this.blockService.getBlockByPositionKey(chunk, positionNamespacedKey).orElse(null);

        if (block == null) {
            return;
        }

        if (this.blockService.getBlockDataByBlock(CustomBlockData.class, block).isPresent()) {
            return;
        }

        final CustomBlock<?> customBlock = CustomBlockData.getItemUniqueId(positionPersistentDataContainer)
                .flatMap(this.blockService.getItemService()::getItemByUniqueId)
                .filter(CustomBlock.class::isInstance)
                .map(CustomBlock.class::cast)
                .orElse(null);

        if (customBlock == null) {
            this.blockService.resetBlock(block);
            return;
        }

        if (block.getType() != customBlock.getMaterial()) {
            this.blockService.deleteBlockData(block);
            return;
        }

        this.registerBlockData(customBlock, block, positionPersistentDataContainer);
    }

    private <BlockData extends CustomBlockData> void registerBlockData(final CustomBlock<BlockData> customBlock, final Block block, final PersistentDataContainer positionPersistentDataContainer) {
        final BlockData blockData = customBlock.createBlockData(positionPersistentDataContainer);

        if (blockData.hasExpired()) {
            customBlock.onExpire(block, blockData);

            if (customBlock.shouldRemoveBlockOnExpire(block, blockData)) {
                this.blockService.resetBlock(block);
                return;
            }

            this.blockService.deleteBlockData(block);
            return;
        }

        this.blockService.addBlockData(block, blockData);

        customBlock.onChunkLoad(block, blockData);
    }
}