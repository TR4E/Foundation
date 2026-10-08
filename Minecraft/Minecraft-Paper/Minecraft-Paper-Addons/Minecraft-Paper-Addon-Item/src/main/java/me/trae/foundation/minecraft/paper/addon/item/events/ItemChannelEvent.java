package me.trae.foundation.minecraft.paper.addon.item.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.item.types.ChannelCustomItem;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomCancellableEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@AllArgsConstructor
@Getter
public final class ItemChannelEvent extends CustomCancellableEvent {

    private final ChannelCustomItem item;
    private final Player player;
    private final ItemStack itemStack;
}