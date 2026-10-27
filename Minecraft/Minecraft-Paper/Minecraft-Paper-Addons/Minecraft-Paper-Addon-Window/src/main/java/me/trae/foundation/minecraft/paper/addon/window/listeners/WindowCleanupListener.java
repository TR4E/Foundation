package me.trae.foundation.minecraft.paper.addon.window.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.window.services.WindowService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

@AllArgsConstructor
@AddonSingleton
public final class WindowCleanupListener implements Listener {

    private final WindowService windowService;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        this.windowService.cleanup(event.getPlayer());
    }
}