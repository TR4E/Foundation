package me.trae.foundation.minecraft.paper.addon.npc.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.npc.Npc;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomCancellableEvent;
import org.bukkit.entity.Entity;

@AllArgsConstructor
@Getter
public final class NpcPreDespawnEvent extends CustomCancellableEvent {

    private final Npc<?> npc;
    private final Entity entity;
}