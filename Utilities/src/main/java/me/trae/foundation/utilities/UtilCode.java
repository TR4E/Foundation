package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;

import java.security.SecureRandom;

@UtilityClass
public class UtilCode {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final char[] UPPERCASE_ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private static final char[] FULL_ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    public static String generate(final int length, final char[] characters) {
        final char[] output = new char[length];

        for (int i = 0; i < length; i++) {
            output[i] = characters[SECURE_RANDOM.nextInt(characters.length)];
        }

        return new String(output);
    }

    public static String generateUpperCase(final int length) {
        return generate(length, UPPERCASE_ALPHANUMERIC);
    }

    public static String generateRandom(final int length) {
        return generate(length, FULL_ALPHANUMERIC);
    }
}