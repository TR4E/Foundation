package me.trae.foundation.minecraft.paper.addon.item.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.item.CustomItem;
import me.trae.foundation.minecraft.paper.addon.item.services.ItemService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;

@AllArgsConstructor
@AddonSingleton
public final class ItemEventListener implements Listener {

    private final ItemService itemService;

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamage(final EntityDamageEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!(event.getEntity() instanceof final Item item)) {
            return;
        }

        final CustomItem customItem = this.itemService.getItemByItemStack(item.getItemStack()).orElse(null);
        if (customItem == null) {
            return;
        }

        if (!customItem.isInvincible()) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityCombust(final EntityCombustEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!(event.getEntity() instanceof final Item item)) {
            return;
        }

        final CustomItem customItem = this.itemService.getItemByItemStack(item.getItemStack()).orElse(null);
        if (customItem == null) {
            return;
        }

        if (!customItem.isInvincible()) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onItemDespawn(final ItemDespawnEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final CustomItem customItem = this.itemService.getItemByItemStack(event.getEntity().getItemStack()).orElse(null);
        if (customItem == null) {
            return;
        }

        if (customItem.canDespawn()) {
            return;
        }

        event.setCancelled(true);
    }
}