package me.trae.foundation.minecraft.velocity.plugin.framework.plugin.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.velocity.plugin.framework.VelocityPlugin;
import me.trae.foundation.minecraft.velocity.plugin.framework.event.CustomEvent;

@AllArgsConstructor
@Getter
public final class PluginShutdownEvent extends CustomEvent {

    private final VelocityPlugin velocityPlugin;
}