package me.trae.foundation.minecraft.paper.addon.npc.types;

import net.kyori.adventure.text.Component;

public interface NameableNpc {

    Component getName();

    default boolean isNameAlwaysVisible() {
        return true;
    }
}