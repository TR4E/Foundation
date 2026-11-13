package me.trae.foundation.minecraft.paper.addon.item.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.injector.extensions.scheduler.annotation.Scheduler;
import me.trae.foundation.minecraft.paper.addon.item.CustomBlock;
import me.trae.foundation.minecraft.paper.addon.item.block.CustomBlockData;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilPdc;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilTask;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilWorld;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@Getter
@AddonSingleton
public final class BlockService {

    private static final NamespacedKey BLOCK_KEY = new NamespacedKey("custom", "block");

    private final ConcurrentHashMap<UUID, ConcurrentHashMap<Long, ConcurrentHashMap<Integer, CustomBlockData>>> blockDataMap = new ConcurrentHashMap<>();

    private final ItemService itemService;

    @Scheduler(period = 50, unit = TimeUnit.MILLISECONDS)
    public void onScheduler() {
        for (final Map.Entry<UUID, ConcurrentHashMap<Long, ConcurrentHashMap<Integer, CustomBlockData>>> worldEntry : this.blockDataMap.entrySet()) {
            final World world = UtilWorld.getWorldById(worldEntry.getKey()).orElse(null);
            if (world == null) {
                continue;
            }

            for (final Map.Entry<Long, ConcurrentHashMap<Integer, CustomBlockData>> chunkEntry : worldEntry.getValue().entrySet()) {
                this.tickChunk(world, chunkEntry.getKey(), chunkEntry.getValue());
            }
        }
    }

    public void addBlockData(final Block block, final CustomBlockData blockData) {
        this.blockDataMap.computeIfAbsent(block.getWorld().getUID(), _ -> new ConcurrentHashMap<>()).computeIfAbsent(this.getChunkKey(block), _ -> new ConcurrentHashMap<>()).put(this.getPositionKey(block), blockData);
    }

    public <BlockData extends CustomBlockData> Optional<BlockData> getBlockDataByBlock(final Class<BlockData> type, final Block block) {
        return this.getBlockMapByBlock(block).map(blockMap -> blockMap.get(this.getPositionKey(block))).filter(type::isInstance).map(type::cast);
    }

    @SuppressWarnings("unchecked")
    public Optional<CustomBlock<CustomBlockData>> getItemByBlock(final Block block) {
        return this.getBlockDataByBlock(CustomBlockData.class, block).flatMap(blockData -> this.itemService.getItemByUniqueId(blockData.getItemUniqueId())).filter(CustomBlock.class::isInstance).map(customItem -> (CustomBlock<CustomBlockData>) customItem);
    }

    public void updateBlockData(final Block block) {
        final CustomBlockData blockData = this.getBlockDataByBlock(CustomBlockData.class, block).orElse(null);
        if (blockData == null) {
            return;
        }

        blockData.setDirty(true);
    }

    public void writeBlockData(final Block block) {
        final CustomBlockData blockData = this.getBlockDataByBlock(CustomBlockData.class, block).orElse(null);
        if (blockData == null) {
            return;
        }

        this.writeBlockData(block, blockData);
    }

    public void deleteBlockData(final Block block) {
        this.getBlockMapByBlock(block).ifPresent(blockMap -> blockMap.remove(this.getPositionKey(block)));

        final PersistentDataContainer chunkContainer = block.getChunk().getPersistentDataContainer();

        final PersistentDataContainer blockContainer = UtilPdc.getPersistentData(chunkContainer, BLOCK_KEY, PersistentDataType.TAG_CONTAINER).orElse(null);
        if (blockContainer == null) {
            return;
        }

        UtilPdc.removePersistentDataType(blockContainer, this.getPositionNamespacedKey(block));

        if (blockContainer.isEmpty()) {
            UtilPdc.removePersistentDataType(chunkContainer, BLOCK_KEY);
            return;
        }

        UtilPdc.setPersistentDataType(chunkContainer, BLOCK_KEY, PersistentDataType.TAG_CONTAINER, blockContainer);
    }

    public void resetBlock(final Block block) {
        this.deleteBlockData(block);

        UtilTask.executeSynchronously(() -> block.setType(Material.AIR));
    }

    public Optional<PersistentDataContainer> getBlockContainerByChunk(final Chunk chunk) {
        return UtilPdc.getPersistentData(chunk.getPersistentDataContainer(), BLOCK_KEY, PersistentDataType.TAG_CONTAINER);
    }

    public Optional<PersistentDataContainer> getPositionContainer(final PersistentDataContainer blockContainer, final NamespacedKey positionKey) {
        return UtilPdc.getPersistentData(blockContainer, positionKey, PersistentDataType.TAG_CONTAINER);
    }

    public Block getBlockByPositionKey(final Chunk chunk, final int positionKey) {
        return chunk.getBlock(positionKey & 15, (positionKey >> 8) + chunk.getWorld().getMinHeight(), (positionKey >> 4) & 15);
    }

