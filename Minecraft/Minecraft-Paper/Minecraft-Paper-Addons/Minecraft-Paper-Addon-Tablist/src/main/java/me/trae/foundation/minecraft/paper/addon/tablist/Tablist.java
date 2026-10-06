package me.trae.foundation.minecraft.paper.addon.tablist;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

@AllArgsConstructor
@Getter
public abstract class Tablist {

    private final int priority;

    public boolean canDisplay() {
        return true;
    }

    public boolean canDisplay(final Player player) {
        return true;
    }

    public abstract Component getHeader(final Player player);

    public abstract Component getFooter(final Player player);
}