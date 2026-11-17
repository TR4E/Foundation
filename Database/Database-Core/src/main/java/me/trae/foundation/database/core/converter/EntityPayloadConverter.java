package me.trae.foundation.database.core.converter;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.converter.ValueConverter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.redis.codec.EntityCodec;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
public final class EntityPayloadConverter<EntityType extends Entity> implements ValueConverter<EntityType, String> {

    private final EntityCodec<EntityType> codec;

    @Override
    public Class<String> getStoredType() {
        return String.class;
    }

    @Override
    public String serialize(final EntityType value) {
        return value.getId() + "\n" + this.codec.encode(value).entrySet().stream()
                .map(entry -> entry.getKey() + "\t" + Base64.getEncoder().encodeToString(entry.getValue().getBytes(StandardCharsets.UTF_8)))
                .collect(Collectors.joining("\n"));
    }

    @Override
    public EntityType deserialize(final String stored) {
        final String[] lines = stored.split("\n");
        final LinkedHashMap<String, String> fieldMap = new LinkedHashMap<>();

        Arrays.stream(lines, 1, lines.length).forEach(line -> put(fieldMap, line));

        return this.codec.decode(UUID.fromString(lines[0]), fieldMap);
    }

    private static void put(final LinkedHashMap<String, String> fieldMap, final String line) {
        final String[] parts = line.split("\t", 2);

        fieldMap.put(parts[0], new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8));
    }
}