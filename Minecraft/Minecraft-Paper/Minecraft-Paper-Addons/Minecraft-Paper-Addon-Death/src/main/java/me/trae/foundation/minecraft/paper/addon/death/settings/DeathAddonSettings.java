package me.trae.foundation.minecraft.paper.addon.death.settings;

import me.trae.foundation.minecraft.common.color.ChatColor;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.settings.AddonSettings;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.awt.Color;

public interface DeathAddonSettings extends AddonSettings {

    default boolean showDeathMessagesForEntity(final LivingEntity entity, final Entity killer) {
        return entity instanceof Player;
    }

    default Color getDefaultCauseNameColor() {
        return ChatColor.YELLOW.getColor();
    }

    default Color getDefaultReasonNameColor() {
        return ChatColor.GREEN.getColor();
    }
}