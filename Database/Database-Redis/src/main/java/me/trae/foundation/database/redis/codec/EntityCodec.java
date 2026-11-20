package me.trae.foundation.database.redis.codec;

import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.entity.EntityInstantiator;
import me.trae.foundation.database.api.property.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
public final class EntityCodec<EntityType extends Entity> {

    private final List<String> columns = new ArrayList<>();

    private final EntityInstantiator<EntityType> instantiator;
    private final List<Property<?, ?>> properties;

    public EntityCodec(final EntityInstantiator<EntityType> instantiator, final List<Property<?, ?>> properties) {
        this.instantiator = instantiator;
        this.properties = properties;

        properties.forEach(property -> this.columns.add(property.getColumn()));
    }

    public Map<String, String> encode(final EntityType entity) {
        final LinkedHashMap<String, String> fieldMap = new LinkedHashMap<>();

        this.properties.forEach(property -> put(fieldMap, property, entity));

        return fieldMap;
    }

    public EntityType decode(final UUID id, final Map<String, String> fieldMap) {
        final EntityType entity = this.instantiator.instantiate(id);

        this.properties.forEach(property -> set(property, entity, fieldMap.get(property.getColumn())));

        return entity;
    }

    private static <Owner, Value> void put(final LinkedHashMap<String, String> fieldMap, final Property<Owner, Value> property, final Object entity) {
        final String encoded = ValueCodec.encode(property, property.get(property.getEntityType().cast(entity)));

        if (encoded != null) {
            fieldMap.put(property.getColumn(), encoded);
        }
    }

    private static <Owner, Value> void set(final Property<Owner, Value> property, final Object entity, final String raw) {
        property.set(property.getEntityType().cast(entity), raw == null ? null : ValueCodec.decode(property, raw));
    }
}