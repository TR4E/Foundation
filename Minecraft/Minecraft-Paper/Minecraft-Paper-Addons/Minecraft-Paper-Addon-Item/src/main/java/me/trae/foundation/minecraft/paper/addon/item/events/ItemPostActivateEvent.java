package me.trae.foundation.minecraft.paper.addon.item.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.item.enums.ActivateType;
import me.trae.foundation.minecraft.paper.addon.item.types.ActivatableCustomItem;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@AllArgsConstructor
@Getter
public final class ItemPostActivateEvent extends CustomEvent {

    private final ActivatableCustomItem item;
    private final Player player;
    private final ItemStack itemStack;
    private final Block clickedBlock;
    private final ActivateType activateType;
}