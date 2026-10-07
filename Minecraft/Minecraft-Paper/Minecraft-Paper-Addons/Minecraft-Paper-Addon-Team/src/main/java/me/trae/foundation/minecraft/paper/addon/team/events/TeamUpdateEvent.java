package me.trae.foundation.minecraft.paper.addon.team.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;
import org.bukkit.entity.Player;

@AllArgsConstructor
@Getter
public final class TeamUpdateEvent extends CustomEvent {

    private final String identifier;
    private final Player player;

    public TeamUpdateEvent(final Player player) {
        this(null, player);
    }
}