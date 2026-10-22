package me.trae.foundation.minecraft.paper.addon.recipe.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.recipe.AbstractCustomRecipe;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;

@AllArgsConstructor
@Getter
public final class CustomRecipePostCraftEvent extends CustomEvent {

    private final AbstractCustomRecipe recipe;

    @Nullable
    private final Player player;

    @Nullable
    private final Block block;

    private final ItemStack result;
}