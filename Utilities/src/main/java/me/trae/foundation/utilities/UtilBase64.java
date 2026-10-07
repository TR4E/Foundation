package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@UtilityClass
public class UtilBase64 {

    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    public static byte[] encodeToBytes(final byte[] input) {
        return ENCODER.encode(input);
    }

    public static byte[] encodeToBytes(final String input) {
        return encodeToBytes(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String encodeToString(final byte[] input) {
        return ENCODER.encodeToString(input);
    }

    public static String encodeToString(final String input) {
        return encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] decodeToBytes(final byte[] input) {
        return DECODER.decode(input);
    }

    public static byte[] decodeToBytes(final String input) {
        return decodeToBytes(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeToString(final byte[] input) {
        return new String(decodeToBytes(input), StandardCharsets.UTF_8);
    }

    public static String decodeToString(final String input) {
        return decodeToString(input.getBytes(StandardCharsets.UTF_8));
    }
}