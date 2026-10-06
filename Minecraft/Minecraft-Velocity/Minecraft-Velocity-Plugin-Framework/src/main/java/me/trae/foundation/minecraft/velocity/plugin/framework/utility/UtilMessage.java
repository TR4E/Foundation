package me.trae.foundation.minecraft.velocity.plugin.framework.utility;

import com.velocitypowered.api.proxy.ProxyServer;
import lombok.experimental.UtilityClass;
import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.minecraft.common.message.BaseUtilMessage;
import net.kyori.adventure.text.Component;

import java.util.Collection;
import java.util.UUID;

@UtilityClass
public class UtilMessage extends BaseUtilMessage {

    public static void broadcast(final Component prefix, final Component message, final Collection<UUID> ignored) {
        message(Injector.INSTANCE.get(ProxyServer.class).getAllPlayers(), prefix, message, ignored);
    }

    public static void broadcast(final Component prefix, final String message, final Collection<UUID> ignored) {
        message(Injector.INSTANCE.get(ProxyServer.class).getAllPlayers(), prefix, message, ignored);
    }

    public static void broadcast(final String prefix, final String message, final Collection<UUID> ignored) {
        message(Injector.INSTANCE.get(ProxyServer.class).getAllPlayers(), prefix, message, ignored);
    }

    public static void broadcast(final String prefix, final Component message, final Collection<UUID> ignored) {
        message(Injector.INSTANCE.get(ProxyServer.class).getAllPlayers(), prefix, message, ignored);
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
        message(Injector.INSTANCE.get(ProxyServer.class).getConsoleCommandSource(), prefix, message);
    }

    public static void log(final String message) {
        message(Injector.INSTANCE.get(ProxyServer.class).getConsoleCommandSource(), message);
    }
}