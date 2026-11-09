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
public final class DiscordFooter implements JsonMapBuilder<String, Object> {

    private String text, iconUrl;

    public DiscordFooter(final String text) {
        this(text, null);
    }

    @Override
    public Map<String, Object> toJsonMap() {
        return UtilJava.createMap(new LinkedHashMap<>(), map -> {
            map.put("text", this.text);
            map.put("icon_url", this.iconUrl);
        });
    }
}