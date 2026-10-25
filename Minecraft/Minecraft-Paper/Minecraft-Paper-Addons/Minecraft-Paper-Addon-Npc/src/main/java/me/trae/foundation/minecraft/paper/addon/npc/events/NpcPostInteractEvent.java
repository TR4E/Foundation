package me.trae.foundation.minecraft.paper.addon.npc.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.npc.Npc;
import me.trae.foundation.minecraft.paper.addon.npc.types.enums.NpcInteractType;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

@AllArgsConstructor
@Getter
public final class NpcPostInteractEvent extends CustomEvent {

    private final Npc<?> npc;
    private final Player player;
    private final Entity entity;
    private final NpcInteractType interactType;
}