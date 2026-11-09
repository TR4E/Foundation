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
public final class DiscordAuthor implements JsonMapBuilder<String, Object> {

    private String name, url, iconUrl;

    public DiscordAuthor(final String name) {
        this(name, null, null);
    }

    @Override
    public Map<String, Object> toJsonMap() {
        return UtilJava.createMap(new LinkedHashMap<>(), map -> {
            map.put("name", this.name);
            map.put("url", this.url);
            map.put("icon_url", this.iconUrl);
        });
    }
}