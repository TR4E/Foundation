package me.trae.foundation.injector.extensions.configuration;

import me.trae.foundation.injector.api.callback.ApplicationCallback;
import me.trae.foundation.injector.api.extension.Extension;
import me.trae.foundation.injector.extensions.configuration.annotation.Configuration;
import me.trae.foundation.injector.extensions.configuration.callback.ConfigurationCallback;
import me.trae.foundation.injector.extensions.configuration.resolver.ConfigurationResolver;

import java.util.List;
import java.util.Optional;

public final class ConfigurationExtension implements Extension {

    private static final ConfigurationCallback DEFAULT_CALLBACK = new ConfigurationCallback() {};

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

        final ConfigurationCallback configurationCallback = applicationCallback instanceof final ConfigurationCallback callback ? callback : DEFAULT_CALLBACK;

        return Optional.of(type.cast(this.configurationResolver.load(applicationClass, configurationCallback, type)));
    }

    @Override
    public void onApplicationShutdown(final Class<?> applicationClass, final ApplicationCallback applicationCallback) {
        this.configurationResolver.remove(applicationClass);
    }

    @Override
    public void onComponentsDetach(final Class<?> applicationClass, final ApplicationCallback applicationCallback, final List<Object> componentList) {
        componentList.forEach(component -> this.configurationResolver.removeConfiguration(component.getClass()));
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