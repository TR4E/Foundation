package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

@UtilityClass
public class KeyDerivation {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public static byte[] derive(final String context) {
        try {
            return hkdf(context, 32);
        } catch (final GeneralSecurityException exception) {
            throw new RuntimeException("%s not available".formatted(HMAC_ALGORITHM), exception);
        }
    }

    private static byte[] hkdf(final String context, final int keyLength) throws GeneralSecurityException {
        final Mac mac = Mac.getInstance(HMAC_ALGORITHM);

        final byte[] salt = new byte[mac.getMacLength()];

        mac.init(new SecretKeySpec(salt, HMAC_ALGORITHM));

        final byte[] ikm = new byte[mac.getMacLength()];

        final byte[] prk = mac.doFinal(ikm);

        mac.init(new SecretKeySpec(prk, HMAC_ALGORITHM));

        mac.update(context.getBytes(StandardCharsets.UTF_8));

        mac.update((byte) 0x01);

        final byte[] okm = mac.doFinal();

        Arrays.fill(prk, (byte) 0);

        return Arrays.copyOf(okm, keyLength);
    }
}