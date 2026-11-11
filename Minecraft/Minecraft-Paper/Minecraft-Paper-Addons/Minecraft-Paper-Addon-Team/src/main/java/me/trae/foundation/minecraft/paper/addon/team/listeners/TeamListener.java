package me.trae.foundation.minecraft.paper.addon.team.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.team.Team;
import me.trae.foundation.minecraft.paper.addon.team.events.TeamUpdateEvent;
import me.trae.foundation.minecraft.paper.addon.team.services.TeamService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerHideEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShowEntityEvent;

@AllArgsConstructor
@AddonSingleton
public final class TeamListener implements Listener {

    private final TeamService teamService;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeamUpdate(final TeamUpdateEvent event) {
        final Player player = event.getPlayer();

        if (!player.isOnline()) {
            return;
        }

        for (final Player viewer : UtilServer.getOnlinePlayers()) {
            final String identifier = event.getIdentifier();

            if (identifier != null) {
                final Team activeTeam = this.teamService.getActiveTeam(player, viewer).orElse(null);

                if (activeTeam == null || !identifier.equals(activeTeam.getIdentifier())) {
                    continue;
                }
            }

            final Team eligibleTeam = this.teamService.getEligibleTeam(player, viewer).orElse(null);

            if (eligibleTeam == null || (identifier != null && !identifier.equals(eligibleTeam.getIdentifier()))) {
                this.teamService.remove(player, viewer);
                continue;
            }

            this.teamService.create(player, viewer, eligibleTeam);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        for (final Player viewer : UtilServer.getOnlinePlayers()) {
            this.teamService.refresh(player, viewer);

            if (!player.equals(viewer)) {
                this.teamService.refresh(viewer, player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        this.teamService.cleanup(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerHideEntity(final PlayerHideEntityEvent event) {
        if (!(event.getEntity() instanceof final Player player)) {
            return;
        }

        this.teamService.remove(player, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerShowEntity(final PlayerShowEntityEvent event) {
        if (!(event.getEntity() instanceof final Player player)) {
            return;
        }

        this.teamService.refresh(player, event.getPlayer());
    }
}