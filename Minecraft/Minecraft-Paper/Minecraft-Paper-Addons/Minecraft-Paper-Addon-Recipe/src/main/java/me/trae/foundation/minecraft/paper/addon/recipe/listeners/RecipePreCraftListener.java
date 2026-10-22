package me.trae.foundation.minecraft.paper.addon.recipe.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.recipe.AbstractCustomRecipe;
import me.trae.foundation.minecraft.paper.addon.recipe.events.CustomRecipePreCraftEvent;
import me.trae.foundation.minecraft.paper.addon.recipe.events.VanillaRecipePreCraftEvent;
import me.trae.foundation.minecraft.paper.addon.recipe.services.RecipeService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilItemStack;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.function.Consumer;

@AllArgsConstructor
@AddonSingleton
public final class RecipePreCraftListener implements Listener {

    private final RecipeService recipeService;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemCraft(final PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof final Keyed keyed)) {
            return;
        }

        if (!(event.getView().getPlayer() instanceof final Player player)) {
            return;
        }

        final CraftingInventory craftingInventory = event.getInventory();

        final AbstractCustomRecipe abstractCustomRecipe = this.recipeService.getRecipeByKey(keyed.getKey()).orElse(null);
        if (abstractCustomRecipe == null) {
            final ItemStack result = craftingInventory.getResult();

            if (this.isVanilla(keyed, result) && !this.handleVanilla(player, null, result, craftingInventory::setResult)) {
                craftingInventory.setResult(null);
            }

            return;
        }

        this.handleCraft(abstractCustomRecipe, player, craftingInventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCrafterCraft(final CrafterCraftEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final CraftingRecipe craftingRecipe = event.getRecipe();

        final Block block = event.getBlock();

        final ItemStack result = event.getResult();

        final AbstractCustomRecipe abstractCustomRecipe = this.recipeService.getRecipeByKey(craftingRecipe.getKey()).orElse(null);
        if (abstractCustomRecipe == null) {
            if (this.isVanilla(craftingRecipe, result) && !this.handleVanilla(null, block, result, event::setResult)) {
                event.setCancelled(true);
            }

            return;
        }

        if (this.handleCraft(abstractCustomRecipe, block, result)) {
            return;
        }

        event.setCancelled(true);
    }

    private void handleCraft(final AbstractCustomRecipe abstractCustomRecipe, final Player player, final CraftingInventory craftingInventory) {
        if (!abstractCustomRecipe.matches(craftingInventory.getMatrix())) {
            craftingInventory.setResult(null);
            return;
        }

        final CustomRecipePreCraftEvent customRecipePreCraftEvent = UtilEvent.supply(new CustomRecipePreCraftEvent(abstractCustomRecipe, player, null, craftingInventory.getResult()));

        final ItemStack result = customRecipePreCraftEvent.getResult();

        if (customRecipePreCraftEvent.isCancelled() || !abstractCustomRecipe.canCraft() || !abstractCustomRecipe.canCraft(player)) {
            craftingInventory.setResult(UtilItemStack.getPersistentData(result, RecipeService.DISABLED_RECIPE_KEY, PersistentDataType.BOOLEAN).isPresent() ? result : null);
            return;
        }

        craftingInventory.setResult(result);
    }

    private boolean handleCraft(final AbstractCustomRecipe abstractCustomRecipe, final Block block, final ItemStack result) {
        if (!(block.getState(false) instanceof final Crafter crafter)) {
            return false;
        }

        if (!abstractCustomRecipe.matches(crafter.getInventory().getContents())) {
            return false;
        }

        if (UtilEvent.supply(new CustomRecipePreCraftEvent(abstractCustomRecipe, null, block, result)).isCancelled()) {
            return false;
        }

        if (!abstractCustomRecipe.canCraft()) {
            return false;
        }

        if (!abstractCustomRecipe.canCraft(block)) {
            return false;
        }

        return true;
    }

    private boolean isVanilla(final Keyed keyed, final ItemStack result) {
        return result != null && keyed.getKey().getNamespace().equals(NamespacedKey.MINECRAFT);
    }

    private boolean handleVanilla(final Player player, final Block block, final ItemStack result, final Consumer<ItemStack> consumer) {
        final VanillaRecipePreCraftEvent vanillaRecipePreCraftEvent = UtilEvent.supply(new VanillaRecipePreCraftEvent(player, block, result));

        if (vanillaRecipePreCraftEvent.isCancelled()) {
            return false;
        }

        consumer.accept(vanillaRecipePreCraftEvent.getResult());

        return true;
    }
}