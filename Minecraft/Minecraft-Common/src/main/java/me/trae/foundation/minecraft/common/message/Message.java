package me.trae.foundation.minecraft.common.message;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import me.trae.foundation.minecraft.common.color.BaseUtilColor;
import me.trae.foundation.minecraft.common.color.ChatColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class Message {

    private final Pattern JSON_PATTERN = Pattern.compile("<json>(.+?)</json>", Pattern.DOTALL);

    @Getter
    private static final GsonComponentSerializer gsonComponentSerializer = GsonComponentSerializer.gson();

    @Getter
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Getter
    @Setter
    private Color prefixColor = ChatColor.BLUE.getColor();

    @Getter
    @Setter
    private Color messageColor = ChatColor.GRAY.getColor();

    @Getter
    @Setter
    private String prefixFormat = "[%s] ";

    public static String serialize(final Component component) {
        return miniMessage.serialize(component);
    }

    public static String serializeWithReset(final Component component) {
        return serialize(component) + "<reset>";
    }

    public static String serializeToJson(final Component component) {
        return gsonComponentSerializer.serialize(component);
    }

    public static Component deserialize(final String string) {
        if (isJsonObject(string)) {
            return gsonComponentSerializer.deserialize(string);
        }

        final Matcher matcher = JSON_PATTERN.matcher(string);

        if (!matcher.find()) {
            return miniMessage.deserialize(string);
        }

        final TagResolver.Builder builder = TagResolver.builder();

        final StringBuilder output = new StringBuilder();

        int index = 0;

        do {
            final String name = "json".repeat(index + 1);

            index++;

            builder.resolver(Placeholder.component(name, gsonComponentSerializer.deserialize(matcher.group(1))));

            matcher.appendReplacement(output, Matcher.quoteReplacement("<%s>".formatted(name)));
        } while (matcher.find());

        matcher.appendTail(output);

        return miniMessage.deserialize(output.toString(), builder.build());
    }

    public static String escape(final String string) {
        return string.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'");
    }

    public static Component render(final Component prefix, final Component message) {
        if (prefix == null) {
            return message;
        }

        return prefix.colorIfAbsent(BaseUtilColor.toTextColor(prefixColor)).append(message.colorIfAbsent(BaseUtilColor.toTextColor(messageColor)));
    }

    public static Component render(final String prefix, final String message) {
        return render(prefix(prefix), deserialize(message));
    }

    public static Component prefix(final Color color, final String prefix) {
        return prefix == null ? null : Component.text(prefixFormat.formatted(prefix), BaseUtilColor.toTextColor(color));
    }

    public static Component prefix(final String prefix) {
        return prefix(prefixColor, prefix);
    }

    private static boolean isJsonObject(final String string) {
        try {
            return JsonParser.parseString(string).isJsonObject();
        } catch (final JsonSyntaxException exception) {
            return false;
        }
    }
}