package me.trae.foundation.minecraft.paper.addon.sidebar.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.event.asynchronous.CustomAsynchronousCancellableEvent;
import org.bukkit.entity.Player;

@AllArgsConstructor
@Getter
public final class SidebarUpdateEvent extends CustomAsynchronousCancellableEvent {

    private final String identifier;
    private final Player player;

    public SidebarUpdateEvent(final Player player) {
        this(null, player);
    }
}