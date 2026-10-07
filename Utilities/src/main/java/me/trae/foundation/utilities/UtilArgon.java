package me.trae.foundation.utilities;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import me.trae.foundation.utilities.functional.consumer.TriConsumer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;

@UtilityClass
public class UtilArgon {

    public static final int DEFAULT_ITERATIONS = 3;

    public static final int DEFAULT_PARALLELISM = 2;

    public static final int DEFAULT_MEMORY_KB = 1024 * 64;

    public static final int DEFAULT_MAXIMUM_CACHE_ENTRIES = 4_096;

    private static final String CACHE_DIGEST_ALGORITHM = "HmacSHA256";

    private static final Mac CACHE_DIGEST_MAC = createCacheDigestMac();

    private static final Argon2 ARGON = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    private static final ConcurrentHashMap<String, Cache> VERIFY_CACHE_MAP = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private static volatile Consumer<String> verifyInvalidator = VERIFY_CACHE_MAP::remove;

    @Getter
    @Setter
    private static volatile int iterations = DEFAULT_ITERATIONS;

    @Getter
    @Setter
    private static volatile int parallelism = DEFAULT_PARALLELISM;

    @Getter
    @Setter
    private static volatile int memoryKb = DEFAULT_MEMORY_KB;

    @Getter
    @Setter
    private static volatile long cacheTtlMs = TimeUnit.MINUTES.toMillis(15L);

    @Getter
    @Setter
    private static volatile int maximumCacheEntries = DEFAULT_MAXIMUM_CACHE_ENTRIES;

    @Getter
    private static volatile int maximumConcurrency = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

    private static volatile Semaphore argonSemaphore = new Semaphore(maximumConcurrency);

    @Getter
    @Setter
    private static volatile BiFunction<String, String, Boolean> verifyGetter = (storedHash, digestKey) -> {
        final Cache cache = VERIFY_CACHE_MAP.get(storedHash);

        if (cache == null) {
            return null;
        }

        if (cache.getExpiresAt() <= System.currentTimeMillis()) {
            VERIFY_CACHE_MAP.remove(storedHash, cache);
            return null;
        }

        return MessageDigest.isEqual(cache.getDigestKey().getBytes(StandardCharsets.UTF_8), digestKey.getBytes(StandardCharsets.UTF_8)) ? Boolean.TRUE : null;
    };

    @Getter
    @Setter
    private static volatile TriConsumer<String, String, Boolean> verifySetter = (storedHash, digestKey, verified) -> {
        if (!Boolean.TRUE.equals(verified)) {
            return;
        }

        if (VERIFY_CACHE_MAP.size() >= maximumCacheEntries) {
            evictExpiredCacheEntries();

            if (VERIFY_CACHE_MAP.size() >= maximumCacheEntries) {
                return;
            }
        }

        VERIFY_CACHE_MAP.put(storedHash, new Cache(digestKey, System.currentTimeMillis() + cacheTtlMs));
    };

    public static void setMaximumConcurrency(final int newMaximumConcurrency) {
        if (newMaximumConcurrency < 1) {
            throw new IllegalArgumentException("Maximum concurrency must be at least one.");
        }

        maximumConcurrency = newMaximumConcurrency;

        argonSemaphore = new Semaphore(newMaximumConcurrency);
    }

    public static String hash(final String plainPassword, final String pepper, final String salt) {
        final byte[] combinedInput = buildInput(plainPassword, pepper, salt);

        try {
            final Semaphore semaphore = argonSemaphore;

            semaphore.acquire();

            try {
                return ARGON.hash(iterations, memoryKb, parallelism, combinedInput);
            } finally {
                semaphore.release();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException("Interrupted while hashing password.", e);
        } finally {
            Arrays.fill(combinedInput, (byte) 0);
        }
    }

    public static boolean verify(final String plainPassword, final String pepper, final String salt, final String storedHash) {
        final byte[] combinedInput = buildInput(plainPassword, pepper, salt);

        try {
            final String digestKey = digest(combinedInput);

            final Boolean cachedResult = verifyGetter.apply(storedHash, digestKey);

            if (cachedResult != null) {
                return cachedResult;
            }

            final Semaphore semaphore = argonSemaphore;

            semaphore.acquire();

            try {
                final boolean result = ARGON.verify(storedHash, combinedInput);

                verifySetter.accept(storedHash, digestKey, result);

                return result;
            } finally {
                semaphore.release();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException("Interrupted while verifying password.", e);
        } finally {
            Arrays.fill(combinedInput, (byte) 0);
        }
    }

    public static String tryReHash(final String plainPassword, final String pepper, final String salt, final String storedHash) {
        if (!ARGON.needsRehash(storedHash, iterations, memoryKb, parallelism)) {
            return null;
        }

        return hash(plainPassword, pepper, salt);
    }

    public static void invalidateVerifyCache(final String passwordHash) {
        verifyInvalidator.accept(passwordHash);
    }

    private static byte[] buildInput(final String plainPassword, final String pepper, final String salt) {
        final byte[] passwordBytes = plainPassword.getBytes(StandardCharsets.UTF_8);
        final byte[] saltBytes = salt.getBytes(StandardCharsets.UTF_8);
        final byte[] pepperBytes = pepper.getBytes(StandardCharsets.UTF_8);

        final byte[] combinedInput = new byte[12 + pepperBytes.length + passwordBytes.length + saltBytes.length];

        int offset = 0;

        offset = writeSegment(combinedInput, offset, pepperBytes);
        offset = writeSegment(combinedInput, offset, passwordBytes);

        writeSegment(combinedInput, offset, saltBytes);

        Arrays.fill(passwordBytes, (byte) 0);
        Arrays.fill(saltBytes, (byte) 0);
        Arrays.fill(pepperBytes, (byte) 0);

        return combinedInput;
    }

    private static int writeSegment(final byte[] target, final int offset, final byte[] segment) {
        final int segmentLength = segment.length;

        target[offset] = (byte) (segmentLength >>> 24);
        target[offset + 1] = (byte) (segmentLength >>> 16);
        target[offset + 2] = (byte) (segmentLength >>> 8);
        target[offset + 3] = (byte) segmentLength;

        System.arraycopy(segment, 0, target, offset + 4, segmentLength);

        return offset + 4 + segmentLength;
    }

    private static String digest(final byte[] combinedInput) {
        final byte[] digest;

        try {
            digest = Mac.class.cast(CACHE_DIGEST_MAC.clone()).doFinal(combinedInput);
        } catch (final CloneNotSupportedException e) {
            throw new IllegalStateException("HMAC-SHA256 does not support cloning.", e);
        }

        try {
            return Base64.getEncoder().encodeToString(digest);
        } finally {
            Arrays.fill(digest, (byte) 0);
        }
    }

    private static Mac createCacheDigestMac() {
        final byte[] keyBytes = new byte[32];

        new SecureRandom().nextBytes(keyBytes);

        try {
            final Mac mac = Mac.getInstance(CACHE_DIGEST_ALGORITHM);

            mac.init(new SecretKeySpec(keyBytes, CACHE_DIGEST_ALGORITHM));

            return mac;
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available.", e);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    private static void evictExpiredCacheEntries() {
        final long now = System.currentTimeMillis();

        VERIFY_CACHE_MAP.entrySet().removeIf(entry -> entry.getValue().getExpiresAt() <= now);
    }

    @AllArgsConstructor
    @Getter
    private static class Cache {

        private final String digestKey;
        private final long expiresAt;
    }
}