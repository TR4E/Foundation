package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.common.message.BaseUtilMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;

import java.util.Collection;
import java.util.UUID;

@UtilityClass
public class UtilMessage extends BaseUtilMessage {

    public static void broadcast(final Component prefix, final Component message, final Collection<UUID> ignored) {
        message(Bukkit.getServer().getOnlinePlayers(), prefix, message, ignored);
    }

    public static void broadcast(final Component prefix, final String message, final Collection<UUID> ignored) {
        message(Bukkit.getServer().getOnlinePlayers(), prefix, message, ignored);
    }

    public static void broadcast(final String prefix, final String message, final Collection<UUID> ignored) {
        message(Bukkit.getServer().getOnlinePlayers(), prefix, message, ignored);
    }

    public static void broadcast(final String prefix, final Component message, final Collection<UUID> ignored) {
        message(Bukkit.getServer().getOnlinePlayers(), prefix, message, ignored);
    }

    public static void broadcast(final Component prefix, final Component message) {
        broadcast(prefix, message, null);
    }

    public static void broadcast(final Component prefix, final String message) {
        broadcast(prefix, message, null);
    }

    public static void broadcast(final String prefix, final String message) {
        broadcast(prefix, message, null);
    }

    public static void broadcast(final String prefix, final Component message) {
        broadcast(prefix, message, null);
    }

    public static void log(final String prefix, final String message) {
        message(Bukkit.getServer().getConsoleSender(), prefix, message);
    }

    public static void log(final String message) {
        message(Bukkit.getServer().getConsoleSender(), message);
    }
}