    public Optional<Block> getBlockByPositionKey(final Chunk chunk, final NamespacedKey positionKey) {
        try {
            return Optional.of(this.getBlockByPositionKey(chunk, Integer.parseInt(positionKey.getKey())));
        } catch (final NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public void unloadChunk(final Chunk chunk) {
        final ConcurrentHashMap<Long, ConcurrentHashMap<Integer, CustomBlockData>> chunkMap = this.blockDataMap.get(chunk.getWorld().getUID());
        if (chunkMap == null) {
            return;
        }

        final ConcurrentHashMap<Integer, CustomBlockData> blockMap = chunkMap.get(chunk.getChunkKey());
        if (blockMap == null) {
            return;
        }

        for (final Map.Entry<Integer, CustomBlockData> blockEntry : blockMap.entrySet()) {
            this.prepareUnloadBlockData(chunk, blockEntry.getKey(), blockEntry.getValue());
        }

        this.writeChunk(chunk, blockMap);

        blockMap.entrySet().removeIf(blockEntry -> this.shouldUnloadBlockData(chunk, blockEntry.getKey(), blockEntry.getValue()));

        if (blockMap.isEmpty()) {
            chunkMap.remove(chunk.getChunkKey());
        }
    }

    public void unloadWorld(final World world) {
        this.blockDataMap.remove(world.getUID());
    }

    private void writeBlockData(final Block block, final CustomBlockData blockData) {
        final PersistentDataContainer chunkContainer = block.getChunk().getPersistentDataContainer();

        final NamespacedKey positionKey = this.getPositionNamespacedKey(block);

        final PersistentDataContainer blockContainer = UtilPdc.getOrCreatePersistentDataContainer(chunkContainer, BLOCK_KEY);

        final PersistentDataContainer positionContainer = UtilPdc.getOrCreatePersistentDataContainer(blockContainer, positionKey);

        blockData.write(positionContainer);

        UtilPdc.setPersistentDataType(blockContainer, positionKey, PersistentDataType.TAG_CONTAINER, positionContainer);
        UtilPdc.setPersistentDataType(chunkContainer, BLOCK_KEY, PersistentDataType.TAG_CONTAINER, blockContainer);

        blockData.setDirty(false);
    }

    private void writeChunk(final Chunk chunk, final ConcurrentHashMap<Integer, CustomBlockData> blockMap) {
        final PersistentDataContainer chunkContainer = chunk.getPersistentDataContainer();

        final PersistentDataContainer blockContainer = UtilPdc.getOrCreatePersistentDataContainer(chunkContainer, BLOCK_KEY);

        for (final Map.Entry<Integer, CustomBlockData> blockEntry : blockMap.entrySet()) {
            final NamespacedKey positionKey = this.getPositionNamespacedKey(blockEntry.getKey());

            final PersistentDataContainer positionContainer = UtilPdc.getOrCreatePersistentDataContainer(blockContainer, positionKey);

            blockEntry.getValue().write(positionContainer);

            UtilPdc.setPersistentDataType(blockContainer, positionKey, PersistentDataType.TAG_CONTAINER, positionContainer);

            blockEntry.getValue().setDirty(false);
        }

        UtilPdc.setPersistentDataType(chunkContainer, BLOCK_KEY, PersistentDataType.TAG_CONTAINER, blockContainer);
    }

    private void tickChunk(final World world, final long chunkKey, final ConcurrentHashMap<Integer, CustomBlockData> blockMap) {
        final int chunkX = (int) chunkKey;
        final int chunkZ = (int) (chunkKey >>> 32);

        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }

        final Chunk chunk = world.getChunkAt(chunkX, chunkZ);

        for (final Map.Entry<Integer, CustomBlockData> blockEntry : blockMap.entrySet()) {
            this.tickBlockData(this.getBlockByPositionKey(chunk, blockEntry.getKey()), blockEntry.getValue());
        }
    }

    private void tickBlockData(final Block block, final CustomBlockData blockData) {
        final CustomBlock<CustomBlockData> customBlock = this.getItemByBlock(block).orElse(null);

        if (blockData.hasExpired()) {
            if (customBlock != null) {
                customBlock.onExpire(block, blockData);
            }

            if (customBlock == null || customBlock.shouldRemoveBlockOnExpire(block, blockData)) {
                this.resetBlock(block);
                return;
            }

            this.deleteBlockData(block);
            return;
        }

        if (customBlock != null) {
            customBlock.onTick(block, blockData);
        }

        if (!blockData.isDirty()) {
            return;
        }

        this.writeBlockData(block, blockData);
    }

    private void prepareUnloadBlockData(final Chunk chunk, final int positionKey, final CustomBlockData blockData) {
        final Block block = this.getBlockByPositionKey(chunk, positionKey);

        final CustomBlock<CustomBlockData> customBlock = this.getItemByBlock(block).orElse(null);
        if (customBlock == null) {
            return;
        }

        customBlock.onChunkUnload(block, blockData);

        blockData.setLastTickedAt(System.currentTimeMillis());
    }

    private boolean shouldUnloadBlockData(final Chunk chunk, final int positionKey, final CustomBlockData blockData) {
        final Block block = this.getBlockByPositionKey(chunk, positionKey);

        return this.getItemByBlock(block).map(customBlock -> customBlock.shouldUnloadChunk(block, blockData)).orElse(true);
    }

    private Optional<ConcurrentHashMap<Integer, CustomBlockData>> getBlockMapByBlock(final Block block) {
        final ConcurrentHashMap<Long, ConcurrentHashMap<Integer, CustomBlockData>> chunkMap = this.blockDataMap.get(block.getWorld().getUID());
        if (chunkMap == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(chunkMap.get(this.getChunkKey(block)));
    }

    private long getChunkKey(final Block block) {
        return Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4);
    }

    private int getPositionKey(final Block block) {
        return (block.getX() & 15) | ((block.getZ() & 15) << 4) | ((block.getY() - block.getWorld().getMinHeight()) << 8);
    }

    private NamespacedKey getPositionNamespacedKey(final int positionKey) {
        return new NamespacedKey("block", Integer.toString(positionKey));
    }

    private NamespacedKey getPositionNamespacedKey(final Block block) {
        return this.getPositionNamespacedKey(this.getPositionKey(block));
    }
}