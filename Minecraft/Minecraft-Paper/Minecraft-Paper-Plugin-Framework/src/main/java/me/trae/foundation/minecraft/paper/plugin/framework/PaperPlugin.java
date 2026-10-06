package me.trae.foundation.minecraft.paper.plugin.framework;

import lombok.CustomLog;
import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.injector.api.callback.ApplicationCallback;
import me.trae.foundation.injector.extensions.configuration.callback.ConfigurationCallback;
import me.trae.foundation.injector.extensions.scheduler.callback.SchedulerCallback;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.AddonRegistry;
import me.trae.foundation.minecraft.paper.plugin.framework.config.events.ConfigReloadEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.config.events.ConfigSaveEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.registry.PluginRegistry;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Executor;

@CustomLog
public abstract class PaperPlugin extends JavaPlugin implements ApplicationCallback, SchedulerCallback, ConfigurationCallback {

    private final AddonRegistry addonRegistry = new AddonRegistry(this);

    @Override
    public final void onEnable() {
        try {
            PluginRegistry.registerInternalPlugin(this);

            Injector.INSTANCE.initialize(this, this.addonRegistry.initialize());

            this.addonRegistry.start();
        } catch (final RuntimeException | Error throwable) {
            this.onApplicationFailure(throwable);
        }
    }

    @Override
    public final void onDisable() {
        this.addonRegistry.shutdown();

        PluginRegistry.unregisterInternalPlugin(this);

        Injector.INSTANCE.shutdown(this);
    }

    @Override
    public final void onComponentRegister(final Object component) {
        if (component instanceof final Listener listener) {
            this.getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    @Override
    public final void onComponentUnregister(final Object component) {
        if (component instanceof final Listener listener) {
            HandlerList.unregisterAll(listener);
        }
    }

    @Override
    public final void onApplicationFailure(final Throwable throwable) {
        LOGGER.error("Failed to start {}", this.getName(), throwable);

        this.getServer().getPluginManager().disablePlugin(this);
    }

    @Override
    public final Executor getSynchronousExecutor() {
        return runnable -> {
            if (this.getServer().isPrimaryThread()) {
                runnable.run();
                return;
            }

            if (this.isEnabled()) {
                this.getServer().getScheduler().runTask(this, runnable);
            }
        };
    }

    @Override
    public final Executor getAsynchronousExecutor() {
        return runnable -> {
            if (this.isEnabled()) {
                this.getServer().getScheduler().runTaskAsynchronously(this, runnable);
            }
        };
    }

    @Override
    public final void onConfigurationSave(final Class<?> type) {
        UtilEvent.dispatch(new ConfigSaveEvent(this, type));
    }

    @Override
    public final void onConfigurationReload(final Class<?> type) {
        UtilEvent.dispatch(new ConfigReloadEvent(this, type));
    }
}