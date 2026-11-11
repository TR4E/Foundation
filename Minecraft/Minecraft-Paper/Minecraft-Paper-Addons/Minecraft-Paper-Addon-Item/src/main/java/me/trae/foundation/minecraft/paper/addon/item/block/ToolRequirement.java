package me.trae.foundation.minecraft.paper.addon.item.block;

import me.trae.foundation.minecraft.paper.addon.item.CustomItem;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@FunctionalInterface
public interface ToolRequirement {

    ToolRequirement ANY = (_, _, _) -> true;
    ToolRequirement CORRECT_TOOL = (player, block, itemStack) -> !block.getDrops(itemStack, player).isEmpty();

    boolean matches(final Player player, final Block block, final ItemStack itemStack);

    static ToolRequirement material(final Material material) {
        return (_, _, itemStack) -> itemStack != null && itemStack.getType() == material;
    }

    static ToolRequirement customItem(final CustomItem customItem) {
        return (_, _, itemStack) -> itemStack != null && customItem.isSimilarByUniqueId(itemStack);
    }

    default ToolRequirement and(final ToolRequirement toolRequirement) {
        return (player, block, itemStack) -> this.matches(player, block, itemStack) && toolRequirement.matches(player, block, itemStack);
    }

    default ToolRequirement or(final ToolRequirement toolRequirement) {
        return (player, block, itemStack) -> this.matches(player, block, itemStack) || toolRequirement.matches(player, block, itemStack);
    }
}