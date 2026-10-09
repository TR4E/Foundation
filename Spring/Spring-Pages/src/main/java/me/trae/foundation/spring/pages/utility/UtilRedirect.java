package me.trae.foundation.spring.pages.utility;

import lombok.experimental.UtilityClass;

@UtilityClass
public class UtilRedirect {

    public static boolean isSafe(final String redirect) {
        if (redirect == null || redirect.isBlank() || !redirect.startsWith("/")) {
            return false;
        }

        if (redirect.contains("{") || redirect.contains("\n") || redirect.contains("\r")) {
            return false;
        }

        final char second = redirect.length() > 1 ? redirect.charAt(1) : 0;

        return second != '/' && second != '\\';
    }

    public static String sanitise(final String redirect) {
        return isSafe(redirect) ? redirect : null;
    }
}