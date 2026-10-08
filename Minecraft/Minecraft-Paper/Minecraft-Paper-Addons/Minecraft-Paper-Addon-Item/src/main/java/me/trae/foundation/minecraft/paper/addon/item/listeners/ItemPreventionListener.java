package me.trae.foundation.minecraft.paper.addon.item.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.item.services.ItemService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.stream.Stream;

@AllArgsConstructor
@AddonSingleton
public final class ItemPreventionListener implements Listener {

    private final ItemService itemService;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPrepareAnvil(final PrepareAnvilEvent event) {
        if (event.getResult() == null) {
            return;
        }

        final AnvilInventory anvilInventory = event.getInventory();

        if (!this.isCustomItem(anvilInventory.getFirstItem(), anvilInventory.getSecondItem())) {
            return;
        }

        event.setResult(null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPrepareItemEnchant(final PrepareItemEnchantEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final ItemStack secondary = event.getInventory() instanceof final EnchantingInventory enchantingInventory ? enchantingInventory.getSecondary() : null;

        if (!this.isCustomItem(event.getItem(), secondary)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEnchantItem(final EnchantItemEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final ItemStack secondary = event.getInventory() instanceof final EnchantingInventory enchantingInventory ? enchantingInventory.getSecondary() : null;

        if (!this.isCustomItem(event.getItem(), secondary)) {
            return;
        }

        event.setCancelled(true);
    }

    private boolean isCustomItem(final ItemStack... itemStacks) {
        return Stream.of(itemStacks).filter(Objects::nonNull).anyMatch(itemStack -> this.itemService.getItemByItemStack(itemStack).isPresent());
    }
}