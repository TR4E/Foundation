package me.trae.foundation.database.core.converter;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.property.converter.ValueConverter;

@AllArgsConstructor
@Getter
public final class EnumValueConverter<Value extends Enum<Value>> implements ValueConverter<Value, String> {

    private final Class<Value> valueType;

    @Override
    public Class<String> getStoredType() {
        return String.class;
    }

    @Override
    public String serialize(final Value value) {
        return value.name();
    }

    @Override
    public Value deserialize(final String stored) {
        return Enum.valueOf(this.valueType, stored);
    }
}