package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.paper.plugin.framework.PaperPlugin;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.registry.PluginRegistry;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

import java.util.concurrent.CompletableFuture;

@UtilityClass
public class UtilEvent {

    public static <T extends Event> void dispatch(final T event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        Bukkit.getServer().getPluginManager().callEvent(event);
    }

    public static <T extends Event> void dispatchSynchronously(final PaperPlugin paperPlugin, final T event) {
        if (paperPlugin == null) {
            throw new IllegalArgumentException("Paper Plugin cannot be null");
        }

        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        if (event.isAsynchronous()) {
            throw new IllegalStateException("Cannot dispatch asynchronous event synchronously");
        }

        UtilTask.executeSynchronously(paperPlugin, () -> Bukkit.getServer().getPluginManager().callEvent(event));
    }

    public static <T extends Event> void dispatchSynchronously(final T event) {
        dispatchSynchronously(PluginRegistry.getSelfPlugin(), event);
    }

    public static <T extends Event> void dispatchAsynchronous(final PaperPlugin paperPlugin, final T event) {
        if (paperPlugin == null) {
            throw new IllegalArgumentException("Paper Plugin cannot be null");
        }

        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        if (!event.isAsynchronous()) {
            throw new IllegalStateException("Cannot dispatch synchronous event asynchronously");
        }

        UtilTask.executeAsynchronously(paperPlugin, () -> Bukkit.getServer().getPluginManager().callEvent(event));
    }

    public static <T extends Event> void dispatchAsynchronous(final T event) {
        dispatchAsynchronous(PluginRegistry.getSelfPlugin(), event);
    }

    public static <R extends Event> R supply(final R event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        dispatch(event);

        return event;
    }

    public static <R extends Event> CompletableFuture<R> supplySynchronous(final PaperPlugin paperPlugin, final R event) {
        if (paperPlugin == null) {
            throw new IllegalArgumentException("Paper Plugin cannot be null");
        }

        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        if (event.isAsynchronous()) {
            throw new IllegalStateException("Cannot supply asynchronous event synchronously");
        }

        final CompletableFuture<R> completableFuture = new CompletableFuture<>();

        UtilTask.executeSynchronously(paperPlugin, () -> {
            try {
                Bukkit.getServer().getPluginManager().callEvent(event);
                completableFuture.complete(event);
            } catch (final Exception e) {
                completableFuture.completeExceptionally(e);
            }
        });

        return completableFuture;
    }

    public static <R extends Event> CompletableFuture<R> supplySynchronous(final R event) {
        return supplySynchronous(PluginRegistry.getSelfPlugin(), event);
    }

    public static <R extends Event> CompletableFuture<R> supplyAsynchronous(final PaperPlugin paperPlugin, final R event) {
        if (paperPlugin == null) {
            throw new IllegalArgumentException("Paper Plugin cannot be null");
        }

        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        if (!event.isAsynchronous()) {
            throw new IllegalStateException("Cannot supply synchronous event asynchronously");
        }

        final CompletableFuture<R> completableFuture = new CompletableFuture<>();

        UtilTask.executeAsynchronously(paperPlugin, () -> {
            try {
                Bukkit.getServer().getPluginManager().callEvent(event);
                completableFuture.complete(event);
            } catch (final Exception e) {
                completableFuture.completeExceptionally(e);
            }
        });

        return completableFuture;
    }

    public static <R extends Event> CompletableFuture<R> supplyAsynchronous(final R event) {
        return supplyAsynchronous(PluginRegistry.getSelfPlugin(), event);
    }
}