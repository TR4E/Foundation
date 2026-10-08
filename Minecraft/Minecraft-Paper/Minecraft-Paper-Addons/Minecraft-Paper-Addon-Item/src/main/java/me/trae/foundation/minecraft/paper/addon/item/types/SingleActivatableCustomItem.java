package me.trae.foundation.minecraft.paper.addon.item.types;

import me.trae.foundation.minecraft.paper.addon.item.enums.ActivateType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public abstract class SingleActivatableCustomItem extends ActivatableCustomItem {

    private final ActivateType activateType;

    public SingleActivatableCustomItem(final Material material, final String identifier, final String namespace, final ActivateType activateType) {
        super(material, identifier, namespace);

        this.activateType = activateType;
    }

    @Override
    public final Set<ActivateType> getSupportedActivateTypes() {
        return Set.of(this.activateType);
    }

    @Override
    public final boolean canActivate(final Player player, final ItemStack itemStack, final Block clickedBlock, final ActivateType activateType) {
        return this.activateType == activateType && this.canActivate(player, itemStack, clickedBlock);
    }

    @Override
    public final void onActivate(final Player player, final ItemStack itemStack, final Block clickedBlock, final ActivateType activateType) {
        this.onActivate(player, itemStack, clickedBlock);
    }

    @Override
    public final Event.Result useItemInHand(final Player player, final ItemStack itemStack, final ActivateType activateType) {
        return this.useItemInHand(player, itemStack);
    }

    @Override
    public final Event.Result useInteractedBlock(final Player player, final ItemStack itemStack, final Block block, final ActivateType activateType) {
        return this.useInteractedBlock(player, itemStack, block);
    }

    @Override
    public final String getCooldownName(final ActivateType activateType) {
        return activateType == this.activateType ? this.getCooldownName() : super.getCooldownName(activateType);
    }

    @Override
    public final long getCooldownDuration(final ActivateType activateType) {
        return activateType == this.activateType ? this.getCooldownDuration() : super.getCooldownDuration(activateType);
    }

    public Event.Result useItemInHand(final Player player, final ItemStack itemStack) {
        return Event.Result.DEFAULT;
    }

    public Event.Result useInteractedBlock(final Player player, final ItemStack itemStack, final Block block) {
        return Event.Result.DEFAULT;
    }

    public String getCooldownName() {
        return super.getCooldownName(this.activateType);
    }

    public long getCooldownDuration() {
        return super.getCooldownDuration(this.activateType);
    }

    public boolean canActivate(final Player player, final ItemStack itemStack, final Block clickedBlock) {
        return true;
    }

    public abstract void onActivate(final Player player, final ItemStack itemStack, final Block clickedBlock);
}