package me.trae.foundation.injector.extensions.configuration.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.injector.extensions.configuration.serializer.ConfigurationSerializer;
import me.trae.foundation.injector.extensions.configuration.serializer.json.JsonConfigurationSerializer;
import me.trae.foundation.injector.extensions.configuration.serializer.yaml.YamlConfigurationSerializer;

@AllArgsConstructor
@Getter
public enum ConfigType {

    JSON(".json", new JsonConfigurationSerializer()),
    YAML(".yml", new YamlConfigurationSerializer());

    private final String extension;
    private final ConfigurationSerializer serializer;
}