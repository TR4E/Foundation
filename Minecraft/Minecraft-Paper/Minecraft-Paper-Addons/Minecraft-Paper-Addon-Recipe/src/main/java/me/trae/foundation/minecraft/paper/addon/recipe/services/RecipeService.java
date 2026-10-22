package me.trae.foundation.minecraft.paper.addon.recipe.services;

import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.trae.foundation.injector.api.lifecycle.Lifecycle;
import me.trae.foundation.minecraft.paper.addon.recipe.AbstractCustomRecipe;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@CustomLog
@RequiredArgsConstructor
@Getter
@AddonSingleton
public final class RecipeService implements Lifecycle {

    public static final NamespacedKey DISABLED_RECIPE_KEY = new NamespacedKey("custom", "disabled_recipe");

    private final Set<NamespacedKey> registeredNamespacedKeySet = new HashSet<>();

    private final List<AbstractCustomRecipe> recipeList;

    private volatile Map<NamespacedKey, AbstractCustomRecipe> recipeByKeyMap = Map.of();

    @Override
    public void onLiveDependencyUpdate() {
        this.removeRegisteredRecipes();

        final Map<NamespacedKey, AbstractCustomRecipe> recipeByKeyMap = new HashMap<>();
        final Set<NamespacedKey> conflictingNamespacedKeySet = new HashSet<>();

        for (final AbstractCustomRecipe abstractCustomRecipe : this.recipeList) {
            recipeByKeyMap.putIfAbsent(abstractCustomRecipe.getNamespacedKey(), abstractCustomRecipe);
            conflictingNamespacedKeySet.addAll(abstractCustomRecipe.getConflictingKeys());
        }

        conflictingNamespacedKeySet.forEach(namespacedKey -> {
            if (Bukkit.getServer().removeRecipe(namespacedKey, true)) {
                LOGGER.info("Removed conflicting vanilla recipe '{}'.", namespacedKey.asString());
            }
        });

        this.recipeList.forEach(abstractCustomRecipe -> {
            if (Bukkit.getServer().addRecipe(abstractCustomRecipe.build(), true)) {
                this.registeredNamespacedKeySet.add(abstractCustomRecipe.getNamespacedKey());
            }
        });

        this.recipeByKeyMap = Map.copyOf(recipeByKeyMap);
    }

    @Override
    public void onComponentShutdown() {
        this.removeRegisteredRecipes();

        this.recipeByKeyMap = Map.of();
    }

    public Optional<AbstractCustomRecipe> getRecipeByKey(final NamespacedKey namespacedKey) {
        if (namespacedKey == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(this.recipeByKeyMap.get(namespacedKey));
    }

    public boolean isIngredient(final NamespacedKey namespacedKey, final ItemStack itemStack) {
        return this.getRecipeByKey(namespacedKey).map(abstractCustomRecipe -> abstractCustomRecipe.isIngredientByItemStack(itemStack)).orElse(false);
    }

    private void removeRegisteredRecipes() {
        this.registeredNamespacedKeySet.forEach(namespacedKey -> Bukkit.getServer().removeRecipe(namespacedKey, true));

        this.registeredNamespacedKeySet.clear();
    }
}