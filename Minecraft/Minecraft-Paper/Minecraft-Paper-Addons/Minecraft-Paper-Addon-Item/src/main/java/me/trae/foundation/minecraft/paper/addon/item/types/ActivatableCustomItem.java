package me.trae.foundation.minecraft.paper.addon.item.types;

import me.trae.foundation.minecraft.paper.addon.item.CustomItem;
import me.trae.foundation.minecraft.paper.addon.item.enums.ActivateType;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilMaterial;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public abstract class ActivatableCustomItem extends CustomItem {

    public ActivatableCustomItem(final Material material, final String identifier, final String namespace) {
        super(material, identifier, namespace);
    }

    public abstract Set<ActivateType> getSupportedActivateTypes();

    public Event.Result useItemInHand(final Player player, final ItemStack itemStack, final ActivateType activateType) {
        return activateType == ActivateType.RIGHT_CLICK && UtilMaterial.isUsable(itemStack.getType()) ? Event.Result.DENY : Event.Result.DEFAULT;
    }

    public Event.Result useInteractedBlock(final Player player, final ItemStack itemStack, final Block block, final ActivateType activateType) {
        return Event.Result.DEFAULT;
    }

    public boolean activateOnItemUse(final Player player, final ItemStack itemStack, final ActivateType activateType) {
        return true;
    }

    public boolean activateOnBlockUse(final Player player, final ItemStack itemStack, final Block block, final ActivateType activateType) {
        return false;
    }

    public boolean canActivate(final Player player, final ItemStack itemStack, final Block clickedBlock, final ActivateType activateType) {
        return true;
    }

    public abstract void onActivate(final Player player, final ItemStack itemStack, final Block clickedBlock, final ActivateType activateType);

    public String getCooldownName(final ActivateType activateType) {
        return this.getName();
    }

    public long getCooldownDuration(final ActivateType activateType) {
        return 0L;
    }
}