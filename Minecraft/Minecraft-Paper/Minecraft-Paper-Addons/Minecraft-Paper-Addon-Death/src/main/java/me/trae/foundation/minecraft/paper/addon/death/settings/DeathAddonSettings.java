package me.trae.foundation.minecraft.paper.addon.death.settings;

import me.trae.foundation.minecraft.common.color.ChatColor;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.settings.AddonSettings;

import java.awt.Color;

public interface DeathAddonSettings extends AddonSettings {

    default Color getDefaultCauseNameColor() {
        return ChatColor.YELLOW.getColor();
    }

    default Color getDefaultReasonNameColor() {
        return ChatColor.GREEN.getColor();
    }
}