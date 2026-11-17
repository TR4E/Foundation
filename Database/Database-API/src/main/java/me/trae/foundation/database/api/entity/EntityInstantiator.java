package me.trae.foundation.database.api.entity;

import me.trae.foundation.database.api.exception.SchemaException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

public final class EntityInstantiator<EntityType extends Entity> {

    private final Constructor<EntityType> constructor;

    public EntityInstantiator(final Class<EntityType> entityType) {
        try {
            this.constructor = entityType.getDeclaredConstructor(UUID.class);

            this.constructor.setAccessible(true);
        } catch (final NoSuchMethodException exception) {
            throw new SchemaException("%s must declare a constructor taking only its UUID id".formatted(entityType.getName()), exception);
        }
    }

    public EntityType instantiate(final UUID id) {
        try {
            return this.constructor.newInstance(id);
        } catch (final ReflectiveOperationException exception) {
            throw new SchemaException("Failed to create %s".formatted(this.constructor.getDeclaringClass().getName()), exception instanceof final InvocationTargetException invocationTargetException ? invocationTargetException.getCause() : exception);
        }
    }
}