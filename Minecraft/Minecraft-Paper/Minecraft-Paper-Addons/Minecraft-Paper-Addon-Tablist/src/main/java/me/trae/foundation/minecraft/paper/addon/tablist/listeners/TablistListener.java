package me.trae.foundation.minecraft.paper.addon.tablist.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.tablist.events.TablistUpdateEvent;
import me.trae.foundation.minecraft.paper.addon.tablist.services.TablistService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

@AllArgsConstructor
@AddonSingleton
public final class TablistListener implements Listener {

    private final TablistService tablistService;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTablistUpdate(final TablistUpdateEvent event) {
        final Player player = event.getPlayer();

        if (event.isCancelled()) {
            this.tablistService.remove(player);
            return;
        }

        this.tablistService.getEligibleTablist(player).ifPresentOrElse(tablist -> {
            this.tablistService.create(player, tablist);
        }, () -> {
            this.tablistService.remove(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        this.tablistService.getActiveTablistSet().remove(event.getPlayer().getUniqueId());
    }
}