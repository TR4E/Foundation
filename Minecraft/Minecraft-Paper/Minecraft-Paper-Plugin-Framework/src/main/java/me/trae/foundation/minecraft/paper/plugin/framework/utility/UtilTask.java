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
        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null");
        }

        runnable.run();
    }

    public static void executeSynchronously(final PaperPlugin paperPlugin, final Runnable runnable) {
        if (paperPlugin == null) {
            throw new IllegalArgumentException("Paper Plugin cannot be null");
        }

        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null");
        }

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
        if (paperPlugin == null) {
            throw new IllegalArgumentException("Paper Plugin cannot be null");
        }

        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null");
        }

        Bukkit.getServer().getScheduler().runTaskAsynchronously(paperPlugin, runnable);
    }

    public static void executeAsynchronously(final Runnable runnable) {
        executeAsynchronously(PluginRegistry.getSelfPlugin(), runnable);
    }

    public static void executeLaterSynchronously(final PaperPlugin paperPlugin, final int delay, final ChronoUnit chronoUnit, final Runnable runnable) {
        if (paperPlugin == null) {
            throw new IllegalArgumentException("Paper Plugin cannot be null");
        }

        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null");
        }

        final long delayTicks = Duration.of(delay, chronoUnit).toMillis() / 50L;

        Bukkit.getServer().getScheduler().runTaskLater(paperPlugin, runnable, delayTicks);
    }

    public static void executeLaterSynchronously(final int delay, final ChronoUnit chronoUnit, final Runnable runnable) {
        executeLaterSynchronously(PluginRegistry.getSelfPlugin(), delay, chronoUnit, runnable);
    }

    public static void executeLaterAsynchronously(final PaperPlugin paperPlugin, final int delay, final ChronoUnit chronoUnit, final Runnable runnable) {
        if (paperPlugin == null) {
            throw new IllegalArgumentException("Paper Plugin cannot be null");
        }

        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null");
        }

        final long delayTicks = Duration.of(delay, chronoUnit).toMillis() / 50L;

        Bukkit.getServer().getScheduler().runTaskLaterAsynchronously(paperPlugin, runnable, delayTicks);
    }

    public static void executeLaterAsynchronously(final int delay, final ChronoUnit chronoUnit, final Runnable runnable) {
        executeLaterAsynchronously(PluginRegistry.getSelfPlugin(), delay, chronoUnit, runnable);
    }

    public static void schedule(final PaperPlugin paperPlugin, final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable, final Supplier<Boolean> cancelSupplier) {
        if (paperPlugin == null) {
            throw new IllegalArgumentException("Paper Plugin cannot be null.");
        }

        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null.");
        }

        if (initialDelay < 0 || period < 0) {
            throw new IllegalArgumentException("Initial delay and Period must be >= 0.");
        }

        if (chronoUnit == null) {
            throw new IllegalArgumentException("Chrono Unit cannot be null.");
        }

        final long initialDelayTicks = Duration.of(initialDelay, chronoUnit).toMillis() / 50L;
        final long periodTicks = Duration.of(period, chronoUnit).toMillis() / 50L;

        Bukkit.getServer().getScheduler().runTaskTimer(paperPlugin, bukkitTask -> {
            if (cancelSupplier != null && cancelSupplier.get()) {
                bukkitTask.cancel();
                return;
            }

            runnable.run();
        }, initialDelayTicks, periodTicks);
    }

    public static void schedule(final PaperPlugin paperPlugin, final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable) {
        schedule(paperPlugin, initialDelay, period, chronoUnit, runnable, null);
    }

    public static void schedule(final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable, final Supplier<Boolean> cancelSupplier) {
        schedule(PluginRegistry.getSelfPlugin(), initialDelay, period, chronoUnit, runnable, cancelSupplier);
    }

    public static void schedule(final Runnable runnable, final int initialDelay, final int period, final ChronoUnit chronoUnit) {
        schedule(PluginRegistry.getSelfPlugin(), initialDelay, period, chronoUnit, runnable, null);
    }

    public static void scheduleAsynchronous(final PaperPlugin paperPlugin, final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable, final Supplier<Boolean> cancelSupplier) {
        if (paperPlugin == null) {
            throw new IllegalArgumentException("Paper Plugin cannot be null.");
        }

        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null.");
        }

        if (initialDelay < 0 || period < 0) {
            throw new IllegalArgumentException("Initial delay and Period must be >= 0.");
        }

        if (chronoUnit == null) {
            throw new IllegalArgumentException("Chrono Unit cannot be null.");
        }

        final long initialDelayTicks = Duration.of(initialDelay, chronoUnit).toMillis() / 50L;
        final long periodTicks = Duration.of(period, chronoUnit).toMillis() / 50L;

        Bukkit.getServer().getScheduler().runTaskTimerAsynchronously(paperPlugin, bukkitTask -> {
            if (cancelSupplier != null && cancelSupplier.get()) {
                bukkitTask.cancel();
                return;
            }

            runnable.run();
        }, initialDelayTicks, periodTicks);
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