package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;

import java.util.function.Predicate;

@UtilityClass
public class UtilType {

    public static boolean isAllMatch(final String input, final Predicate<Character> predicate) {
        return input != null && !input.isEmpty() && input.chars().allMatch(value -> predicate.test((char) value));
    }

    public static boolean isAnyMatch(final String input, final Predicate<Character> predicate) {
        return input != null && input.chars().anyMatch(value -> predicate.test((char) value));
    }

    public static boolean isAlphabetic(final String input) {
        return isAllMatch(input, Character::isLetter);
    }

    public static boolean isNumeric(final String input) {
        return isAllMatch(input, Character::isDigit);
    }

    public static boolean isInteger(final String input) {
        if (input != null) {
            try {
                Integer.parseInt(input);
                return true;
            } catch (final NumberFormatException ignored) {
            }
        }

        return false;
    }

    public static boolean isDouble(final String input) {
        if (input != null) {
            try {
                return Double.isFinite(Double.parseDouble(input));
            } catch (final NumberFormatException ignored) {
            }
        }

        return false;
    }

    public static boolean isFloat(final String input) {
        if (input != null) {
            try {
                return Float.isFinite(Float.parseFloat(input));
            } catch (final NumberFormatException ignored) {
            }
        }

        return false;
    }

    public static boolean isLong(final String input) {
        if (input != null) {
            try {
                Long.parseLong(input);
                return true;
            } catch (final NumberFormatException ignored) {
            }
        }

        return false;
    }
}