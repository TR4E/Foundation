package me.trae.foundation.database.api.converter;

public interface ValueConverter<Value, Stored> {

    Class<Stored> getStoredType();

    Stored serialize(final Value value);

    Value deserialize(final Stored stored);
}