package me.trae.foundation.minecraft.velocity.plugin.framework.utility;

import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.velocity.plugin.framework.VelocityPlugin;
import me.trae.foundation.minecraft.velocity.plugin.framework.event.internal.Event;
import me.trae.foundation.minecraft.velocity.plugin.framework.utility.registry.PluginRegistry;

import java.util.concurrent.CompletableFuture;

@UtilityClass
public class UtilEvent {

    public static <T extends Event> void dispatch(final VelocityPlugin velocityPlugin, final T event) {
        if (velocityPlugin == null) {
            throw new IllegalArgumentException("Velocity Plugin cannot be null");
        }

        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        velocityPlugin.getProxyServer().getEventManager().fireAndForget(event);
    }

    public static <T extends Event> void dispatch(final T event) {
        dispatch(PluginRegistry.getSelfPlugin(), event);
    }

    public static <R extends Event> CompletableFuture<R> supply(final VelocityPlugin velocityPlugin, final R event) {
        if (velocityPlugin == null) {
            throw new IllegalArgumentException("Velocity Plugin cannot be null");
        }

        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        return velocityPlugin.getProxyServer().getEventManager().fire(event);
    }

    public static <R extends Event> CompletableFuture<R> supply(final R event) {
        return supply(PluginRegistry.getSelfPlugin(), event);
    }
}