package me.trae.foundation.minecraft.paper.addon.hologram.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.hologram.Hologram;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;
import org.bukkit.entity.Player;

@AllArgsConstructor
@Getter
public final class HologramUpdateEvent extends CustomEvent {

    private final Hologram hologram;
    private final Player player;
}