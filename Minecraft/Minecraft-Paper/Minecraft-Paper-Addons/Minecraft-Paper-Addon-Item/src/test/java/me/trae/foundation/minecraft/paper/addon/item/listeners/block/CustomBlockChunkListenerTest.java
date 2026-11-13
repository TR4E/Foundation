package me.trae.foundation.minecraft.paper.addon.item.listeners.block;

import me.trae.foundation.minecraft.paper.addon.item.CustomBlock;
import me.trae.foundation.minecraft.paper.addon.item.block.CustomBlockData;
import me.trae.foundation.minecraft.paper.addon.item.services.BlockService;
import me.trae.foundation.minecraft.paper.addon.item.services.ItemService;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilWorld;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class CustomBlockChunkListenerTest {

    private static final NamespacedKey POSITION_KEY = new NamespacedKey("block", "0");

    private final BlockService blockService = mock(BlockService.class);

    private final ItemService itemService = mock(ItemService.class);

    private final World world = mock(World.class);

    private final Chunk chunk = mock(Chunk.class);

    private final Block block = mock(Block.class);

    private final PersistentDataContainer blockContainer = mock(PersistentDataContainer.class);
    private final PersistentDataContainer positionContainer = mock(PersistentDataContainer.class);

    @SuppressWarnings("unchecked")
    private final CustomBlock<CustomBlockData> customBlock = mock(CustomBlock.class);

    private final CustomBlockChunkListener customBlockChunkListener = new CustomBlockChunkListener(this.blockService);

    private MockedStatic<UtilWorld> utilWorldMockedStatic;
    private MockedStatic<Bukkit> bukkitMockedStatic;

    @BeforeEach
    void initialize() {
        this.bukkitMockedStatic = mockStatic(Bukkit.class);
        this.bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
        this.utilWorldMockedStatic = mockStatic(UtilWorld.class);
        this.utilWorldMockedStatic.when(UtilWorld::getWorlds).thenReturn(List.of(this.world));
        when(this.world.getLoadedChunks()).thenReturn(new Chunk[]{this.chunk});
        when(this.blockService.getBlockContainerByChunk(this.chunk)).thenReturn(Optional.of(this.blockContainer));
        when(this.blockContainer.getKeys()).thenReturn(Set.of(POSITION_KEY));
        when(this.blockService.getPositionContainer(this.blockContainer, POSITION_KEY)).thenReturn(Optional.of(this.positionContainer));
        when(this.blockService.getBlockByPositionKey(this.chunk, POSITION_KEY)).thenReturn(Optional.of(this.block));
        when(this.positionContainer.get(new NamespacedKey("custom_block_data", "item_unique_id"), PersistentDataType.STRING))
                .thenReturn("temporary_block");
        when(this.blockService.getItemService()).thenReturn(this.itemService);
        when(this.itemService.getItemByUniqueId("temporary_block")).thenReturn(Optional.of(this.customBlock));
        when(this.customBlock.getMaterial()).thenReturn(Material.GOLD_BLOCK);
        when(this.block.getType()).thenReturn(Material.GOLD_BLOCK);
    }

    @AfterEach
    void shutdown() {
        this.utilWorldMockedStatic.close();
        this.bukkitMockedStatic.close();
    }

    @Test
    void startupExpiresASpawnBlockWhoseLifetimeElapsedWhileOffline() {
        final CustomBlockData customBlockData = this.restoreBlockData(1_000L);
        when(this.customBlock.shouldRemoveBlockOnExpire(this.block, customBlockData)).thenReturn(true);

        this.customBlockChunkListener.onServerLoad(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));

        final InOrder inOrder = inOrder(this.customBlock, this.blockService);
        inOrder.verify(this.customBlock).onExpire(this.block, customBlockData);
        inOrder.verify(this.customBlock).shouldRemoveBlockOnExpire(this.block, customBlockData);
        inOrder.verify(this.blockService).resetBlock(this.block);
        verify(this.blockService, never()).addBlockData(any(Block.class), any(CustomBlockData.class));
        verify(this.customBlock, never()).onChunkLoad(any(Block.class), any(CustomBlockData.class));
    }

    @Test
    void restorationCanRetainThePhysicalBlockAfterExpiry() {
        final CustomBlockData customBlockData = this.restoreBlockData(1_000L);
        when(this.customBlock.shouldRemoveBlockOnExpire(this.block, customBlockData)).thenReturn(false);

        this.customBlockChunkListener.onServerLoad(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));

        final InOrder inOrder = inOrder(this.customBlock, this.blockService);
        inOrder.verify(this.customBlock).onExpire(this.block, customBlockData);
        inOrder.verify(this.customBlock).shouldRemoveBlockOnExpire(this.block, customBlockData);
        inOrder.verify(this.blockService).deleteBlockData(this.block);
        verify(this.blockService, never()).resetBlock(this.block);
        verify(this.blockService, never()).addBlockData(any(Block.class), any(CustomBlockData.class));
    }

    @Test
    void ordinaryChunkLoadAlsoResetsAnExpiredBlock() {
        when(this.world.getLoadedChunks()).thenReturn(new Chunk[0]);
        final CustomBlockData customBlockData = this.restoreBlockData(1_000L);
        when(this.customBlock.shouldRemoveBlockOnExpire(this.block, customBlockData)).thenReturn(true);
        this.customBlockChunkListener.onServerLoad(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));

        this.customBlockChunkListener.onChunkLoad(new ChunkLoadEvent(this.chunk, false));

        verify(this.customBlock).onExpire(this.block, customBlockData);
        verify(this.blockService).resetBlock(this.block);
    }

    @Test
    void serverReloadRestoresAnUnexpiredLoadedBlock() {
        final CustomBlockData customBlockData = this.restoreBlockData(0L);

        this.customBlockChunkListener.onServerLoad(new ServerLoadEvent(ServerLoadEvent.LoadType.RELOAD));

        verify(this.blockService).addBlockData(this.block, customBlockData);
        verify(this.customBlock).onChunkLoad(this.block, customBlockData);
        verify(this.blockService, never()).resetBlock(this.block);
    }

    @Test
    void repeatedRestorationPreservesAlreadyRegisteredStateAndCallbacks() {
        final CustomBlockData customBlockData = this.restoreBlockData(0L);
        this.customBlockChunkListener.onServerLoad(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));
        when(this.blockService.getBlockDataByBlock(CustomBlockData.class, this.block)).thenReturn(Optional.of(customBlockData));

        this.customBlockChunkListener.onChunkLoad(new ChunkLoadEvent(this.chunk, false));

        verify(this.customBlock, times(1)).createBlockData(this.positionContainer);
        verify(this.blockService, times(1)).addBlockData(this.block, customBlockData);
        verify(this.customBlock, times(1)).onChunkLoad(this.block, customBlockData);
    }

    @Test
    void newChunksAreNotRestored() {
        this.customBlockChunkListener.onChunkLoad(new ChunkLoadEvent(this.chunk, true));

        verify(this.blockService, never()).getBlockContainerByChunk(this.chunk);
    }

    private CustomBlockData restoreBlockData(final long expiry) {
        when(this.positionContainer.get(new NamespacedKey("custom_block_data", "placed_at"), PersistentDataType.LONG))
                .thenReturn(System.currentTimeMillis() - 60_000L);
        when(this.positionContainer.get(new NamespacedKey("custom_block_data", "expiry"), PersistentDataType.LONG))
                .thenReturn(expiry);
        when(this.positionContainer.get(new NamespacedKey("custom_block_data", "last_ticked_at"), PersistentDataType.LONG))
                .thenReturn(0L);

        final CustomBlockData customBlockData = new CustomBlockData(this.positionContainer);
        when(this.customBlock.createBlockData(this.positionContainer)).thenReturn(customBlockData);

        return customBlockData;
    }
}