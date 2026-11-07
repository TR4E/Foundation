package me.trae.foundation.minecraft.paper.addon.resourcepack.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.resourcepack.ResourcePack;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;
import org.bukkit.entity.Player;

@AllArgsConstructor
@Getter
public final class ResourcePackLoadedEvent extends CustomEvent {

    private final ResourcePack resourcePack;
    private final Player player;
}