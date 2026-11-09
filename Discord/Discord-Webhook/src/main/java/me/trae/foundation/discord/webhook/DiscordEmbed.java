package me.trae.foundation.discord.webhook;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.trae.foundation.discord.webhook.data.DiscordAuthor;
import me.trae.foundation.discord.webhook.data.DiscordField;
import me.trae.foundation.discord.webhook.data.DiscordFooter;
import me.trae.foundation.utilities.UtilJava;
import me.trae.foundation.utilities.builder.JsonMapBuilder;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public final class DiscordEmbed implements JsonMapBuilder<String, Object> {

    private String title, description, url;
    private Color color;
    private Instant timestamp = Instant.now();
    private List<DiscordField> fields = new ArrayList<>();
    private DiscordAuthor author;
    private DiscordFooter footer;

    public DiscordEmbed(final String title, final String description, final Color color) {
        this.title = title;
        this.description = description;
        this.color = color;
    }

    public DiscordEmbed(final String title, final String description) {
        this(title, description, null);
    }

    public DiscordEmbed(final String title) {
        this(title, null, null);
    }

    public void addFields(final List<DiscordField> fields) {
        this.fields.addAll(fields);
    }

    public void addField(final DiscordField field) {
        this.fields.add(field);
    }

    @Override
    public Map<String, Object> toJsonMap() {
        return UtilJava.createMap(new LinkedHashMap<>(), map -> {
            map.put("title", this.title);
            map.put("description", this.description);
            map.put("url", this.url);
            map.put("color", this.color != null ? this.color.getRGB() & 0xFFFFFF : null);
            map.put("timestamp", this.timestamp != null ? this.timestamp.toString() : null);
            map.put("author", this.author != null ? this.author.toJsonMap() : null);
            map.put("footer", this.footer != null ? this.footer.toJsonMap() : null);
            map.put("fields", !this.fields.isEmpty() ? this.fields.stream().map(DiscordField::toJsonMap).toList() : null);
        });
    }
}