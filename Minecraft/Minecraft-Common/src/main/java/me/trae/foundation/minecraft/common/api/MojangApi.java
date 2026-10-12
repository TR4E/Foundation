package me.trae.foundation.minecraft.common.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import lombok.experimental.UtilityClass;
import me.trae.foundation.utilities.UtilHttp;
import me.trae.foundation.utilities.enums.HttpMethod;
import me.trae.foundation.utilities.exceptions.HttpException;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@UtilityClass
public class MojangApi {

    private static final String USERNAME_URL = "https://api.mojang.com/users/profiles/minecraft/%s";
    private static final String ID_URL = "https://sessionserver.mojang.com/session/minecraft/profile/%s";

    public static Optional<UUID> getIdByUsername(final String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        final String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8).replace("+", "%20");

        final HttpResponse<String> httpResponse = UtilHttp.supply(
                HttpMethod.GET,
                USERNAME_URL.formatted(encodedUsername),
                HttpResponse.BodyHandlers.ofString()
        );

        final Optional<JsonObject> response = parseResponse(httpResponse);

        if (response.isEmpty()) {
            return Optional.empty();
        }

        final Optional<String> id = getString(response.get(), "id");

        if (id.isEmpty() || !id.get().matches("(?i)[0-9a-f]{32}")) {
            return Optional.empty();
        }

        final String value = id.get();

        final String formattedId = "%s-%s-%s-%s-%s".formatted(
                value.substring(0, 8),
                value.substring(8, 12),
                value.substring(12, 16),
                value.substring(16, 20),
                value.substring(20)
        );

        return Optional.of(UUID.fromString(formattedId));
    }

    public static Optional<String> getUsernameById(final UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }

        final String id = uuid.toString().replace("-", "");

        final HttpResponse<String> httpResponse = UtilHttp.supply(
                HttpMethod.GET,
                ID_URL.formatted(id),
                HttpResponse.BodyHandlers.ofString()
        );

        final Optional<JsonObject> response = parseResponse(httpResponse);

        if (response.isEmpty()) {
            return Optional.empty();
        }

        return getString(response.get(), "name");
    }

    private static Optional<JsonObject> parseResponse(final HttpResponse<String> httpResponse) {
        if (httpResponse.statusCode() == 204 || httpResponse.statusCode() == 404) {
            return Optional.empty();
        }

        if (!UtilHttp.isStatus(httpResponse, 200)) {
            throw new HttpException("Mojang API returned HTTP %d".formatted(httpResponse.statusCode()));
        }

        try {
            final JsonElement jsonElement = JsonParser.parseString(httpResponse.body());

            return jsonElement.isJsonObject() ? Optional.of(jsonElement.getAsJsonObject()) : Optional.empty();
        } catch (final JsonParseException exception) {
            return Optional.empty();
        }
    }

    private static Optional<String> getString(final JsonObject jsonObject, final String key) {
        if (!jsonObject.has(key)) {
            return Optional.empty();
        }

        final JsonElement jsonElement = jsonObject.get(key);

        if (!jsonElement.isJsonPrimitive() || !jsonElement.getAsJsonPrimitive().isString()) {
            return Optional.empty();
        }

        final String value = jsonElement.getAsString();

        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}