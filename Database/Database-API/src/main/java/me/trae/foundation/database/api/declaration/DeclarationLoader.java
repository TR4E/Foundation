package me.trae.foundation.database.api.declaration;

import lombok.experimental.UtilityClass;
import me.trae.foundation.database.api.exception.SchemaException;

@UtilityClass
public final class DeclarationLoader {

    public void load(final Class<?>... declarations) {
        for (final Class<?> declaration : declarations) {
            try {
                Class.forName(declaration.getName(), true, declaration.getClassLoader());
            } catch (final ClassNotFoundException exception) {
                throw new SchemaException("Failed to load declaration %s".formatted(declaration.getName()), exception);
            }
        }
    }
}