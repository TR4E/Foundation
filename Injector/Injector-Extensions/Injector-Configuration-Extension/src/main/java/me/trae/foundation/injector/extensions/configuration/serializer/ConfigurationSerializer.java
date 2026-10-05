package me.trae.foundation.injector.extensions.configuration.serializer;

public interface ConfigurationSerializer {

    String serialize(final Object instance);

    <T> T deserialize(final String content, final Class<T> type);
}