package me.trae.foundation.minecraft.velocity.plugin.framework.utility;

import com.velocitypowered.api.scheduler.ScheduledTask;
import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.velocity.plugin.framework.VelocityPlugin;
import me.trae.foundation.minecraft.velocity.plugin.framework.utility.registry.PluginRegistry;

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

    public static void executeAsynchronous(final VelocityPlugin velocityPlugin, final Runnable runnable) {
        if (velocityPlugin == null) {
            throw new IllegalArgumentException("Velocity Plugin cannot be null");
        }

        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null");
        }

        velocityPlugin.getProxyServer().getScheduler().buildTask(velocityPlugin, runnable).schedule();
    }

    public static void executeAsynchronous(final Runnable runnable) {
        executeAsynchronous(PluginRegistry.getSelfPlugin(), runnable);
    }

    public static void executeLaterAsynchronous(final VelocityPlugin velocityPlugin, final int delay, final ChronoUnit chronoUnit, final Runnable runnable) {
        if (velocityPlugin == null) {
            throw new IllegalArgumentException("Velocity Plugin cannot be null");
        }

        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null");
        }

        final Duration delayDuration = Duration.of(delay, chronoUnit);

        velocityPlugin.getProxyServer().getScheduler().buildTask(velocityPlugin, runnable).delay(delayDuration).schedule();
    }

    public static void executeLaterAsynchronous(final int delay, final ChronoUnit chronoUnit, final Runnable runnable) {
        executeLaterAsynchronous(PluginRegistry.getSelfPlugin(), delay, chronoUnit, runnable);
    }

    public static void scheduleAsynchronous(final VelocityPlugin velocityPlugin, final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable, final Supplier<Boolean> cancelSupplier) {
        if (velocityPlugin == null) {
            throw new IllegalArgumentException("Velocity Plugin cannot be null");
        }

        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null");
        }

        if (initialDelay < 0 || period < 0) {
            throw new IllegalArgumentException("Initial delay and Period must be >= 0");
        }

        if (chronoUnit == null) {
            throw new IllegalArgumentException("Chrono Unit cannot be null");
        }

        final Duration initialDelayDuration = Duration.of(initialDelay, chronoUnit);
        final Duration periodDuration = Duration.of(period, chronoUnit);

        velocityPlugin.getProxyServer().getScheduler().buildTask(velocityPlugin, (final ScheduledTask scheduledTask) -> {
            if (cancelSupplier != null && cancelSupplier.get()) {
                scheduledTask.cancel();
                return;
            }

            runnable.run();
        }).delay(initialDelayDuration).repeat(periodDuration).schedule();
    }

    public static void scheduleAsynchronous(final VelocityPlugin velocityPlugin, final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable) {
        scheduleAsynchronous(velocityPlugin, initialDelay, period, chronoUnit, runnable, null);
    }

    public static void scheduleAsynchronous(final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable, final Supplier<Boolean> cancelSupplier) {
        scheduleAsynchronous(PluginRegistry.getSelfPlugin(), initialDelay, period, chronoUnit, runnable, cancelSupplier);
    }

    public static void scheduleAsynchronous(final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable) {
        scheduleAsynchronous(PluginRegistry.getSelfPlugin(), initialDelay, period, chronoUnit, runnable, null);
    }
}