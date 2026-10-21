package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.paper.plugin.framework.PaperPlugin;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.registry.PluginRegistry;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

@UtilityClass
public class UtilTask {

    public static void execute(final Runnable runnable) {
        runnable.run();
    }

    public static void executeSynchronously(final PaperPlugin paperPlugin, final Runnable runnable) {
        if (Bukkit.getServer().isPrimaryThread()) {
            runnable.run();
            return;
        }

        Bukkit.getServer().getScheduler().runTask(paperPlugin, runnable);
    }

    public static void executeSynchronously(final Runnable runnable) {
        executeSynchronously(PluginRegistry.getSelfPlugin(), runnable);
    }

    public static void executeAsynchronously(final PaperPlugin paperPlugin, final Runnable runnable) {
        Bukkit.getServer().getScheduler().runTaskAsynchronously(paperPlugin, runnable);
    }

    public static void executeAsynchronously(final Runnable runnable) {
        executeAsynchronously(PluginRegistry.getSelfPlugin(), runnable);
    }

    public static void executeLaterSynchronously(final PaperPlugin paperPlugin, final int delay, final ChronoUnit chronoUnit, final Runnable runnable) {
        Bukkit.getServer().getScheduler().runTaskLater(paperPlugin, runnable, Duration.of(delay, chronoUnit).toMillis() / 50L);
    }

    public static void executeLaterSynchronously(final int delay, final ChronoUnit chronoUnit, final Runnable runnable) {
        executeLaterSynchronously(PluginRegistry.getSelfPlugin(), delay, chronoUnit, runnable);
    }

    public static void executeLaterAsynchronously(final PaperPlugin paperPlugin, final int delay, final ChronoUnit chronoUnit, final Runnable runnable) {
        Bukkit.getServer().getScheduler().runTaskLaterAsynchronously(
                paperPlugin,
                runnable,
                Duration.of(delay, chronoUnit).toMillis() / 50L
        );
    }

    public static void executeLaterAsynchronously(final int delay, final ChronoUnit chronoUnit, final Runnable runnable) {
        executeLaterAsynchronously(PluginRegistry.getSelfPlugin(), delay, chronoUnit, runnable);
    }

    public static void schedule(final PaperPlugin paperPlugin, final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable, final Supplier<Boolean> cancelSupplier) {
        Bukkit.getServer().getScheduler().runTaskTimer(
                paperPlugin,
                bukkitTask -> {
                    if (cancelSupplier != null && cancelSupplier.get()) {
                        bukkitTask.cancel();
                        return;
                    }

                    runnable.run();
                },
                Duration.of(initialDelay, chronoUnit).toMillis() / 50L,
                Duration.of(period, chronoUnit).toMillis() / 50L
        );
    }

    public static void schedule(final PaperPlugin paperPlugin, final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable) {
        schedule(paperPlugin, initialDelay, period, chronoUnit, runnable, null);
    }

    public static void schedule(final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable, final Supplier<Boolean> cancelSupplier) {
        schedule(PluginRegistry.getSelfPlugin(), initialDelay, period, chronoUnit, runnable, cancelSupplier);
    }

    public static void schedule(final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable) {
        schedule(PluginRegistry.getSelfPlugin(), initialDelay, period, chronoUnit, runnable, null);
    }

    public static void scheduleAsynchronous(final PaperPlugin paperPlugin, final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable, final Supplier<Boolean> cancelSupplier) {
        Bukkit.getServer().getScheduler().runTaskTimerAsynchronously(
                paperPlugin,
                bukkitTask -> {
                    if (cancelSupplier != null && cancelSupplier.get()) {
                        bukkitTask.cancel();
                        return;
                    }

                    runnable.run();
                },
                Duration.of(initialDelay, chronoUnit).toMillis() / 50L,
                Duration.of(period, chronoUnit).toMillis() / 50L
        );
    }

    public static void scheduleAsynchronous(final PaperPlugin paperPlugin, final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable) {
        scheduleAsynchronous(paperPlugin, initialDelay, period, chronoUnit, runnable, null);
    }

    public static void scheduleAsynchronous(final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable, final Supplier<Boolean> cancelSupplier) {
        scheduleAsynchronous(PluginRegistry.getSelfPlugin(), initialDelay, period, chronoUnit, runnable, cancelSupplier);
    }

    public static void scheduleAsynchronous(final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable) {
        scheduleAsynchronous(PluginRegistry.getSelfPlugin(), initialDelay, period, chronoUnit, runnable, null);
    }
}