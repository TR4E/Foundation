package me.trae.foundation.minecraft.paper.addon.window.builtin;

import me.trae.foundation.minecraft.paper.addon.window.element.Element;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public abstract class SkullElement<W extends me.trae.foundation.minecraft.paper.addon.window.Window<?>> extends Element<W> {

    public SkullElement(final W window, final ItemStack itemStack) {
        super(window, itemStack);
    }

    public SkullElement(final W window) {
        this(window, new ItemStack(Material.PLAYER_HEAD));
    }

    @Override
    public final void build() {
        this.applySkull(this.getItemStack());
    }

    protected abstract void applySkull(final ItemStack itemStack);
}