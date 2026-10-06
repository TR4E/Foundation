package me.trae.foundation.minecraft.paper.addon.tablist.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.event.asynchronous.CustomAsynchronousCancellableEvent;
import org.bukkit.entity.Player;

@AllArgsConstructor
@Getter
public final class TablistUpdateEvent extends CustomAsynchronousCancellableEvent {

    private final Player player;
}