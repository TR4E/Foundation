package me.trae.foundation.minecraft.velocity.plugin.framework.config.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.velocity.plugin.framework.VelocityPlugin;
import me.trae.foundation.minecraft.velocity.plugin.framework.event.CustomEvent;

@AllArgsConstructor
@Getter
public final class ConfigReloadEvent extends CustomEvent {

    private final VelocityPlugin velocityPlugin;
    private final Class<?> configurationClass;
}