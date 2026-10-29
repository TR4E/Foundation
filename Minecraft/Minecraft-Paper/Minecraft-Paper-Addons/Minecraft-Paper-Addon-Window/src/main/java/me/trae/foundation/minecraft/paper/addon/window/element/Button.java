package me.trae.foundation.minecraft.paper.addon.window.element;

import me.trae.foundation.minecraft.paper.plugin.framework.stash.SoundStash;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public abstract class Button<Window extends me.trae.foundation.minecraft.paper.addon.window.Window<?>> extends Element<Window> {

    public Button(final Window window, final ItemStack itemStack) {
        super(window, itemStack);
    }

    public SoundStash getClickSoundStash(final Player player, final ClickType clickType) {
        return null;
    }

    public boolean canClick(final Player player, final ClickType clickType) {
        return true;
    }

    public abstract void onClick(final Player player, final ClickType clickType);
}