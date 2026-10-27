package me.trae.foundation.minecraft.paper.addon.window.types;

import me.trae.foundation.minecraft.paper.addon.window.Window;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

public abstract class DispenserWindow<Layout extends me.trae.foundation.minecraft.paper.addon.window.layout.Layout> extends Window<Layout> {

    public DispenserWindow(final Layout layout, final Player viewer) {
        super(layout, viewer);

        if (layout.getSize() != 9) {
            throw new IllegalArgumentException("DispenserWindow requires a layout size of 9");
        }
    }

    @Override
    protected final Inventory createInventory() {
        return Bukkit.getServer().createInventory(this, InventoryType.DISPENSER, this.getTitle());
    }
}