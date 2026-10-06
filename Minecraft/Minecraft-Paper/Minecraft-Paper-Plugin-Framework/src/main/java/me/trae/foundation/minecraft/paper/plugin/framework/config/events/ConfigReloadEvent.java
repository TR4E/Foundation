package me.trae.foundation.minecraft.paper.plugin.framework.config.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.PaperPlugin;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;

@AllArgsConstructor
@Getter
public final class ConfigReloadEvent extends CustomEvent {

    private final PaperPlugin paperPlugin;
    private final Class<?> configurationClass;
}