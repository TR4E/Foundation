package me.trae.foundation.minecraft.paper.addon.window.types;

import me.trae.foundation.minecraft.paper.addon.window.Window;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public abstract class ChestWindow<Layout extends me.trae.foundation.minecraft.paper.addon.window.layout.Layout> extends Window<Layout> {

    public ChestWindow(final Layout layout, final Player viewer) {
        super(layout, viewer);

        if (layout.getSize() > 54) {
            throw new IllegalArgumentException("ChestWindow requires a maximum layout size of 54");
        }

        if (layout.getSize() % 9 != 0) {
            throw new IllegalArgumentException("ChestWindow requires a multiplier layout size of 9");
        }
    }

    @Override
    protected final Inventory createInventory() {
        return Bukkit.getServer().createInventory(this, this.getLayout().getSize(), this.getTitle());
    }
}