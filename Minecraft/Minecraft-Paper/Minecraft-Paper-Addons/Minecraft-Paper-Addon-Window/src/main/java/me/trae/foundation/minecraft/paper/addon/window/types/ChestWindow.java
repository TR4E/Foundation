package me.trae.foundation.minecraft.paper.addon.window.types;

import me.trae.foundation.minecraft.paper.addon.window.Window;
import me.trae.foundation.minecraft.paper.addon.window.constants.WindowConstants;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public abstract class ChestWindow<Layout extends me.trae.foundation.minecraft.paper.addon.window.layout.Layout> extends Window<Layout> {

    public static final int COLUMN_SIZE = 9;

    public ChestWindow(final Layout layout, final Player viewer) {
        super(layout, viewer);

        if (layout.getSize() > WindowConstants.DOUBLE_CHEST_WINDOW_SIZE) {
            throw new IllegalArgumentException("%s requires a maximum layout size of %s".formatted(ChestWindow.class.getSimpleName(), WindowConstants.DOUBLE_CHEST_WINDOW_SIZE));
        }

        if (layout.getSize() % COLUMN_SIZE != 0) {
            throw new IllegalArgumentException("%s requires a multiplier layout size of %s".formatted(ChestWindow.class.getSimpleName(), COLUMN_SIZE));
        }
    }

    @Override
    protected final Inventory createInventory() {
        return Bukkit.getServer().createInventory(this, this.getLayout().getSize(), this.getTitle());
    }
}