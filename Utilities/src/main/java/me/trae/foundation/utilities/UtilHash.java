package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@UtilityClass
public class UtilHash {

    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private static final ConcurrentHashMap<String, MessageDigest> MESSAGE_DIGEST_CACHE_MAP = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, Mac> MAC_CACHE_MAP = new ConcurrentHashMap<>();

    public static byte[] hashToBytes(final String algorithm, final byte[] bytes) {
        return getMessageDigest(algorithm).digest(bytes);
    }

    public static byte[] hashToBytes(final String algorithm, final String string) {
        return hashToBytes(algorithm, string.getBytes(StandardCharsets.UTF_8));
    }

    public static String hashToString(final String algorithm, final byte[] bytes) {
        return toHex(hashToBytes(algorithm, bytes));
    }

    public static String hashToString(final String algorithm, final String string) {
        return hashToString(algorithm, string.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean verify(final String algorithm, final byte[] plainBytes, final byte[] storedBytes) {
        return MessageDigest.isEqual(storedBytes, hashToBytes(algorithm, plainBytes));
    }

    public static boolean verify(final String algorithm, final String plainString, final String storedString) {
        final byte[] storedBytes = fromHex(storedString);
        final byte[] computed = hashToBytes(algorithm, plainString);

        return MessageDigest.isEqual(storedBytes, computed);
    }

    public static String hmac(final String algorithm, final String key, final String input) {
        try {
            final Mac mac = getMac(algorithm);

            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm));

            final byte[] hashBytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));

            return UtilBase64.encodeToString(hashBytes);
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to compute HMAC using %s".formatted(algorithm), e);
        }
    }

    public static String toHex(final byte[] bytes) {
        return HEX_FORMAT.formatHex(bytes);
    }

    public static byte[] fromHex(final String hex) {
        return HEX_FORMAT.parseHex(hex);
    }

    private static MessageDigest getMessageDigest(final String algorithm) {
        try {
            return MessageDigest.class.cast(MESSAGE_DIGEST_CACHE_MAP.computeIfAbsent(algorithm.toUpperCase(Locale.ROOT), key -> {
                try {
                    return MessageDigest.getInstance(key);
                } catch (final NoSuchAlgorithmException e) {
                    throw new IllegalStateException("Unsupported MessageDigest algorithm: %s".formatted(key), e);
                }
            }).clone());
        } catch (final CloneNotSupportedException e) {
            throw new IllegalStateException("MessageDigest does not support cloning: %s".formatted(algorithm), e);
        }
    }

    private static Mac getMac(final String algorithm) {
        try {
            return Mac.class.cast(MAC_CACHE_MAP.computeIfAbsent(algorithm.toUpperCase(Locale.ROOT), key -> {
                try {
                    return Mac.getInstance(key);
                } catch (final NoSuchAlgorithmException e) {
                    throw new IllegalStateException("Unsupported Mac algorithm: %s".formatted(key), e);
                }
            }).clone());
        } catch (final CloneNotSupportedException e) {
            throw new IllegalStateException("Mac does not support cloning: %s".formatted(algorithm), e);
        }
    }
}