package me.trae.foundation.minecraft.paper.addon.tablist.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.injector.api.lifecycle.Lifecycle;
import me.trae.foundation.injector.extensions.scheduler.annotation.Scheduler;
import me.trae.foundation.minecraft.paper.addon.tablist.Tablist;
import me.trae.foundation.minecraft.paper.addon.tablist.events.TablistUpdateEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@Getter
@AddonSingleton
public final class TablistService implements Lifecycle {

    private final Set<UUID> activeTablistSet = ConcurrentHashMap.newKeySet();

    private final List<Tablist> tablistList;

    @Override
    public void onComponentShutdown() {
        if (Bukkit.isStopping()) {
            return;
        }

        List.copyOf(this.activeTablistSet).stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(this::remove);
    }

    @Scheduler(period = 1, unit = TimeUnit.SECONDS, asynchronous = true)
    public void onScheduler() {
        for (final Player player : Bukkit.getServer().getOnlinePlayers()) {
            UtilEvent.dispatch(new TablistUpdateEvent(player));
        }
    }

    public void create(final Player player, final Tablist tablist) {
        player.sendPlayerListHeaderAndFooter(tablist.getHeader(player), tablist.getFooter(player));

        this.activeTablistSet.add(player.getUniqueId());
    }

    public void remove(final Player player) {
        if (!this.activeTablistSet.remove(player.getUniqueId())) {
            return;
        }

        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }

    public Optional<Tablist> getEligibleTablist(final Player player) {
        return this.tablistList.stream()
                .sorted(Comparator.comparingInt(Tablist::getPriority))
                .filter(tablist -> tablist.canDisplay() && tablist.canDisplay(player))
                .findFirst();
    }
}