package me.trae.foundation.minecraft.paper.addon.sidebar;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.sidebar.events.SidebarUpdateEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

@AllArgsConstructor
@Getter
public abstract class Sidebar {

    private final String identifier;
    private final int priority;

    public final void updatePlayer(final Player player) {
        UtilEvent.dispatchAsynchronous(new SidebarUpdateEvent(this.identifier, player));
    }

    public final void updateAllPlayers() {
        UtilServer.getOnlinePlayers().forEach(this::updatePlayer);
    }

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