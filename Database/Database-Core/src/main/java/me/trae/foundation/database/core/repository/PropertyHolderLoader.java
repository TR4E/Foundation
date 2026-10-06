package me.trae.foundation.database.core.repository;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.exception.SchemaException;

@UtilityClass
public class PropertyHolderLoader {

    public void load(final Class<?>... propertyHolders) {
        for (final Class<?> propertyHolder : propertyHolders) {
            try {
                Class.forName(propertyHolder.getName(), true, propertyHolder.getClassLoader());
            } catch (final ClassNotFoundException exception) {
                throw new SchemaException("Failed to load property holder %s".formatted(propertyHolder.getName()), exception);
            }
        }
    }
}