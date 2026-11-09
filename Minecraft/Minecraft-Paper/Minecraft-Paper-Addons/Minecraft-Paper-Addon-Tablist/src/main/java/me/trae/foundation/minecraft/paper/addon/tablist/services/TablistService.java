package me.trae.foundation.minecraft.paper.addon.tablist.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.injector.api.lifecycle.Lifecycle;
import me.trae.foundation.injector.extensions.scheduler.annotation.Scheduler;
import me.trae.foundation.minecraft.paper.addon.tablist.Tablist;
import me.trae.foundation.minecraft.paper.addon.tablist.events.TablistUpdateEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@Getter
@AddonSingleton
public final class TablistService implements Lifecycle {

    private final ConcurrentHashMap<UUID, Tablist> activeTablistMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<UUID, Map.Entry<Component, Component>> cachedHeaderFooterMap = new ConcurrentHashMap<>();

    private final List<Tablist> tablistList;

    @Override
    public void onComponentShutdown() {
        if (Bukkit.getServer().isStopping()) {
            return;
        }

        List.copyOf(this.activeTablistMap.keySet()).stream()
                .map(UtilServer::getOnlinePlayerById)
                .flatMap(Optional::stream)
                .forEach(this::remove);

        this.activeTablistMap.clear();
        this.cachedHeaderFooterMap.clear();
    }

    @Scheduler(period = 1, unit = TimeUnit.SECONDS, asynchronous = true)
    public void onScheduler() {
        for (final Player player : UtilServer.getOnlinePlayers()) {
            UtilEvent.dispatch(new TablistUpdateEvent(player));
        }
    }

    public void create(final Player player, final Tablist tablist) {
        final Component header = tablist.getHeader(player);
        final Component footer = tablist.getFooter(player);

        final Map.Entry<Component, Component> cached = this.cachedHeaderFooterMap.get(player.getUniqueId());

        if (cached != null && cached.getKey().equals(header) && cached.getValue().equals(footer)) {
            return;
        }

        player.sendPlayerListHeaderAndFooter(header, footer);

        this.cachedHeaderFooterMap.put(player.getUniqueId(), Map.entry(header, footer));

        this.activeTablistMap.put(player.getUniqueId(), tablist);
    }

    public void remove(final Player player) {
        this.cachedHeaderFooterMap.remove(player.getUniqueId());

        if (this.activeTablistMap.remove(player.getUniqueId()) == null) {
            return;
        }

        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }

    public Optional<Tablist> getEligibleTablist(final Player player) {
        return this.tablistList.stream()
                .filter(tablist -> tablist.canDisplay() && tablist.canDisplay(player))
                .min(Comparator.comparingInt(Tablist::getPriority));
    }
}