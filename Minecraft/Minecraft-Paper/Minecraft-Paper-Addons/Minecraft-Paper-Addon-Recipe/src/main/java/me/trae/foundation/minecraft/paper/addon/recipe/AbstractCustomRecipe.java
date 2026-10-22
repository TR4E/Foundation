package me.trae.foundation.minecraft.paper.addon.recipe;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilItemStack;
import me.trae.foundation.utilities.UtilJava;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
public abstract class AbstractCustomRecipe {

    private final String key;

    public final NamespacedKey getNamespacedKey() {
        return new NamespacedKey("custom", this.key);
    }

    public boolean removeConflictingRecipes() {
        return true;
    }

    public boolean canCraft() {
        return true;
    }

    public boolean canCraft(final Player player) {
        return true;
    }

    public boolean canCraft(final Block block) {
        return true;
    }

    protected final RecipeChoice toChoice(final ItemStack itemStack) {
        return new RecipeChoice.MaterialChoice(itemStack.getType());
    }

    protected final ItemStack buildResult() {
        final ItemStack itemStack = this.getResult();

        itemStack.setAmount(Math.min(itemStack.getAmount(), itemStack.getMaxStackSize()));

        return itemStack;
    }

    public final boolean isIngredientByItemStack(final ItemStack itemStack) {
        return this.getIngredients().values().stream().anyMatch(ingredient -> UtilItemStack.isSimilar(ingredient, itemStack));
    }

    public boolean matches(final ItemStack[] matrix) {
        final List<ItemStack> required = Arrays.stream(this.toMatrix())
                .filter(itemStack -> itemStack != null && !itemStack.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));

        for (final ItemStack itemStack : matrix) {
            if (itemStack == null || itemStack.isEmpty()) {
                continue;
            }

            if (!this.consume(required, itemStack)) {
                return false;
            }
        }

        return required.isEmpty();
    }

    private boolean consume(final List<ItemStack> required, final ItemStack itemStack) {
        final Iterator<ItemStack> iterator = required.iterator();

        while (iterator.hasNext()) {
            if (!UtilItemStack.isSimilar(iterator.next(), itemStack)) {
                continue;
            }

            iterator.remove();

            return true;
        }

        return false;
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