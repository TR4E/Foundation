package me.trae.foundation.database.redis.codec;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.entity.EntityInstantiator;
import me.trae.foundation.database.api.property.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
public final class EntityCodec<EntityType extends Entity> {

    private final EntityInstantiator<EntityType> instantiator;
    private final List<Property<?, ?>> properties;

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
        if (raw == null) {
            return;
        }

        property.set(property.getEntityType().cast(entity), ValueCodec.decode(property, raw));
    }

    public List<String> getColumns() {
        return this.properties.stream()
                .map(Property::getColumn)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}