package me.trae.foundation.minecraft.paper.addon.sidebar;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

@AllArgsConstructor
@Getter
public abstract class Sidebar {

    private final String identifier;
    private final int priority;

    public boolean canDisplay() {
        return true;
    }

    public boolean canDisplay(final Player player) {
        return true;
    }

    public boolean isStaticTitle() {
        return true;
    }

    public abstract Component getTitle(final Player player);

    public abstract List<Component> getLines(final Player player);
}