package me.trae.foundation.minecraft.paper.addon.tablist;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.tablist.events.TablistUpdateEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

@AllArgsConstructor
@Getter
public abstract class Tablist {

    private final int priority;

    public final void updatePlayer(final Player player) {
        UtilEvent.dispatchSynchronously(new TablistUpdateEvent(player));
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

    public abstract Component getHeader(final Player player);

    public abstract Component getFooter(final Player player);
}