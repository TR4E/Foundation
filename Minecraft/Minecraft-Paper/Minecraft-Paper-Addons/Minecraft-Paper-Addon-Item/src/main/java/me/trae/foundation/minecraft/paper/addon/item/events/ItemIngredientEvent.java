package me.trae.foundation.minecraft.paper.addon.item.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.addon.item.CustomItem;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

@AllArgsConstructor
@Getter
@Setter
public final class ItemIngredientEvent extends CustomEvent {

    private final CustomItem item;
    private final ItemStack itemStack;
    private final NamespacedKey namespacedKey;

    private boolean allowed;
}