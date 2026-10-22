package me.trae.foundation.minecraft.paper.addon.recipe.events;

import lombok.Getter;
import lombok.Setter;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomCancellableEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;

@Getter
@Setter
public final class VanillaRecipePreCraftEvent extends CustomCancellableEvent {

    @Nullable
    private final Player player;

    @Nullable
    private final Block block;

    private final ItemStack originalResult;

    private ItemStack result;

    public VanillaRecipePreCraftEvent(@Nullable final Player player, @Nullable final Block block, final ItemStack originalResult) {
        this.player = player;
        this.block = block;
        this.originalResult = originalResult;
        this.result = originalResult.clone();
    }
}