package me.trae.foundation.minecraft.paper.addon.tablist;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.tablist.events.TablistUpdateEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilTask;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

@AllArgsConstructor
@Getter
public abstract class Tablist {

    private final String identifier;
    private final int priority;

    public final void updatePlayer(final Player player) {
        UtilEvent.dispatchAsynchronous(new TablistUpdateEvent(this.identifier, player));
    }

    public final void updateAllPlayers() {
        final List<Player> playerList = UtilServer.getOnlinePlayers();

        UtilTask.executeAsynchronously(() -> playerList.forEach(player -> UtilEvent.dispatch(new TablistUpdateEvent(this.identifier, player))));
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