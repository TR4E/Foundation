package me.trae.foundation.minecraft.paper.addon.item.listeners;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.trae.foundation.minecraft.paper.addon.item.CustomItem;
import me.trae.foundation.minecraft.paper.addon.item.events.ItemStackUpdateEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

@AddonSingleton
public final class ItemCooldownListener implements Listener {

    @SuppressWarnings("UnstableApiUsage")
    @EventHandler
    public void onItemStackUpdate(final ItemStackUpdateEvent event) {
        if (!(event.getItem() instanceof final CustomItem customItem)) {
            return;
        }

        final ItemStack itemStack = event.getItemStack();

        if (customItem.removeUseCooldown()) {
            if (itemStack.hasData(DataComponentTypes.USE_COOLDOWN)) {
                itemStack.unsetData(DataComponentTypes.USE_COOLDOWN);
            }
        } else {
            if (!itemStack.hasData(DataComponentTypes.USE_COOLDOWN)) {
                if (itemStack.getType().hasDefaultData(DataComponentTypes.USE_COOLDOWN)) {
                    itemStack.resetData(DataComponentTypes.USE_COOLDOWN);
                }
            }
        }
    }
}