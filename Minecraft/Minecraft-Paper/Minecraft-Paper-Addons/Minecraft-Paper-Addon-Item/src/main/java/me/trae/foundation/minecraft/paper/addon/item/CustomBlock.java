package me.trae.foundation.minecraft.paper.addon.item;

import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.item.block.CustomBlockData;
import me.trae.foundation.minecraft.paper.addon.item.block.ToolRequirement;
import me.trae.foundation.minecraft.paper.addon.item.enums.BlockRemoveReason;
import me.trae.foundation.minecraft.paper.addon.item.enums.InteractType;
import me.trae.foundation.utilities.UtilClass;
import me.trae.foundation.utilities.UtilGeneric;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.Collections;
import java.util.Set;

public abstract class CustomBlock<BlockData extends CustomBlockData> extends CustomItem {

    @Getter
    @SuppressWarnings("unchecked")
    private final Class<BlockData> blockDataClass = (Class<BlockData>) UtilGeneric.getGenericParameter(CustomBlock.class, this.getClass(), 0);

    public CustomBlock(final Material material, final String uniqueId, final String identifier) {
        if (!material.isBlock()) {
            throw new IllegalArgumentException("Material is not a block");
        }

        super(material, uniqueId, identifier);
    }

    public final BlockData createBlockData(final PersistentDataContainer persistentDataContainer) {
        try {
            return UtilClass.create(this.blockDataClass, persistentDataContainer);
        } catch (final Exception exception) {
            throw new RuntimeException("Could not create block data with PDC for %s (%s)".formatted(this.getClass().getName(), this.blockDataClass.getName()), exception);
        }
    }

    public BlockData createBlockData(final Player player, final Block block) {
        try {
            return UtilClass.create(this.blockDataClass, player);
        } catch (final Exception exception) {
            throw new RuntimeException("Could not create block data with  for %s (%s)".formatted(this.getClass().getName(), this.blockDataClass.getName()), exception);
        }
    }

    public boolean canPlace(final Player player, final Block block) {
        return true;
    }

    public boolean canBreak(final Player player, final Block block, final BlockData blockData) {
        return true;
    }

    public boolean canRemove(final Block block, final BlockData blockData, final BlockRemoveReason removeReason) {
        return true;
    }

    public boolean canInteract(final Player player, final Block block, final ItemStack itemInHand, final InteractType interactType, final BlockData blockData) {
        return true;
    }

    public boolean canDrop(final Player player, final Block block, final BlockData blockData) {
        return true;
    }

    public void onPlace(final Player player, final Block block) {
    }

    public void onBreak(final Player player, final Block block, final BlockData blockData) {
    }

    public void onRemove(final Block block, final BlockData blockData, final BlockRemoveReason removeReason) {
    }

    public void onInteract(final Player player, final Block block, final ItemStack itemInHand, final InteractType interactType, final BlockData blockData) {
    }

    public void onTick(final Block block, final BlockData blockData) {
    }

    public void onExpire(final Block block, final BlockData blockData) {
    }

    public void onChunkLoad(final Block block, final BlockData blockData) {
    }

    public void onChunkUnload(final Block block, final BlockData blockData) {
    }

    public boolean shouldUnloadChunk(final Block block, final BlockData blockData) {
        return true;
    }

    public boolean shouldRemoveBlockOnExpire(final Block block, final BlockData blockData) {
        return true;
    }

    public boolean insertOnDrop(final Player player, final Block block, final BlockData blockData) {
        return false;
    }

    public Set<ItemStack> getDrops(final Player player, final Block block, final BlockData blockData) {
        return Collections.singleton(this.create());
    }

    public ToolRequirement getToolRequirement() {
        return ToolRequirement.CORRECT_TOOL;
    }
}