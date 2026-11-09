package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@UtilityClass
public class UtilJson {

    public static String toJson(final Object value) {
        switch (value) {
            case null -> {
                return "null";
            }

            case final Map<?, ?> map -> {
                return map.entrySet().stream()
                        .filter(entry -> entry.getValue() != null)
                        .map(entry -> "%s:%s".formatted(toJson(String.valueOf(entry.getKey())), toJson(entry.getValue())))
                        .collect(Collectors.joining(",", "{", "}"));
            }

            case final Collection<?> collection -> {
                return collection.stream().filter(Objects::nonNull).map(UtilJson::toJson).collect(Collectors.joining(",", "[", "]"));
            }

            default -> {}
        }

        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }

        return escape(String.valueOf(value));
    }

    public static String escape(final String value) {
        final StringBuilder stringBuilder = new StringBuilder(value.length() + 2).append('"');

        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);

            switch (character) {
                case '"' -> stringBuilder.append("\\\"");
                case '\\' -> stringBuilder.append("\\\\");
                case '\n' -> stringBuilder.append("\\n");
                case '\r' -> stringBuilder.append("\\r");
                case '\t' -> stringBuilder.append("\\t");
                case '\b' -> stringBuilder.append("\\b");
                case '\f' -> stringBuilder.append("\\f");
                default -> stringBuilder.append(character < 0x20 ? "\\u%04x".formatted((int) character) : character);
            }
        }

        return stringBuilder.append('"').toString();
    }
}