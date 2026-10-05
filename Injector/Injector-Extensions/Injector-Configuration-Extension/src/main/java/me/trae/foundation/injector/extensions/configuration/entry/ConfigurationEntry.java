package me.trae.foundation.injector.extensions.configuration.entry;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.injector.extensions.configuration.enums.ConfigType;

import java.nio.file.Path;

@AllArgsConstructor
@Getter
public final class ConfigurationEntry {

    private final Class<?> applicationClass;
    private final Path path;
    private final ConfigType configType;
    private final Object instance;
}