package me.trae.foundation.minecraft.common.message;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.common.color.BaseUtilColor;
import me.trae.foundation.minecraft.common.color.ChatColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.awt.Color;

@UtilityClass
public class Message {

    @Getter
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Getter
    @Setter
    private Color prefixColor = ChatColor.BLUE.getColor();

    @Getter
    @Setter
    private Color messageColor = ChatColor.GRAY.getColor();

    @Getter
    @Setter
    private String prefixFormat = "[%s] ";

    public static Component render(final Component prefix, final Component message) {
        final Component body = message.colorIfAbsent(BaseUtilColor.toTextColor(messageColor));

        if (prefix == null) {
            return body;
        }

        return prefix.colorIfAbsent(BaseUtilColor.toTextColor(prefixColor)).append(body);
    }

    public static Component render(final String prefix, final String message) {
        return render(prefix(prefix), miniMessage.deserialize(message));
    }

    public static Component prefix(final Color color, final String prefix) {
        return prefix == null ? null : Component.text(prefixFormat.formatted(prefix), BaseUtilColor.toTextColor(color));
    }

    public static Component prefix(final String prefix) {
        return prefix(prefixColor, prefix);
    }
}