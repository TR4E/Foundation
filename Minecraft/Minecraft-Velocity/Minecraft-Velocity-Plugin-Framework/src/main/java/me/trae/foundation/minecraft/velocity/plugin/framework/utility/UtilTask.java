package me.trae.foundation.minecraft.velocity.plugin.framework.utility;

import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.velocity.plugin.framework.VelocityPlugin;
import me.trae.foundation.minecraft.velocity.plugin.framework.utility.registry.PluginRegistry;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

@UtilityClass
public class UtilTask {

    public static void execute(final Runnable runnable) {
        runnable.run();
    }

    public static void executeAsynchronous(final VelocityPlugin velocityPlugin, final Runnable runnable) {
        velocityPlugin.getProxyServer().getScheduler().buildTask(velocityPlugin, runnable).schedule();
    }

    public static void executeAsynchronous(final Runnable runnable) {
        executeAsynchronous(PluginRegistry.getSelfPlugin(), runnable);
    }

    public static void executeLaterAsynchronous(final VelocityPlugin velocityPlugin, final int delay, final ChronoUnit chronoUnit, final Runnable runnable) {
        velocityPlugin.getProxyServer().getScheduler().buildTask(velocityPlugin, runnable).delay(Duration.of(delay, chronoUnit)).schedule();
    }

    public static void executeLaterAsynchronous(final int delay, final ChronoUnit chronoUnit, final Runnable runnable) {
        executeLaterAsynchronous(PluginRegistry.getSelfPlugin(), delay, chronoUnit, runnable);
    }

    public static void scheduleAsynchronous(final VelocityPlugin velocityPlugin, final int initialDelay, final int period, final ChronoUnit chronoUnit, final Runnable runnable, final Supplier<Boolean> cancelSupplier) {
        velocityPlugin.getProxyServer().getScheduler().buildTask(velocityPlugin, scheduledTask -> {
            if (cancelSupplier != null && cancelSupplier.get()) {
                scheduledTask.cancel();
                return;
            }

            runnable.run();
        }).delay(Duration.of(initialDelay, chronoUnit)).repeat(Duration.of(period, chronoUnit)).schedule();
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