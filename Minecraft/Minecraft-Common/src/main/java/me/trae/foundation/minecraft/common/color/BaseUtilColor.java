package me.trae.foundation.minecraft.common.color;

import net.kyori.adventure.text.format.TextColor;

import java.awt.Color;

public abstract class BaseUtilColor {

    public static String serialize(final Color color, final String string) {
        if (color == null) {
            return string;
        }

        final String colorTag;

        final String chatColorName = ChatColor.getNameByRgb(color.getRGB());

        if (chatColorName != null) {
            colorTag = chatColorName.toLowerCase();
        } else {
            colorTag = "#%06x".formatted(color.getRGB() & 0xFFFFFF);
        }

        return "<%s>%s</%s>".formatted(colorTag, string, colorTag);
    }

    public static TextColor toTextColor(final Color color) {
        if (color == null) {
            return null;
        }

        return TextColor.color(color.getRGB() & 0xFFFFFF);
    }
}