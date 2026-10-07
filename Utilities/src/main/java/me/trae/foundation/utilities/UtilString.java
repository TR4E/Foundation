package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@UtilityClass
public class UtilString {

    private static final int MAX_CACHE_SIZE = 4096;

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern SLICE_PATTERN = Pattern.compile("[ _.]");

    private static final ConcurrentHashMap<String, String> CLEAN_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> SLICE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> UN_SLICE_CACHE = new ConcurrentHashMap<>();

    public static String clean(final String input) {
        if (isEmpty(input)) {
            return null;
        }

        return cache(CLEAN_CACHE, input.toLowerCase(Locale.ROOT), key -> WHITESPACE_PATTERN.splitAsStream(key.replace('_', ' '))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" ")));
    }

    public static String slice(final String input) {
        if (isEmpty(input)) {
            return null;
        }

        return cache(SLICE_CACHE, input, key -> SLICE_PATTERN.matcher(key).replaceAll(""));
    }

    public static String unSlice(final String input) {
        if (isEmpty(input)) {
            return null;
        }

        return cache(UN_SLICE_CACHE, input, key -> {
            final char[] characters = key.replace('_', ' ').toCharArray();

            final StringBuilder builder = new StringBuilder(characters.length + 8);

            for (int index = 0; index < characters.length; index++) {
                final char current = characters[index];

                if (index > 0 && Character.isUpperCase(current) && !Character.isWhitespace(characters[index - 1])) {
                    final boolean previousIsLower = Character.isLowerCase(characters[index - 1]);

                    final boolean nextIsLower = index + 1 < characters.length && Character.isLowerCase(characters[index + 1]);

                    if (previousIsLower || nextIsLower) {
                        builder.append(' ');
                    }
                }

                builder.append(current);
            }

            return builder.toString();
        });
    }

    public static boolean isEmpty(final String input) {
        return input == null || input.isBlank();
    }

    public static String pair(final String key, final String value) {
        return "%s: %s".formatted(key, value);
    }

    public static String formatToDollarByInteger(final int input) {
        return String.format(Locale.ROOT, "$%,d", input);
    }

    public static String formatToDollarByDouble(final double input) {
        return String.format(Locale.ROOT, "$%,.2f", input);
    }

    public static String withIndefiniteArticle(final String input) {
        return IndefiniteArticle.format(input);
    }

    public static String getIndefiniteArticlePrefix(final String input) {
        return IndefiniteArticle.get(input);
    }

    private static String cache(final ConcurrentHashMap<String, String> map, final String key, final Function<String, String> function) {
        final String cached = map.get(key);

        if (cached != null) {
            return cached;
        }

        final String value = function.apply(key);

        if (map.size() < MAX_CACHE_SIZE) {
            map.putIfAbsent(key, value);
        }

        return value;
    }
}