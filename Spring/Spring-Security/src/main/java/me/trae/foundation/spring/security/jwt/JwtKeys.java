package me.trae.foundation.spring.security.jwt;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import lombok.CustomLog;
import lombok.Getter;
import me.trae.foundation.spring.common.FoundationProperties;
import me.trae.foundation.spring.security.jwt.enums.TokenType;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

@CustomLog
@Getter
public final class JwtKeys {

    private static final String KEY_ALGORITHM = "Ed25519";
    private static final int SEED_BYTE_LENGTH = 32;

    private final KeyPair accessTokenKeyPair, refreshTokenKeyPair;

    private final JwtParser accessTokenParser, refreshTokenParser;

    public JwtKeys(final JwtProperties jwtProperties, final FoundationProperties foundationProperties) {
        this.accessTokenKeyPair = resolveKeyPair(jwtProperties.getAccessTokenKeySeed(), foundationProperties.isProduction(), TokenType.ACCESS_TOKEN);
        this.refreshTokenKeyPair = resolveKeyPair(jwtProperties.getRefreshTokenKeySeed(), foundationProperties.isProduction(), TokenType.REFRESH_TOKEN);

        jwtProperties.setAccessTokenKeySeed(null);
        jwtProperties.setRefreshTokenKeySeed(null);

        this.accessTokenParser = buildParser(jwtProperties, this.accessTokenKeyPair);
        this.refreshTokenParser = buildParser(jwtProperties, this.refreshTokenKeyPair);
    }

    public PrivateKey getSigningKey(final TokenType tokenType) {
        return switch (tokenType) {
            case ACCESS_TOKEN -> this.accessTokenKeyPair.getPrivate();
            case REFRESH_TOKEN -> this.refreshTokenKeyPair.getPrivate();
        };
    }

    public JwtParser getParser(final TokenType tokenType) {
        return switch (tokenType) {
            case ACCESS_TOKEN -> this.accessTokenParser;
            case REFRESH_TOKEN -> this.refreshTokenParser;
        };
    }

    private static JwtParser buildParser(final JwtProperties jwtProperties, final KeyPair keyPair) {
        return Jwts.parser()
                .clockSkewSeconds(jwtProperties.getClockSkew().toSeconds())
                .requireIssuer(jwtProperties.getIssuer())
                .requireAudience(jwtProperties.getAudience())
                .verifyWith(keyPair.getPublic())
                .build();
    }

    private static KeyPair resolveKeyPair(final String seed, final boolean production, final TokenType tokenType) {
        if (seed == null || seed.isBlank()) {
            if (production) {
                throw new IllegalStateException("%s key seed is required in production for: %s".formatted(KEY_ALGORITHM, tokenType.getKey()));
            }

            LOGGER.warn("No {} key seed configured for {}, generating an ephemeral key pair", KEY_ALGORITHM, tokenType.getKey());

            return generateKeyPair();
        }

        final byte[] bytes = decodeSeed(seed, tokenType);

        try {
            return deriveKeyPair(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static byte[] decodeSeed(final String seed, final TokenType tokenType) {
        final byte[] bytes;

        try {
            bytes = Base64.getDecoder().decode(seed);
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException("%s key seed for %s is not valid Base64".formatted(KEY_ALGORITHM, tokenType.getKey()), exception);
        }

        if (bytes.length != SEED_BYTE_LENGTH) {
            Arrays.fill(bytes, (byte) 0);

            throw new IllegalArgumentException("%s key seed for %s must decode to exactly %s bytes".formatted(KEY_ALGORITHM, tokenType.getKey(), SEED_BYTE_LENGTH));
        }

        return bytes;
    }

    private static KeyPair deriveKeyPair(final byte[] seed) {
        try {
            final Ed25519PrivateKeyParameters privateKeyParameters = new Ed25519PrivateKeyParameters(seed, 0);
            final KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);

            final PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(PrivateKeyInfoFactory.createPrivateKeyInfo(privateKeyParameters).getEncoded()));
            final PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(privateKeyParameters.generatePublicKey()).getEncoded()));

            return new KeyPair(publicKey, privateKey);
        } catch (final Exception exception) {
            throw new IllegalStateException("Failed to derive %s key pair from seed".formatted(KEY_ALGORITHM), exception);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance(KEY_ALGORITHM).generateKeyPair();
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("%s key pair algorithm unavailable".formatted(KEY_ALGORITHM), exception);
        }
    }
}