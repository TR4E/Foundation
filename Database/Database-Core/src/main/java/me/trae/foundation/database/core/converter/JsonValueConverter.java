package me.trae.foundation.database.core.converter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.property.converter.ValueConverter;
import org.jooq.JSONB;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonValueConverter<Value> implements ValueConverter<Value, JSONB> {

    private static final Gson GSON = new Gson();

    @Getter
    private final Class<Value> valueType;

    private final Type type;

    public static <Value> JsonValueConverter<Value> of(final Class<Value> valueType) {
        return new JsonValueConverter<>(valueType, valueType);
    }

    @SuppressWarnings("unchecked")
    public static <Element> JsonValueConverter<List<Element>> ofList(final Class<Element> elementType) {
        return new JsonValueConverter<>((Class<List<Element>>) (Class<?>) List.class, TypeToken.getParameterized(List.class, elementType).getType());
    }

    @SuppressWarnings("unchecked")
    public static <Element> JsonValueConverter<Set<Element>> ofSet(final Class<Element> elementType) {
        return new JsonValueConverter<>((Class<Set<Element>>) (Class<?>) Set.class, TypeToken.getParameterized(Set.class, elementType).getType());
    }

    @SuppressWarnings("unchecked")
    public static <MapKey, MapValue> JsonValueConverter<Map<MapKey, MapValue>> ofMap(final Class<MapKey> keyType, final Class<MapValue> valueType) {
        return new JsonValueConverter<>((Class<Map<MapKey, MapValue>>) (Class<?>) Map.class, TypeToken.getParameterized(Map.class, keyType, valueType).getType());
    }

    @Override
    public Class<JSONB> getStoredType() {
        return JSONB.class;
    }

    @Override
    public JSONB serialize(final Value value) {
        return JSONB.valueOf(GSON.toJson(value, this.type));
    }

    @Override
    public Value deserialize(final JSONB stored) {
        return GSON.fromJson(stored.data(), this.type);
    }
}