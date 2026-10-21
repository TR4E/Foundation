package me.trae.foundation.minecraft.paper.addon.recipe.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.item.events.ItemIngredientEvent;
import me.trae.foundation.minecraft.paper.addon.recipe.services.RecipeService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

@AllArgsConstructor
@AddonSingleton
public final class RecipeIngredientListener implements Listener {

    private final RecipeService recipeService;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemIngredient(final ItemIngredientEvent event) {
        if (event.getNamespacedKey() == null) {
            return;
        }

        if (event.isAllowed()) {
            return;
        }

        if (!this.recipeService.isIngredient(event.getNamespacedKey(), event.getItemStack())) {
            return;
        }

        event.setAllowed(true);
    }
}