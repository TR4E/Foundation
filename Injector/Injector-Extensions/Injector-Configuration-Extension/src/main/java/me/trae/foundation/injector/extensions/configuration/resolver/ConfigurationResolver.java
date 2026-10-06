package me.trae.foundation.injector.extensions.configuration.resolver;

import me.trae.foundation.injector.extensions.configuration.annotation.Configuration;
import me.trae.foundation.injector.extensions.configuration.callback.ConfigurationCallback;
import me.trae.foundation.injector.extensions.configuration.entry.ConfigurationEntry;
import me.trae.foundation.injector.extensions.configuration.enums.ConfigType;
import me.trae.foundation.injector.extensions.configuration.exception.ConfigurationException;
import me.trae.foundation.injector.extensions.configuration.field.FieldResolver;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

public final class ConfigurationResolver {

    private final LinkedHashMap<Class<?>, ConfigurationEntry> entryMap = new LinkedHashMap<>();

    public synchronized Object load(final Class<?> applicationClass, final ConfigurationCallback configurationCallback, final Class<?> type) {
        final Configuration configuration = type.getAnnotation(Configuration.class);

        final Path path = configurationCallback.getDataFolder().toPath().resolve(configuration.value() + configuration.type().getExtension()).toAbsolutePath();

        final ConfigurationEntry configurationEntry = new ConfigurationEntry(
                applicationClass,
                configurationCallback,
                path,
                configuration.type(),
                this.read(path, configuration.type(), type).orElseGet(() -> this.createDefault(type))
        );

        this.write(configurationEntry);

        this.entryMap.put(type, configurationEntry);

        return configurationEntry.getInstance();
    }

    public synchronized void reloadAllConfigurations() {
        List.copyOf(this.entryMap.values()).forEach(this::reload);
    }

    public synchronized void reloadConfigurations(final Class<?> applicationClass) {
        this.entryMap.values().stream()
                .filter(entry -> entry.getApplicationClass() == applicationClass)
                .toList()
                .forEach(this::reload);
    }

    public synchronized void reloadConfiguration(final Class<?> type) {
        this.reload(this.getEntry(type));
    }

    public synchronized void saveConfiguration(final Class<?> type) {
        final ConfigurationEntry configurationEntry = this.getEntry(type);

        this.write(configurationEntry);

        configurationEntry.getConfigurationCallback().onConfigurationSave(type);
    }

    public synchronized void removeConfiguration(final Class<?> type) {
        this.entryMap.remove(type);
    }

    public synchronized void remove(final Class<?> applicationClass) {
        this.entryMap.values().removeIf(entry -> entry.getApplicationClass() == applicationClass);
    }

    private void reload(final ConfigurationEntry configurationEntry) {
        this.read(
                configurationEntry.getPath(),
                configurationEntry.getConfigType(),
                configurationEntry.getInstance().getClass()
        ).ifPresent(loaded -> FieldResolver.copy(loaded, configurationEntry.getInstance()));

        this.write(configurationEntry);

        configurationEntry.getConfigurationCallback().onConfigurationReload(configurationEntry.getInstance().getClass());
    }

    private Optional<Object> read(final Path path, final ConfigType configType, final Class<?> type) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(configType.getSerializer().deserialize(Files.readString(path), type));
        } catch (final IOException | RuntimeException exception) {
            throw new ConfigurationException("Failed to read configuration %s".formatted(path), exception);
        }
    }

    private void write(final ConfigurationEntry entry) {
        try {
            Files.createDirectories(entry.getPath().getParent());

            Files.writeString(entry.getPath(), entry.getConfigType().getSerializer().serialize(entry.getInstance()));
        } catch (final IOException exception) {
            throw new ConfigurationException("Failed to write configuration %s".formatted(entry.getPath()), exception);
        }
    }

    private Object createDefault(final Class<?> type) {
        try {
            final Constructor<?> constructor = type.getDeclaredConstructor();

            constructor.setAccessible(true);

            return constructor.newInstance();
        } catch (final ReflectiveOperationException exception) {
            throw new ConfigurationException("%s must declare a no-args constructor".formatted(type.getName()), exception);
        }
    }

    private ConfigurationEntry getEntry(final Class<?> type) {
        final ConfigurationEntry configurationEntry = this.entryMap.get(type);
        if (configurationEntry == null) {
            throw new ConfigurationException("%s is not a loaded configuration".formatted(type.getName()));
        }

        return configurationEntry;
    }
}