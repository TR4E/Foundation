package me.trae.foundation.minecraft.paper.addon.tablist.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.tablist.Tablist;
import me.trae.foundation.minecraft.paper.addon.tablist.events.TablistUpdateEvent;
import me.trae.foundation.minecraft.paper.addon.tablist.services.TablistService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@AllArgsConstructor
@AddonSingleton
public final class TablistListener implements Listener {

    private final TablistService tablistService;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTablistUpdate(final TablistUpdateEvent event) {
        final Player player = event.getPlayer();

        if (!player.isOnline()) {
            return;
        }

        final Tablist activeTablist = this.tablistService.getActiveTablistMap().get(player.getUniqueId());

        final String identifier = event.getIdentifier();

        if (identifier != null && (activeTablist == null || !identifier.equals(activeTablist.getIdentifier()))) {
            return;
        }

        if (event.isCancelled()) {
            if (activeTablist != null) {
                this.tablistService.remove(player);
            }
            return;
        }

        final Tablist eligibleTablist = this.tablistService.getEligibleTablist(player).orElse(null);

        if (eligibleTablist == null || (identifier != null && !identifier.equals(eligibleTablist.getIdentifier()))) {
            this.tablistService.remove(player);
            return;
        }

        this.tablistService.create(player, eligibleTablist);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        UtilEvent.dispatchAsynchronous(new TablistUpdateEvent(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        this.tablistService.remove(event.getPlayer());
    }
}