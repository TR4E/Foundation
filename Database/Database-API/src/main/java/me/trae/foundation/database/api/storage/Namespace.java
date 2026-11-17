package me.trae.foundation.database.api.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.converter.ValueConverter;

import java.time.Duration;
import java.util.function.Function;

@AllArgsConstructor
@Getter
public final class Namespace<Key, Value> {

    private final String name;
    private final Class<Value> valueType;
    private final Function<Key, String> keyEncoder;
    private final ValueConverter<Value, String> valueConverter;
    private final Duration ttl;
}