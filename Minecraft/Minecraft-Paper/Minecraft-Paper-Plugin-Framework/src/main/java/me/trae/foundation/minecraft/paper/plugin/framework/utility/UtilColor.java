package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.common.color.BaseUtilColor;

import java.awt.Color;

@UtilityClass
public class UtilColor extends BaseUtilColor {

    public static org.bukkit.Color toBukkitColor(final Color color) {
        if (color == null) {
            return null;
        }

        return org.bukkit.Color.fromRGB(color.getRGB() & 0xFFFFFF);
    }
}