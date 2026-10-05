package me.trae.foundation.injector.extensions.configuration.serializer.yaml;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public final class YamlPathSegment {

    private final int indent;
    private final String key;
}