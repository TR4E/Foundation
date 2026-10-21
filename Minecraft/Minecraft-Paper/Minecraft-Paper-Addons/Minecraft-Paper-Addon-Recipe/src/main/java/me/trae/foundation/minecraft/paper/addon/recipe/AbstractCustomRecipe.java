package me.trae.foundation.minecraft.paper.addon.recipe;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilItemStack;
import me.trae.foundation.utilities.UtilJava;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@AllArgsConstructor
@Getter
public abstract class AbstractCustomRecipe {

    private final String namespace;

    public final NamespacedKey getKey() {
        return new NamespacedKey("custom", this.namespace);
    }

    public boolean removeConflictingRecipes() {
        return true;
    }

    protected final RecipeChoice toChoice(final ItemStack itemStack) {
        return new RecipeChoice.MaterialChoice(itemStack.getType());
    }

    public final boolean isIngredientByItemStack(final ItemStack itemStack) {
        return this.getIngredients().values().stream().anyMatch(ingredient -> UtilItemStack.isSimilar(ingredient, itemStack));
    }

    public final boolean matches(final ItemStack[] matrix) {
        return Arrays.stream(matrix)
                .filter(itemStack -> itemStack != null && !itemStack.isEmpty())
                .allMatch(this::isIngredientByItemStack);
    }

    public final Set<NamespacedKey> getConflictingKeys() {
        return UtilJava.createCollection(new HashSet<>(), set -> {
            if (!this.removeConflictingRecipes() || Bukkit.getWorlds().isEmpty()) {
                return;
            }

            if (Bukkit.getCraftingRecipe(this.toMatrix(), Bukkit.getWorlds().getFirst()) instanceof final Keyed keyed && keyed.getKey().getNamespace().equals(NamespacedKey.MINECRAFT)) {
                set.add(keyed.getKey());
            }
        });
    }

    public abstract ItemStack getResult();

    public abstract Map<Character, ItemStack> getIngredients();

    public abstract Recipe build();

    protected abstract ItemStack[] toMatrix();
}