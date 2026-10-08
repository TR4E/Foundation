package me.trae.foundation.minecraft.paper.addon.item.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.item.Item;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;
import org.bukkit.inventory.ItemStack;

@AllArgsConstructor
@Getter
public final class ItemStackUpdateEvent extends CustomEvent {

    private final Item item;
    private final ItemStack itemStack;
}