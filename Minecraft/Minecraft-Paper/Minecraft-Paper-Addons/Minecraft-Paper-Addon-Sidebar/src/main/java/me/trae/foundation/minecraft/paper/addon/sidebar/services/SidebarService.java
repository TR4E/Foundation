package me.trae.foundation.minecraft.paper.addon.sidebar.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.injector.extensions.scheduler.annotation.Scheduler;
import me.trae.foundation.minecraft.paper.addon.sidebar.Sidebar;
import me.trae.foundation.minecraft.paper.addon.sidebar.events.SidebarUpdateEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilNms;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@AddonSingleton
public final class SidebarService {

    @Getter
    private final ConcurrentHashMap<UUID, Sidebar> activeSidebarMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<UUID, Component> cachedTitleMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<UUID, List<Component>> cachedLinesMap = new ConcurrentHashMap<>();

    private List<Sidebar> sidebarList;

    @Scheduler(period = 250, unit = TimeUnit.MILLISECONDS, asynchronous = true)
    public void onScheduler() {
        for (final Player player : Bukkit.getServer().getOnlinePlayers()) {
            final Sidebar activeSidebar = this.activeSidebarMap.get(player.getUniqueId());
            if (activeSidebar != null) {
                if (!activeSidebar.canDisplay() || !activeSidebar.canDisplay(player)) {
                    UtilEvent.dispatch(new SidebarUpdateEvent(player));
                    continue;
                }
            }

            this.updateTitle(player);
        }
    }

    public void create(final Player player, final Sidebar sidebar) {
        final String identifier = sidebar.getIdentifier();
        final Component title = sidebar.getTitle(player);
        final List<Component> lines = sidebar.getLines(player);

        final Objective objective = this.buildObjective(identifier, title);

        UtilNms.sendPacket(player, new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));
        UtilNms.sendPacket(player, new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));

        final int size = lines.size();

        for (int index = 0; index < size; index++) {
            this.sendLine(player, identifier, index, lines.get(index), size - 1 - index);
        }

        this.cachedTitleMap.put(player.getUniqueId(), title);
        this.cachedLinesMap.put(player.getUniqueId(), lines);
    }

    public void refreshTitle(final Player player, final Sidebar sidebar) {
        final Component newTitle = sidebar.getTitle(player);
        final Component cachedTitle = this.cachedTitleMap.get(player.getUniqueId());

        if (newTitle.equals(cachedTitle)) {
            return;
        }

        UtilNms.sendPacket(player, new ClientboundSetObjectivePacket(
                this.buildObjective(sidebar.getIdentifier(), newTitle),
                ClientboundSetObjectivePacket.METHOD_CHANGE
        ));

        this.cachedTitleMap.put(player.getUniqueId(), newTitle);
    }

    public void updateLines(final Player player, final Sidebar sidebar) {
        final List<Component> newLines = sidebar.getLines(player);
        final List<Component> oldLines = this.cachedLinesMap.getOrDefault(player.getUniqueId(), Collections.emptyList());

        final int newSize = newLines.size();
        final int oldSize = oldLines.size();

        for (int index = 0; index < newSize; index++) {
            final Component newLine = newLines.get(index);
            final Component oldLine = index < oldSize ? oldLines.get(index) : null;

            // Re-send when the content changed, or when a length change shifted this line's score.
            if (!newLine.equals(oldLine) || newSize != oldSize) {
                this.sendLine(player, sidebar.getIdentifier(), index, newLine, newSize - 1 - index);
            }
        }

        for (int index = newSize; index < oldSize; index++) {
            this.removeLine(player, sidebar.getIdentifier(), index);
        }

        this.cachedLinesMap.put(player.getUniqueId(), newLines);
    }

    public void clear(final Player player) {
        this.cachedTitleMap.remove(player.getUniqueId());
        this.cachedLinesMap.remove(player.getUniqueId());

        final Sidebar activeSidebar = this.activeSidebarMap.remove(player.getUniqueId());
        if (activeSidebar == null) {
            return;
        }

        UtilNms.sendPacket(player, new ClientboundSetObjectivePacket(
                this.buildObjective(activeSidebar.getIdentifier(), activeSidebar.getTitle(player)),
                ClientboundSetObjectivePacket.METHOD_REMOVE
        ));
    }

    public Optional<Sidebar> getEligibleSidebar(final Player player) {
        return this.sidebarList.stream().filter(sidebar -> sidebar.canDisplay() && sidebar.canDisplay(player)).findFirst();
    }

    private void updateTitle(final Player player) {
        final Sidebar activeSidebar = this.activeSidebarMap.get(player.getUniqueId());
        if (activeSidebar == null || activeSidebar.isStaticTitle()) {
            return;
        }

        this.refreshTitle(player, activeSidebar);
    }

    private void sendLine(final Player player, final String identifier, final int index, final Component line, final int score) {
        UtilNms.sendPacket(player, new ClientboundSetScorePacket(
                this.getScoreOwner(player, index),
                identifier,
                score,
                Optional.of(UtilNms.toNms(line)),
                Optional.empty()
        ));
    }

    private void removeLine(final Player player, final String identifier, final int index) {
        UtilNms.sendPacket(player, new ClientboundResetScorePacket(this.getScoreOwner(player, index), identifier));
    }

    private Objective buildObjective(final String identifier, final Component title) {
        return new Objective(
                new Scoreboard(),
                identifier,
                ObjectiveCriteria.DUMMY,
                UtilNms.toNms(title),
                ObjectiveCriteria.RenderType.INTEGER,
                false,
                null
        );
    }

    private String getScoreOwner(final Player player, final int index) {
        return "%s:%s".formatted(player.getUniqueId(), index);
    }
}