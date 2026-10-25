package me.trae.foundation.minecraft.paper.addon.npc.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.npc.Npc;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomCancellableEvent;
import org.bukkit.Location;

@AllArgsConstructor
@Getter
public final class NpcPreRestoreEvent extends CustomCancellableEvent {

    private final Npc<?> npc;
    private final Location location;
}