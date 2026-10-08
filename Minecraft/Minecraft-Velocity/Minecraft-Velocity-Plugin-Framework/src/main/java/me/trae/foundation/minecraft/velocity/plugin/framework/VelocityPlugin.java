package me.trae.foundation.minecraft.velocity.plugin.framework;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.AllArgsConstructor;
import lombok.CustomLog;
import lombok.Getter;
import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.injector.api.callback.ApplicationCallback;
import me.trae.foundation.injector.extensions.configuration.callback.ConfigurationCallback;
import me.trae.foundation.injector.extensions.scheduler.callback.SchedulerCallback;
import me.trae.foundation.minecraft.velocity.plugin.framework.command.BaseCommand;
import me.trae.foundation.minecraft.velocity.plugin.framework.config.events.ConfigReloadEvent;
import me.trae.foundation.minecraft.velocity.plugin.framework.config.events.ConfigSaveEvent;
import me.trae.foundation.minecraft.velocity.plugin.framework.event.internal.Listener;
import me.trae.foundation.minecraft.velocity.plugin.framework.plugin.events.PluginInitializeEvent;
import me.trae.foundation.minecraft.velocity.plugin.framework.plugin.events.PluginShutdownEvent;
import me.trae.foundation.minecraft.velocity.plugin.framework.provider.ProxyServerProvider;
import me.trae.foundation.minecraft.velocity.plugin.framework.utility.UtilEvent;
import me.trae.foundation.minecraft.velocity.plugin.framework.utility.registry.CommandRegistry;
import me.trae.foundation.minecraft.velocity.plugin.framework.utility.registry.PluginRegistry;

import java.io.File;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;

@CustomLog
@AllArgsConstructor
@Getter
public abstract class VelocityPlugin implements ApplicationCallback, SchedulerCallback, ConfigurationCallback {

    private final ProxyServer proxyServer;
    private final Path dataDirectory;

    @Subscribe
    public final void onProxyInitialize(final ProxyInitializeEvent event) {
        try {
            PluginRegistry.registerInternalPlugin(this);

            Injector.INSTANCE.initialize(this, List.of(ProxyServerProvider.class));

            UtilEvent.dispatch(this, new PluginInitializeEvent(this));
        } catch (final RuntimeException | Error throwable) {
            this.onApplicationFailure(throwable);
        }
    }

    @Subscribe
    public final void onProxyShutdown(final ProxyShutdownEvent event) {
        UtilEvent.dispatch(this, new PluginShutdownEvent(this));

        PluginRegistry.unregisterInternalPlugin(this);

        Injector.INSTANCE.shutdown(this);
    }

    @Override
    public final File getDataFolder() {
        return this.dataDirectory.toFile();
    }

    @Override
    public final Comparator<Class<?>> getComponentSorter() {
        final Comparator<Class<?>> componentSorter = ApplicationCallback.super.getComponentSorter();

        return (first, second) -> {
            if (BaseCommand.class.isAssignableFrom(first) && BaseCommand.class.isAssignableFrom(second)) {
                return BaseCommand.COMPARATOR_ORDER.compare(first, second);
            }

            return componentSorter.compare(first, second);
        };
    }

    @Override
    public final void onComponentRegister(final Object component) {
        if (component instanceof final Listener listener) {
            this.proxyServer.getEventManager().register(this, listener);
        }

        if (component instanceof final BaseCommand<?, ?> baseCommand) {
            CommandRegistry.registerCommand(this, baseCommand);
        }
    }

    @Override
    public final void onComponentUnregister(final Object component) {
        if (component instanceof final Listener listener) {
            this.proxyServer.getEventManager().unregisterListener(this, listener);
        }

        if (component instanceof final BaseCommand<?, ?> baseCommand) {
            CommandRegistry.unregisterCommand(this, baseCommand);
        }
    }

    @Override
    public final void onApplicationFailure(final Throwable throwable) {
        LOGGER.error("Failed to start {}", this.getClass().getSimpleName(), throwable);
    }

    @Override
    public final Executor getSynchronousExecutor() {
        return Runnable::run;
    }

    @Override
    public final Executor getAsynchronousExecutor() {
        return runnable -> this.proxyServer.getScheduler().buildTask(this, runnable).schedule();
    }

    @Override
    public final void onConfigurationSave(final Class<?> type) {
        UtilEvent.dispatch(this, new ConfigSaveEvent(this, type));
    }

    @Override
    public final void onConfigurationReload(final Class<?> type) {
        UtilEvent.dispatch(this, new ConfigReloadEvent(this, type));
    }
}