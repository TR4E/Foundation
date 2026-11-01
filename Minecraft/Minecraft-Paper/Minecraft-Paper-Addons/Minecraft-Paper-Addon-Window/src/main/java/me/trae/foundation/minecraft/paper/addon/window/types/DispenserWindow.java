package me.trae.foundation.minecraft.paper.addon.window.types;

import me.trae.foundation.minecraft.paper.addon.window.Window;
import me.trae.foundation.minecraft.paper.addon.window.constants.WindowConstants;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

public abstract class DispenserWindow<Layout extends me.trae.foundation.minecraft.paper.addon.window.layout.Layout> extends Window<Layout> {

    public DispenserWindow(final Layout layout, final Player viewer) {
        super(layout, viewer);

        if (layout.getSize() != WindowConstants.DISPENSER_WINDOW_SIZE) {
            throw new IllegalArgumentException("%s requires a layout size of %s".formatted(DispenserWindow.class.getSimpleName(), WindowConstants.DISPENSER_WINDOW_SIZE));
        }
    }

    @Override
    protected final Inventory createInventory() {
        return Bukkit.getServer().createInventory(this, InventoryType.DISPENSER, this.getTitle());
    }
}