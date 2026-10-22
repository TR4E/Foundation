package me.trae.foundation.minecraft.paper.addon.recipe.listeners;

import io.papermc.paper.event.inventory.ItemCraftedEvent;
import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.recipe.AbstractCustomRecipe;
import me.trae.foundation.minecraft.paper.addon.recipe.events.CustomRecipePostCraftEvent;
import me.trae.foundation.minecraft.paper.addon.recipe.events.VanillaRecipePostCraftEvent;
import me.trae.foundation.minecraft.paper.addon.recipe.services.RecipeService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

@AllArgsConstructor
@AddonSingleton
public final class RecipePostCraftListener implements Listener {

    private final RecipeService recipeService;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemCrafted(final ItemCraftedEvent event) {
        final Player player = event.getPlayer();
        if (!(player.getOpenInventory().getTopInventory() instanceof final CraftingInventory craftingInventory)) {
            return;
        }

        if (!(craftingInventory.getRecipe() instanceof final Keyed keyed)) {
            return;
        }

        final ItemStack result = event.getCraftedItem();
        if (result.isEmpty()) {
            return;
        }

        this.handle(keyed, player, null, result);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCrafterCraft(final CrafterCraftEvent event) {
        if (event.isCancelled()) {
            return;
        }

        this.handle(event.getRecipe(), null, event.getBlock(), event.getResult());
    }

    private void handle(final Keyed keyed, final Player player, final Block block, final ItemStack result) {
        final AbstractCustomRecipe abstractCustomRecipe = this.recipeService.getRecipeByKey(keyed.getKey()).orElse(null);
        if (abstractCustomRecipe == null) {
            if (keyed.getKey().getNamespace().equals(NamespacedKey.MINECRAFT)) {
                UtilEvent.supply(new VanillaRecipePostCraftEvent(player, block, result));
            }

            return;
        }

        UtilEvent.supply(new CustomRecipePostCraftEvent(abstractCustomRecipe, player, block, result));
    }
}