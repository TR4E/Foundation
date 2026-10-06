package me.trae.foundation.database.storage.codec;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.SchemaException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

public class EntityInstantiator<E extends Entity> {

    private final Constructor<E> constructor;

    public EntityInstantiator(final Class<E> entityType) {
        try {
            this.constructor = entityType.getDeclaredConstructor(UUID.class);

            this.constructor.setAccessible(true);
        } catch (final NoSuchMethodException exception) {
            throw new SchemaException("%s must declare a constructor taking only its UUID id".formatted(entityType.getName()), exception);
        }
    }

    public E instantiate(final UUID id) {
        try {
            return this.constructor.newInstance(id);
        } catch (final ReflectiveOperationException exception) {
            throw new SchemaException("Failed to create %s".formatted(this.constructor.getDeclaringClass().getName()), exception instanceof final InvocationTargetException invocationTargetException ? invocationTargetException.getCause() : exception);
        }
    }
}