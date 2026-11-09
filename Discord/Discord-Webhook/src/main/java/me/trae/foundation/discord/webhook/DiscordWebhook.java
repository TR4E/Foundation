package me.trae.foundation.discord.webhook;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.utilities.UtilHttp;
import me.trae.foundation.utilities.UtilJava;
import me.trae.foundation.utilities.UtilJson;
import me.trae.foundation.utilities.builder.JsonMapBuilder;
import me.trae.foundation.utilities.enums.HttpMethod;

import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@AllArgsConstructor
@Getter
public final class DiscordWebhook implements JsonMapBuilder<String, Object> {

    private static final String CONTENT_TYPE = "application/json";

    private final String url, content;

    private final List<DiscordEmbed> embeds;

    public DiscordWebhook(final String url, final String content) {
        this(url, content, Collections.emptyList());
    }

    public DiscordWebhook(final String url, final List<DiscordEmbed> embeds) {
        this(url, null, embeds);
    }

    public DiscordWebhook(final String url, final String content, final DiscordEmbed embed) {
        this(url, content, Collections.singletonList(embed));
    }

    public DiscordWebhook(final String url, final DiscordEmbed embed) {
        this(url, null, embed);
    }

    public void dispatchSynchronous(final Consumer<HttpResponse<Void>> successConsumer, final Consumer<Throwable> errorConsumer) {
        UtilHttp.dispatch(
                HttpMethod.POST,
                this.url,
                UtilJson.toJson(this.toJsonMap()),
                CONTENT_TYPE,
                null,
                HttpResponse.BodyHandlers.discarding(),
                successConsumer,
                errorConsumer
        );
    }

    public void dispatchSynchronous() {
        this.dispatchSynchronous(null, null);
    }

    public void dispatchAsynchronous(final Consumer<HttpResponse<Void>> successConsumer, final Consumer<Throwable> errorConsumer) {
        UtilHttp.dispatchAsynchronous(
                HttpMethod.POST,
                this.url,
                UtilJson.toJson(this.toJsonMap()),
                CONTENT_TYPE,
                null,
                HttpResponse.BodyHandlers.discarding(),
                successConsumer,
                errorConsumer
        );
    }

    public void dispatchAsynchronous() {
        this.dispatchAsynchronous(null, null);
    }

    @Override
    public Map<String, Object> toJsonMap() {
        return UtilJava.createMap(new LinkedHashMap<>(), map -> {
            map.put("content", this.content);
            map.put("embeds", !this.embeds.isEmpty() ? this.embeds.stream().map(DiscordEmbed::toJsonMap).toList() : null);
        });
    }
}