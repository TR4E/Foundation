package me.trae.foundation.minecraft.paper.addon.window.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.addon.window.Window;
import me.trae.foundation.minecraft.paper.addon.window.element.Button;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomCancellableEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

@AllArgsConstructor
@Getter
public final class ButtonPreClickEvent extends CustomCancellableEvent {

    private final Window<?> window;
    private final Button<?> button;
    private final Player player;
    private final ClickType clickType;
}