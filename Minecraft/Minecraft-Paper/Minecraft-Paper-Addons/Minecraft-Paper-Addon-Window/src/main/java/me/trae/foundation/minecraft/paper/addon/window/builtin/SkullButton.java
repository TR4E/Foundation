package me.trae.foundation.minecraft.paper.addon.window.builtin;

import me.trae.foundation.minecraft.paper.addon.window.element.Button;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public abstract class SkullButton<W extends me.trae.foundation.minecraft.paper.addon.window.Window<?>> extends Button<W> {

    public SkullButton(final W window, final ItemStack itemStack) {
        super(window, itemStack);
    }

    public SkullButton(final W window) {
        this(window, new ItemStack(Material.PLAYER_HEAD));
    }

    @Override
    public final void build() {
        this.applySkull(this.getItemStack());
    }

    protected abstract void applySkull(final ItemStack itemStack);
}