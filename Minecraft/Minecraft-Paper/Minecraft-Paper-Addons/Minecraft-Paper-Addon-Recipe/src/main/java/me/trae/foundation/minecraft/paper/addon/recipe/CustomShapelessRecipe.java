package me.trae.foundation.minecraft.paper.addon.recipe;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.Collections;
import java.util.List;

public abstract class CustomShapelessRecipe extends AbstractCustomRecipe {

    public CustomShapelessRecipe(final String namespace) {
        super(namespace);
    }

    private List<ItemStack> collectIngredients() {
        return this.getIngredients().values().stream()
                .flatMap(ingredient -> Collections.nCopies(ingredient.getAmount(), ingredient.asOne()).stream())
                .toList();
    }

    @Override
    public final Recipe build() {
        final ShapelessRecipe shapelessRecipe = new ShapelessRecipe(this.getKey(), this.getResult());

        this.collectIngredients().forEach(ingredient -> shapelessRecipe.addIngredient(this.toChoice(ingredient)));

        return shapelessRecipe;
    }

    @Override
    protected final ItemStack[] toMatrix() {
        final List<ItemStack> ingredients = this.collectIngredients();

        final ItemStack[] matrix = new ItemStack[9];

        for (int index = 0; index < Math.min(ingredients.size(), matrix.length); index++) {
            matrix[index] = ingredients.get(index);
        }

        return matrix;
    }
}