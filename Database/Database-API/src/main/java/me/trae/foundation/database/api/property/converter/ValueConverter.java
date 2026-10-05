package me.trae.foundation.database.api.property.converter;

public interface ValueConverter<Value, Stored> {

    Class<Value> getValueType();

    Class<Stored> getStoredType();

    Stored serialize(final Value value);

    Value deserialize(final Stored stored);
}