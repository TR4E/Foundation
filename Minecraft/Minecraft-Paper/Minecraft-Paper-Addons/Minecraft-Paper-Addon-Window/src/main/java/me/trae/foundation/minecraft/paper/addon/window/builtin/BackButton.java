package me.trae.foundation.minecraft.paper.addon.window.builtin;

import me.trae.foundation.minecraft.paper.addon.window.Window;
import me.trae.foundation.minecraft.paper.addon.window.element.Button;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class BackButton<W extends Window<?>> extends Button<W> {

    private final Window<?> parentWindow;

    public BackButton(final W window, final ItemStack itemStack, final Window<?> parentWindow) {
        super(window, itemStack);

        this.parentWindow = parentWindow;
    }

    public BackButton(final W window, final Window<?> parentWindow) {
        this(window, ItemStack.of(Material.BARRIER), parentWindow);
    }

    @Override
    public Component getDisplayName() {
        return Component.text("Back", NamedTextColor.RED);
    }

    @Override
    public List<String> getDescription() {
        return List.of();
    }

    @Override
    public void onClick(final Player player, final ClickType clickType) {
        this.parentWindow.open();
    }
}