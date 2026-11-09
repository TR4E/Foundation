package me.trae.foundation.minecraft.paper.addon.window.builtin;

import me.trae.foundation.minecraft.paper.addon.window.Window;
import me.trae.foundation.minecraft.paper.addon.window.element.Button;
import me.trae.foundation.minecraft.paper.addon.window.layout.PaginatedLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class NextPageButton<W extends Window<L>, L extends PaginatedLayout> extends Button<W> {

    public NextPageButton(final W window, final ItemStack itemStack) {
        super(window, itemStack);
    }

    public NextPageButton(final W window) {
        this(window, ItemStack.of(Material.ARROW));
    }

    @Override
    public Component getDisplayName() {
        return Component.text("Next Page", NamedTextColor.YELLOW);
    }

    @Override
    public List<String> getDescription() {
        return List.of();
    }

    @Override
    public void onClick(final Player player, final ClickType clickType) {
        if (this.getWindow().getLayout().nextPage()) {
            this.getWindow().render();
        }
    }
}