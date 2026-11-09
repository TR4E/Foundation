package me.trae.foundation.discord.webhook.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.trae.foundation.utilities.UtilJava;
import me.trae.foundation.utilities.builder.JsonMapBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public final class DiscordField implements JsonMapBuilder<String, Object> {

    private String name, value;
    private boolean inline;

    public DiscordField(final String name, final String value) {
        this(name, value, false);
    }

    @Override
    public Map<String, Object> toJsonMap() {
        return UtilJava.createMap(new LinkedHashMap<>(), map -> {
            map.put("name", this.name);
            map.put("value", this.value);
            map.put("inline", this.inline);
        });
    }
}