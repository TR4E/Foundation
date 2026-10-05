package me.trae.foundation.injector.extensions.configuration.field;

import lombok.experimental.UtilityClass;
import me.trae.foundation.injector.extensions.configuration.exception.ConfigurationException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public final class FieldResolver {

    public List<Field> getFields(final Class<?> type) {
        final List<Field> fieldList = new ArrayList<>();

        for (Class<?> clazz = type; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (final Field field : clazz.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }

                field.setAccessible(true);

                fieldList.add(field);
            }
        }

        return fieldList;
    }

    public boolean isNested(final Class<?> type) {
        if (type.isPrimitive() || type.isArray() || type.isEnum()) {
            return false;
        }

        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            return false;
        }

        if (type.getName().startsWith("java.")) {
            return false;
        }

        return true;
    }

    public Object getValue(final Field field, final Object instance) {
        try {
            return field.get(instance);
        } catch (final IllegalAccessException exception) {
            throw new ConfigurationException("Failed to read field %s".formatted(field.getName()), exception);
        }
    }

    public void copy(final Object source, final Object target) {
        for (final Field field : getFields(source.getClass())) {
            try {
                field.set(target, field.get(source));
            } catch (final IllegalAccessException exception) {
                throw new ConfigurationException("Failed to copy field %s".formatted(field.getName()), exception);
            }
        }
    }
}