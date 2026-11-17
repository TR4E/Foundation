package me.trae.foundation.database.core.converter;

import me.trae.foundation.database.api.converter.ValueConverter;

import java.util.UUID;

public final class UuidConverter implements ValueConverter<UUID, String> {

    @Override
    public Class<String> getStoredType() {
        return String.class;
    }

    @Override
    public String serialize(final UUID value) {
        return value.toString();
    }

    @Override
    public UUID deserialize(final String stored) {
        return UUID.fromString(stored);
    }
}