package me.trae.foundation.minecraft.paper.addon.item.types;

import me.trae.foundation.minecraft.paper.addon.item.CustomItem;
import me.trae.foundation.minecraft.paper.addon.item.block.CustomBlockData;
import me.trae.foundation.minecraft.paper.addon.item.block.ToolRequirement;
import me.trae.foundation.minecraft.paper.addon.item.enums.BlockRemoveReason;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.Collections;
import java.util.Set;

public abstract class PlaceableCustomItem<BlockData extends CustomBlockData> extends CustomItem {

    public PlaceableCustomItem(final Material material, final String uniqueId, final String identifier) {
        if (!material.isBlock()) {
            throw new IllegalArgumentException("Material is not a block");
        }

        super(material, uniqueId, identifier);
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

    public boolean canDamage(final Player player, final Block block, final ItemStack itemInHand, final BlockData blockData) {
        return block.getType().getHardness() >= 0.0F;
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

    public void onDamage(final Player player, final Block block, final ItemStack itemInHand, final BlockData blockData) {
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

    public boolean insertOnDrop(final Player player, final Block block, final BlockData blockData) {
        return false;
    }

    public Set<ItemStack> getDrops(final Player player, final Block block, final BlockData blockData) {
        return Collections.singleton(this.create());
    }

    public ToolRequirement getToolRequirement() {
        return ToolRequirement.CORRECT_TOOL;
    }

    public abstract BlockData createBlockData(final Player player, final Block block);

    public abstract BlockData createBlockData(final PersistentDataContainer persistentDataContainer);
}