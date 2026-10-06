package me.trae.foundation.database.storage.codec;

import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.property.EntityPropertyRegistry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EntityCodec<E extends Entity> {

    private final Class<E> entityType;
    private final EntityInstantiator<E> entityInstantiator;

    private volatile List<EntityProperty<?, ?>> propertyList;

    public EntityCodec(final Class<E> entityType) {
        this.entityType = entityType;
        this.entityInstantiator = new EntityInstantiator<>(entityType);
    }

    public List<EntityProperty<?, ?>> getProperties() {
        if (this.propertyList == null) {
            final List<EntityProperty<?, ?>> resolved = EntityPropertyRegistry.getProperties(this.entityType);

            if (resolved.isEmpty()) {
                throw new SchemaException("No properties registered for %s, load its property holder first".formatted(this.entityType.getName()));
            }

            this.propertyList = resolved;
        }

        return this.propertyList;
    }

    public Map<String, String> encode(final E entity) {
        return this.encode(entity, this.getProperties());
    }

    public Map<String, String> encode(final E entity, final Collection<? extends EntityProperty<?, ?>> entityProperties) {
        final Map<String, String> fieldMap = new LinkedHashMap<>();

        for (final EntityProperty<?, ?> entityProperty : entityProperties) {
            fieldMap.put(entityProperty.getName(), this.encodeProperty(entityProperty, entity));
        }

        return fieldMap;
    }

    public E decode(final UUID id, final Map<String, String> fieldMap) {
        final E entity = this.entityInstantiator.instantiate(id);

        for (final EntityProperty<?, ?> entityProperty : this.getProperties()) {
            final String raw = fieldMap.get(entityProperty.getName());

            if (raw != null) {
                this.decodeProperty(entityProperty, entity, raw);
            }
        }

        return entity;
    }

    private <T extends Entity, V> String encodeProperty(final EntityProperty<T, V> entityProperty, final Entity entity) {
        return ValueCodec.encode(entityProperty, entityProperty.getValue(this.cast(entityProperty, entity)));
    }

    private <T extends Entity, V> void decodeProperty(final EntityProperty<T, V> entityProperty, final Entity entity, final String raw) {
        entityProperty.setValue(this.cast(entityProperty, entity), ValueCodec.decode(entityProperty, raw));
    }

    private <T extends Entity> T cast(final EntityProperty<T, ?> entityProperty, final Entity entity) {
        if (!entityProperty.getEntityType().isInstance(entity)) {
            throw new SchemaException("Property %s belongs to %s, not %s".formatted(entityProperty.getName(), entityProperty.getEntityType().getName(), entity.getClass().getName()));
        }

        return entityProperty.getEntityType().cast(entity);
    }
}