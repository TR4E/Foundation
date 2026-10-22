package me.trae.foundation.minecraft.paper.addon.recipe;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

import java.util.Map;

public abstract class CustomShapedRecipe extends AbstractCustomRecipe {

    public CustomShapedRecipe(final String namespace) {
        super(namespace);
    }

    @Override
    public final Recipe build() {
        final ShapedRecipe shapedRecipe = new ShapedRecipe(this.getNamespacedKey(), this.buildResult());

        shapedRecipe.shape(this.getShape());

        this.getIngredients().forEach((character, ingredient) -> shapedRecipe.setIngredient(character, this.toChoice(ingredient)));

        return shapedRecipe;
    }

    @Override
    protected final ItemStack[] toMatrix() {
        final Map<Character, ItemStack> ingredients = this.getIngredients();

        final String[] shape = this.getShape();

        final ItemStack[] matrix = new ItemStack[9];

        for (int row = 0; row < shape.length; row++) {
            for (int column = 0; column < shape[row].length(); column++) {
                final ItemStack ingredient = ingredients.get(shape[row].charAt(column));

                if (ingredient != null) {
                    matrix[row * 3 + column] = ingredient.asOne();
                }
            }
        }

        return matrix;
    }

    public abstract String[] getShape();
}