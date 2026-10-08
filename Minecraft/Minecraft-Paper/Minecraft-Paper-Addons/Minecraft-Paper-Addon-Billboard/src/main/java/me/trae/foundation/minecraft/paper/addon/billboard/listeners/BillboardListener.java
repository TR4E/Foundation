package me.trae.foundation.minecraft.paper.addon.billboard.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.billboard.services.BillboardService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

@AllArgsConstructor
@AddonSingleton
public final class BillboardListener implements Listener {

    private final BillboardService billboardService;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent event) {
        this.billboardService.forget(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(final PlayerRespawnEvent event) {
        this.billboardService.forget(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        this.billboardService.forget(event.getPlayer(), true);
    }
}