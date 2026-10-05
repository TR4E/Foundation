package me.trae.foundation.injector.extensions.configuration.serializer.yaml;

import me.trae.foundation.injector.extensions.configuration.field.FieldResolver;
import me.trae.foundation.injector.extensions.configuration.serializer.ConfigurationSerializer;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.representer.Representer;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class YamlConfigurationSerializer implements ConfigurationSerializer {

    private static final DumperOptions DUMPER_OPTIONS = createDumperOptions();

    @Override
    public String serialize(final Object instance) {
        return YamlCommentProcessor.inject(new Yaml(DUMPER_OPTIONS).dump(toMap(instance)), instance.getClass());
    }

    @Override
    public <T> T deserialize(final String content, final Class<T> type) {
        final Representer representer = new Representer(DUMPER_OPTIONS);

        representer.getPropertyUtils().setSkipMissingProperties(true);

        final Yaml yaml = new Yaml(
                new Constructor(type, new LoaderOptions()),
                representer,
                DUMPER_OPTIONS
        );

        yaml.setBeanAccess(BeanAccess.FIELD);

        return yaml.loadAs(content, type);
    }

    private static DumperOptions createDumperOptions() {
        final DumperOptions dumperOptions = new DumperOptions();

        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setIndent(2);

        return dumperOptions;
    }

    private static Map<String, Object> toMap(final Object instance) {
        final Map<String, Object> map = new LinkedHashMap<>();

        for (final Field field : FieldResolver.getFields(instance.getClass())) {
            map.put(field.getName(), toValue(FieldResolver.getValue(field, instance)));
        }

        return map;
    }

    private static Object toValue(final Object value) {
        return switch (value) {
            case null -> null;
            case final Enum<?> enumValue -> enumValue.name();
            case final Map<?, ?> mapValue -> {
                final Map<Object, Object> result = new LinkedHashMap<>();

                mapValue.forEach((key, entryValue) -> result.put(toValue(key), toValue(entryValue)));

                yield result;
            }
            case final Collection<?> collectionValue -> collectionValue.stream().map(YamlConfigurationSerializer::toValue).toList();
            default -> value.getClass().isArray() ? toArray(value) : value.getClass().getName().startsWith("java.") ? value : toMap(value);
        };
    }

    private static List<Object> toArray(final Object array) {
        final List<Object> result = new ArrayList<>();

        for (int index = 0; index < Array.getLength(array); index++) {
            result.add(toValue(Array.get(array, index)));
        }

        return result;
    }
}