package me.trae.foundation.injector.extensions.configuration;

import me.trae.foundation.injector.api.callback.ApplicationCallback;
import me.trae.foundation.injector.api.extension.Extension;
import me.trae.foundation.injector.extensions.configuration.annotation.Configuration;
import me.trae.foundation.injector.extensions.configuration.resolver.ConfigurationResolver;

import java.util.Optional;

public final class ConfigurationExtension implements Extension {

    private final ConfigurationResolver configurationResolver = new ConfigurationResolver();

    @Override
    public boolean isComponent(final Class<?> type) {
        return type.isAnnotationPresent(Configuration.class);
    }

    @Override
    public <T> Optional<T> instantiate(final Class<?> applicationClass, final ApplicationCallback applicationCallback, final Class<T> type) {
        if (!this.isComponent(type)) {
            return Optional.empty();
        }

        return Optional.of(type.cast(this.configurationResolver.load(applicationClass, applicationCallback.getDataFolder(), type)));
    }

    @Override
    public void onApplicationShutdown(final Class<?> applicationClass, final ApplicationCallback applicationCallback) {
        this.configurationResolver.remove(applicationClass);
    }

    public void reloadAllConfigurations() {
        this.configurationResolver.reloadAllConfigurations();
    }

    public void reloadConfigurations(final Class<?> applicationClass) {
        this.configurationResolver.reloadConfigurations(applicationClass);
    }

    public void reloadConfiguration(final Class<?> type) {
        this.configurationResolver.reloadConfiguration(type);
    }

    public void saveConfiguration(final Class<?> type) {
        this.configurationResolver.saveConfiguration(type);
    }
}