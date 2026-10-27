package me.trae.foundation.minecraft.paper.addon.window.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.window.Window;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomCancellableEvent;
import org.bukkit.entity.Player;

@AllArgsConstructor
@Getter
public final class WindowCloseEvent extends CustomCancellableEvent {

    private final Window<?> window;
    private final Player player;
}