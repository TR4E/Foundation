package me.trae.foundation.minecraft.paper.addon.hologram.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.hologram.Hologram;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomCancellableEvent;
import org.bukkit.entity.Player;

@AllArgsConstructor
@Getter
public final class HologramSpawnEvent extends CustomCancellableEvent {

    private final Hologram hologram;
    private final Player player;
}