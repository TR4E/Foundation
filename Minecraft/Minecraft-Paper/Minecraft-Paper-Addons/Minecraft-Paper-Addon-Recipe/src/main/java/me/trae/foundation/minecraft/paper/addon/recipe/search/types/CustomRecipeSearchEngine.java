package me.trae.foundation.minecraft.paper.addon.recipe.search.types;

import me.trae.foundation.minecraft.paper.addon.recipe.AbstractCustomRecipe;
import me.trae.foundation.minecraft.paper.addon.recipe.services.RecipeService;
import me.trae.foundation.minecraft.paper.plugin.framework.search.AbstractPaperSearchEngine;
import me.trae.foundation.utilities.UtilString;
import org.bukkit.command.CommandSender;

import java.util.Locale;

public final class CustomRecipeSearchEngine extends AbstractPaperSearchEngine<AbstractCustomRecipe> {

    public CustomRecipeSearchEngine(final RecipeService recipeService) {
        super("Custom Recipe Search", recipeService::getRecipeList);
    }

    @Override
    protected String getMatchName(final AbstractCustomRecipe abstractCustomRecipe, final CommandSender commandSender) {
        return UtilString.clean(abstractCustomRecipe.getKey());
    }

    @Override
    protected boolean isExact(final AbstractCustomRecipe abstractCustomRecipe, final String result) {
        return abstractCustomRecipe.getKey().equalsIgnoreCase(result);
    }

    @Override
    protected boolean isMatching(final AbstractCustomRecipe abstractCustomRecipe, final String result) {
        return abstractCustomRecipe.getKey().toLowerCase(Locale.ROOT).contains(result.toLowerCase(Locale.ROOT));
    }
}