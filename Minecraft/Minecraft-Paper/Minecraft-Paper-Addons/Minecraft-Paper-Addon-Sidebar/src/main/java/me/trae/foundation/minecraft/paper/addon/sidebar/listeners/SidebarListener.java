package me.trae.foundation.minecraft.paper.addon.sidebar.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.sidebar.Sidebar;
import me.trae.foundation.minecraft.paper.addon.sidebar.events.SidebarUpdateEvent;
import me.trae.foundation.minecraft.paper.addon.sidebar.services.SidebarService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;

@AllArgsConstructor
@AddonSingleton
public final class SidebarListener implements Listener {

    private final SidebarService sidebarService;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSidebarUpdate(final SidebarUpdateEvent event) {
        final Player player = event.getPlayer();

        final Sidebar activeSidebar = this.sidebarService.getActiveSidebarMap().get(player.getUniqueId());

        if (event.isCancelled()) {
            if (activeSidebar != null) {
                this.sidebarService.clear(player);
            }
            return;
        }

        if (event.getIdentifier() != null && (activeSidebar == null || !activeSidebar.getIdentifier().equals(event.getIdentifier()))) {
            return;
        }

        final Optional<Sidebar> eligibleSidebarOptional = this.sidebarService.getEligibleSidebar(player);
        if (eligibleSidebarOptional.isEmpty()) {
            this.sidebarService.clear(player);
            return;
        }

        final Sidebar eligibleSidebar = eligibleSidebarOptional.get();

        if (activeSidebar != null && activeSidebar.getIdentifier().equals(eligibleSidebar.getIdentifier())) {
            this.sidebarService.refreshTitle(player, eligibleSidebar);
            this.sidebarService.updateLines(player, eligibleSidebar);
        } else {
            this.sidebarService.clear(player);
            this.sidebarService.create(player, eligibleSidebar);
        }

        this.sidebarService.getActiveSidebarMap().put(player.getUniqueId(), eligibleSidebar);
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        UtilEvent.dispatchAsynchronous(new SidebarUpdateEvent(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        this.sidebarService.clear(event.getPlayer());
    }
}