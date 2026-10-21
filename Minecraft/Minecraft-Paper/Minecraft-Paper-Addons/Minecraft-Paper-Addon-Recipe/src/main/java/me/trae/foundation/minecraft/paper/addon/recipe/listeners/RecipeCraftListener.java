package me.trae.foundation.minecraft.paper.addon.recipe.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.recipe.AbstractCustomRecipe;
import me.trae.foundation.minecraft.paper.addon.recipe.services.RecipeService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.Keyed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;

@AllArgsConstructor
@AddonSingleton
public final class RecipeCraftListener implements Listener {

    private final RecipeService recipeService;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemCraft(final PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof final Keyed keyed)) {
            return;
        }

        final AbstractCustomRecipe abstractCustomRecipe = this.recipeService.getRecipeByKey(keyed.getKey()).orElse(null);
        if (abstractCustomRecipe == null) {
            return;
        }

        final CraftingInventory craftingInventory = event.getInventory();

        if (abstractCustomRecipe.matches(craftingInventory.getMatrix())) {
            return;
        }

        craftingInventory.setResult(null);
    }
}