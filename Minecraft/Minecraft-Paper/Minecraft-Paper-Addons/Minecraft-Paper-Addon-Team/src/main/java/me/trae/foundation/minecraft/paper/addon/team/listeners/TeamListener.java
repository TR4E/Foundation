package me.trae.foundation.minecraft.paper.addon.team.listeners;

import lombok.AllArgsConstructor;
import me.trae.foundation.minecraft.paper.addon.team.events.TeamUpdateEvent;
import me.trae.foundation.minecraft.paper.addon.team.services.TeamService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@AllArgsConstructor
@AddonSingleton
public final class TeamListener implements Listener {

    private final TeamService teamService;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeamUpdate(final TeamUpdateEvent event) {
        final Player player = event.getPlayer();

        for (final Player viewer : Bukkit.getServer().getOnlinePlayers()) {
            if (event.getIdentifier() == null) {
                this.teamService.refresh(player, viewer);
                continue;
            }

            this.teamService.getEligibleTeam(player, viewer)
                    .filter(team -> team.getIdentifier().equals(event.getIdentifier()))
                    .ifPresentOrElse(team -> {
                        this.teamService.create(player, viewer, team);
                    }, () -> {
                        this.teamService.remove(player, viewer);
                    });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        for (final Player viewer : Bukkit.getServer().getOnlinePlayers()) {
            this.teamService.refresh(player, viewer);

            if (!player.equals(viewer)) {
                this.teamService.refresh(viewer, player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();

        for (final Player viewer : Bukkit.getServer().getOnlinePlayers()) {
            this.teamService.remove(player, viewer);

            if (!player.equals(viewer)) {
                this.teamService.remove(viewer, player);
            }
        }
    }
}