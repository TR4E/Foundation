package me.trae.foundation.minecraft.paper.addon.hologram.services;

import lombok.AllArgsConstructor;
import me.trae.foundation.injector.extensions.scheduler.annotation.Scheduler;
import me.trae.foundation.minecraft.paper.addon.hologram.Hologram;
import me.trae.foundation.minecraft.paper.addon.hologram.utility.UtilHologram;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@AddonSingleton
public final class HologramService {

    private final List<Hologram> hologramList;

    @Scheduler(period = 500, unit = TimeUnit.MILLISECONDS)
    public void onScheduler() {
        final List<Player> playerList = UtilServer.getOnlinePlayers();

        if (playerList.isEmpty()) {
            return;
        }

        for (final Hologram hologram : this.hologramList) {
            if (!hologram.isBuilt()) {
                hologram.build();

                if (!hologram.isBuilt()) {
                    continue;
                }
            }

            final Location location = hologram.getLocation();

            final Set<UUID> viewerSet = hologram.getViewerSet();

            final boolean dynamic = hologram.isDynamic();

            for (final Player player : playerList) {
                final boolean viewing = viewerSet.contains(player.getUniqueId());
                final boolean visible = hologram.isVisible(player, location);

                if (visible && !viewing) {
                    UtilHologram.spawn(player, hologram);
                    continue;
                }

                if (!visible && viewing) {
                    UtilHologram.despawn(player, hologram);
                    continue;
                }

                if (visible && dynamic) {
                    UtilHologram.update(player, hologram);
                }
            }
        }
    }

    public void relocate(final Hologram hologram) {
        this.despawn(hologram);

        hologram.build();
    }

    public void refresh(final Hologram hologram) {
        hologram.build();

        if (!hologram.isBuilt()) {
            return;
        }

        for (final Player player : UtilServer.getOnlinePlayers()) {
            if (!hologram.getViewerSet().contains(player.getUniqueId())) {
                continue;
            }

            UtilHologram.update(player, hologram);
        }
    }

    public void despawn(final Hologram hologram) {
        if (!hologram.isBuilt()) {
            return;
        }

        for (final Player player : UtilServer.getOnlinePlayers()) {
            if (!hologram.getViewerSet().contains(player.getUniqueId())) {
                continue;
            }

            UtilHologram.despawn(player, hologram);
        }
    }

    public void forget(final Player player) {
        this.hologramList.forEach(hologram -> hologram.getViewerSet().remove(player.getUniqueId()));
    }
}