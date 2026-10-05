package me.trae.foundation.injector.extensions.configuration.serializer.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.trae.foundation.injector.extensions.configuration.serializer.ConfigurationSerializer;

public final class JsonConfigurationSerializer implements ConfigurationSerializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @Override
    public <T> T deserialize(final String content, final Class<T> type) {
        return GSON.fromJson(JsonCommentProcessor.strip(content), type);
    }

    @Override
    public String serialize(final Object instance) {
        return JsonCommentProcessor.inject(GSON.toJson(instance), instance.getClass());
    }
}