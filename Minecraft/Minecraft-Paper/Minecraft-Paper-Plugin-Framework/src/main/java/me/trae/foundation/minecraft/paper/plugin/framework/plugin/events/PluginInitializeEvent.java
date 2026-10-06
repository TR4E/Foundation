package me.trae.foundation.minecraft.paper.plugin.framework.plugin.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.PaperPlugin;
import me.trae.foundation.minecraft.paper.plugin.framework.event.CustomEvent;

@AllArgsConstructor
@Getter
public final class PluginInitializeEvent extends CustomEvent {

    private final PaperPlugin paperPlugin;
}