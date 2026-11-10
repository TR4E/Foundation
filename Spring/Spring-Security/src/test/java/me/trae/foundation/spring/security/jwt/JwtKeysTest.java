package me.trae.foundation.spring.security.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import me.trae.foundation.spring.common.FoundationProperties;
import me.trae.foundation.spring.security.jwt.enums.TokenType;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class JwtKeysTest {

    @Test
    void identicalSeedsProduceIdenticalKeysAndInteroperableTokens() {
        final JwtKeys firstJwtKeys = new JwtKeys(JwtTestFixture.properties(), new FoundationProperties());
        final JwtKeys secondJwtKeys = new JwtKeys(JwtTestFixture.properties(), new FoundationProperties());

        assertArrayEquals(firstJwtKeys.getAccessTokenKeyPair().getPrivate().getEncoded(), secondJwtKeys.getAccessTokenKeyPair().getPrivate().getEncoded());
        assertArrayEquals(firstJwtKeys.getAccessTokenKeyPair().getPublic().getEncoded(), secondJwtKeys.getAccessTokenKeyPair().getPublic().getEncoded());
        assertArrayEquals(firstJwtKeys.getRefreshTokenKeyPair().getPrivate().getEncoded(), secondJwtKeys.getRefreshTokenKeyPair().getPrivate().getEncoded());
        assertArrayEquals(firstJwtKeys.getRefreshTokenKeyPair().getPublic().getEncoded(), secondJwtKeys.getRefreshTokenKeyPair().getPublic().getEncoded());
        assertEquals("alice", secondJwtKeys.getAccessTokenParser().parseSignedClaims(this.token(firstJwtKeys, TokenType.ACCESS_TOKEN)).getPayload().getSubject());
        assertEquals("alice", secondJwtKeys.getRefreshTokenParser().parseSignedClaims(this.token(firstJwtKeys, TokenType.REFRESH_TOKEN)).getPayload().getSubject());
    }

    @Test
    void developmentWithoutSeedsGeneratesDifferentKeys() {
        final JwtKeys firstJwtKeys = new JwtKeys(new JwtProperties(), new FoundationProperties());
        final JwtKeys secondJwtKeys = new JwtKeys(new JwtProperties(), new FoundationProperties());

        assertFalse(java.util.Arrays.equals(firstJwtKeys.getAccessTokenKeyPair().getPublic().getEncoded(), secondJwtKeys.getAccessTokenKeyPair().getPublic().getEncoded()));
        assertFalse(java.util.Arrays.equals(firstJwtKeys.getRefreshTokenKeyPair().getPublic().getEncoded(), secondJwtKeys.getRefreshTokenKeyPair().getPublic().getEncoded()));
        assertThrows(JwtException.class, () -> secondJwtKeys.getAccessTokenParser().parseSignedClaims(this.token(firstJwtKeys, TokenType.ACCESS_TOKEN)));
        assertThrows(JwtException.class, () -> secondJwtKeys.getRefreshTokenParser().parseSignedClaims(this.token(firstJwtKeys, TokenType.REFRESH_TOKEN)));
    }

    @Test
    void productionRequiresBothSeeds() {
        final FoundationProperties foundationProperties = new FoundationProperties();
        foundationProperties.setProduction(true);

        assertThrows(IllegalStateException.class, () -> new JwtKeys(new JwtProperties(), foundationProperties));

        final JwtProperties jwtProperties = JwtTestFixture.properties();
        jwtProperties.setRefreshTokenKeySeed(null);

        assertThrows(IllegalStateException.class, () -> new JwtKeys(jwtProperties, foundationProperties));
    }

    @Test
    void invalidBase64SeedIsRejected() {
        final JwtProperties jwtProperties = JwtTestFixture.properties();
        jwtProperties.setAccessTokenKeySeed("!invalid!");

        assertThrows(IllegalArgumentException.class, () -> new JwtKeys(jwtProperties, new FoundationProperties()));
    }

    @Test
    void seedsMustDecodeToExactlyThirtyTwoBytes() {
        for (final int size : new int[]{31, 33}) {
            final JwtProperties jwtProperties = JwtTestFixture.properties();
            jwtProperties.setAccessTokenKeySeed(Base64.getEncoder().encodeToString(new byte[size]));

            assertThrows(IllegalArgumentException.class, () -> new JwtKeys(jwtProperties, new FoundationProperties()));
            jwtProperties.setAccessTokenKeySeed(JwtTestFixture.seed(1));
            jwtProperties.setRefreshTokenKeySeed(Base64.getEncoder().encodeToString(new byte[size]));

            assertThrows(IllegalArgumentException.class, () -> new JwtKeys(jwtProperties, new FoundationProperties()));
        }
    }

    @Test
    void constructionClearsConfiguredSeeds() {
        final JwtProperties jwtProperties = JwtTestFixture.properties();
        new JwtKeys(jwtProperties, new FoundationProperties());

        assertNull(jwtProperties.getAccessTokenKeySeed());
        assertNull(jwtProperties.getRefreshTokenKeySeed());
    }

    @Test
    void accessAndRefreshKeysCannotVerifyEachOthersTokens() {
        final JwtKeys jwtKeys = new JwtKeys(JwtTestFixture.properties(), new FoundationProperties());

        assertFalse(java.util.Arrays.equals(jwtKeys.getAccessTokenKeyPair().getPublic().getEncoded(), jwtKeys.getRefreshTokenKeyPair().getPublic().getEncoded()));
        assertThrows(JwtException.class, () -> jwtKeys.getRefreshTokenParser().parseSignedClaims(this.token(jwtKeys, TokenType.ACCESS_TOKEN)));
        assertThrows(JwtException.class, () -> jwtKeys.getAccessTokenParser().parseSignedClaims(this.token(jwtKeys, TokenType.REFRESH_TOKEN)));
    }

    @Test
    void tokenTypeSelectsTheCorrespondingKeyAndParser() {
        final JwtKeys jwtKeys = new JwtKeys(JwtTestFixture.properties(), new FoundationProperties());

        assertSame(jwtKeys.getAccessTokenKeyPair().getPrivate(), jwtKeys.getSigningKey(TokenType.ACCESS_TOKEN));
        assertSame(jwtKeys.getRefreshTokenKeyPair().getPrivate(), jwtKeys.getSigningKey(TokenType.REFRESH_TOKEN));
        assertSame(jwtKeys.getAccessTokenParser(), jwtKeys.getParser(TokenType.ACCESS_TOKEN));
        assertSame(jwtKeys.getRefreshTokenParser(), jwtKeys.getParser(TokenType.REFRESH_TOKEN));
    }

    private String token(final JwtKeys jwtKeys, final TokenType tokenType) {
        return Jwts.builder().issuer("foundation").audience().add("foundation").and().subject("alice").signWith(jwtKeys.getSigningKey(tokenType), Jwts.SIG.EdDSA).compact();
    }
}