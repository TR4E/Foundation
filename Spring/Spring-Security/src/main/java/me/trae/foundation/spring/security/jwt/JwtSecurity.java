package me.trae.foundation.spring.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.CustomLog;
import lombok.Getter;
import me.trae.foundation.spring.common.FoundationProperties;
import me.trae.foundation.spring.common.account.AccountIdentity;
import me.trae.foundation.spring.common.account.AccountRoleDefinition;
import me.trae.foundation.spring.common.account.CommonAccountProvider;
import me.trae.foundation.spring.common.cookie.CookieHelper;
import me.trae.foundation.spring.security.account.SecurityAccountProvider;
import me.trae.foundation.spring.security.jwt.enums.TokenType;
import me.trae.foundation.spring.security.jwt.refresh.RefreshSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@CustomLog
public final class JwtSecurity<Account extends AccountIdentity<?>> {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String CLAIM_FINGERPRINT = "fingerprint";
    private static final String CLAIM_SESSION = "sid";

    private static final String FINGERPRINT_ALGORITHM = "SHA-256";
    private static final String REFRESH_TOKEN_ALGORITHM = "SHA-512";

    private static final String ROTATION_GRACE_ATTRIBUTE = "foundation.security.jwt.rotation-grace";
    private static final String ROTATION_SESSION_ATTRIBUTE = "foundation.security.jwt.rotation-session";
    private static final String RESOLVED_ACCOUNT_ATTRIBUTE = "foundation.security.jwt.resolved-account";

    private static final int FINGERPRINT_BYTE_LENGTH = 32;

    private final JwtProperties properties;
    private final CommonAccountProvider<Account> commonAccountProvider;
    private final SecurityAccountProvider<Account> securityAccountProvider;
    private final CookieHelper cookieHelper;

    private final JwtKeys keys;

    @Getter
    private final Generator generator;

    @Getter
    private final Validator validator;

    @Getter
    private final Authenticator authenticator;

    @Getter
    private final Cookie cookie;

    @Getter
    private final Rotation rotation;

    public JwtSecurity(final JwtProperties properties, final FoundationProperties foundationProperties, final CommonAccountProvider<Account> commonAccountProvider, final SecurityAccountProvider<Account> securityAccountProvider, final CookieHelper cookieHelper) {
        this.properties = properties;
        this.commonAccountProvider = commonAccountProvider;
        this.securityAccountProvider = securityAccountProvider;
        this.cookieHelper = cookieHelper;

        this.keys = new JwtKeys(properties, foundationProperties);

        this.generator = new Generator();
        this.validator = new Validator();
        this.authenticator = new Authenticator();
        this.cookie = new Cookie();
        this.rotation = new Rotation();
    }

    private Optional<Account> resolveAccount(final Claims claims) {
        final UUID accountId;

        try {
            accountId = UUID.fromString(claims.getSubject());
        } catch (final IllegalArgumentException exception) {
            return Optional.empty();
        }

        return this.commonAccountProvider.getAccountById(accountId);
    }

    private Optional<UUID> resolveSessionId(final Claims claims) {
        final String sessionId = claims.get(CLAIM_SESSION, String.class);

        if (sessionId == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(sessionId));
        } catch (final IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private boolean hasActiveSession(final Claims claims, final Account account) {
        return this.resolveSessionId(claims)
                .flatMap(sessionId -> this.securityAccountProvider.getRefreshSession(account, sessionId))
                .filter(refreshSession -> refreshSession.getExpireAt() >= System.currentTimeMillis())
                .isPresent();
    }

    private static String hash(final String algorithm, final String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("%s digest algorithm unavailable".formatted(algorithm), exception);
        }
    }

    private static boolean matchesHash(final String algorithm, final String value, final String expectedHash) {
        return MessageDigest.isEqual(hash(algorithm, value).getBytes(StandardCharsets.UTF_8), expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static String randomHex() {
        final byte[] bytes = new byte[FINGERPRINT_BYTE_LENGTH];

        SECURE_RANDOM.nextBytes(bytes);

        return HexFormat.of().formatHex(bytes);
    }

    public final class Generator {

        private String generate(final Account account, final TokenType tokenType, final UUID sessionId, final UUID jti, final String fingerprintHash) {
            final Date issuedAt = new Date();
            final Date expiration = new Date(issuedAt.getTime() + tokenType.getDurationFunction().apply(JwtSecurity.this.properties).toMillis());

            final JwtBuilder jwtBuilder = Jwts.builder()
                    .id(jti.toString())
                    .issuer(JwtSecurity.this.properties.getIssuer())
                    .audience().add(JwtSecurity.this.properties.getAudience()).and()
                    .subject(account.getId().toString())
                    .claim(CLAIM_TOKEN_TYPE, tokenType.getKey())
                    .claim(CLAIM_FINGERPRINT, fingerprintHash)
                    .claim(CLAIM_SESSION, sessionId.toString())
                    .issuedAt(issuedAt)
                    .notBefore(issuedAt)
                    .expiration(expiration)
                    .signWith(JwtSecurity.this.keys.getSigningKey(tokenType), Jwts.SIG.EdDSA);

            return jwtBuilder.compact();
        }
    }

    public final class Validator {

        public Optional<Claims> validate(final HttpServletRequest httpServletRequest, final TokenType tokenType) {
            return this.parse(JwtSecurity.this.cookie.getToken(httpServletRequest, tokenType), tokenType).filter(claims -> this.matchesFingerprint(httpServletRequest, claims));
        }

        private Optional<Claims> parse(final String token, final TokenType tokenType) {
            if (token == null || token.isBlank()) {
                return Optional.empty();
            }

            try {
                final Claims claims = JwtSecurity.this.keys.getParser(tokenType).parseSignedClaims(token).getPayload();

                if (claims.getSubject() == null || claims.getId() == null || claims.get(CLAIM_SESSION, String.class) == null) {
                    return Optional.empty();
                }

                if (!tokenType.getKey().equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                    LOGGER.warn("Rejected a token with a mismatched type claim for subject: {}", claims.getSubject());

                    return Optional.empty();
                }

                return Optional.of(claims);
            } catch (final JwtException | IllegalArgumentException exception) {
                LOGGER.debug("Failed to parse {}: {}", tokenType.getKey(), exception.getMessage());

                return Optional.empty();
            }
        }

        private boolean matchesFingerprint(final HttpServletRequest httpServletRequest, final Claims claims) {
            final String expectedHash = claims.get(CLAIM_FINGERPRINT, String.class);

            if (expectedHash == null || expectedHash.isBlank()) {
                LOGGER.warn("Rejected a token without a fingerprint claim for subject: {}", claims.getSubject());

                return false;
            }

            final String rawFingerprint = JwtSecurity.this.cookie.getFingerprint(httpServletRequest);

            if (rawFingerprint == null || rawFingerprint.isBlank()) {
                return false;
            }

            if (!matchesHash(FINGERPRINT_ALGORITHM, rawFingerprint, expectedHash)) {
                LOGGER.warn("Rejected a token with a mismatched fingerprint for subject: {}", claims.getSubject());

                return false;
            }

            return true;
        }
    }

    public final class Authenticator {

        public Optional<Account> getAccountByRequest(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
            if (httpServletRequest.getAttribute(RESOLVED_ACCOUNT_ATTRIBUTE) instanceof final UUID accountId) {
                return JwtSecurity.this.commonAccountProvider.getAccountById(accountId);
            }

            final Optional<Account> accountOptional = this.resolveByAccessToken(httpServletRequest).or(() -> JwtSecurity.this.rotation.resolve(httpServletRequest).map(account -> this.rotate(httpServletRequest, httpServletResponse, account)));

            accountOptional.ifPresent(account -> httpServletRequest.setAttribute(RESOLVED_ACCOUNT_ATTRIBUTE, account.getId()));

            return accountOptional;
        }

        public boolean isAuthenticated(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
            return this.getAccountByRequest(httpServletRequest, httpServletResponse).isPresent();
        }

        public boolean hasRole(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse, final AccountRoleDefinition accountRoleDefinition) {
            return this.getAccountByRequest(httpServletRequest, httpServletResponse).map(account -> account.hasRole(accountRoleDefinition)).orElse(false);
        }

        private Optional<Account> resolveByAccessToken(final HttpServletRequest httpServletRequest) {
            return JwtSecurity.this.validator.validate(httpServletRequest, TokenType.ACCESS_TOKEN).flatMap(claims -> JwtSecurity.this.resolveAccount(claims).filter(account -> JwtSecurity.this.hasActiveSession(claims, account)));
        }

        private Account rotate(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse, final Account account) {
            if (Boolean.TRUE.equals(httpServletRequest.getAttribute(ROTATION_GRACE_ATTRIBUTE))) {
                return account;
            }

            if (httpServletRequest.getAttribute(ROTATION_SESSION_ATTRIBUTE) instanceof final RefreshSession refreshSession) {
                JwtSecurity.this.cookie.issue(httpServletResponse, account, refreshSession.getSessionId(), refreshSession.getTokenHash());
            }

            return account;
        }
    }

    public final class Cookie {

        public String getToken(final HttpServletRequest httpServletRequest, final TokenType tokenType) {
            return JwtSecurity.this.cookieHelper.getCookie(httpServletRequest, tokenType.getCookieNameFunction().apply(JwtSecurity.this.properties), true);
        }

        public String getFingerprint(final HttpServletRequest httpServletRequest) {
            return JwtSecurity.this.cookieHelper.getCookie(httpServletRequest, JwtSecurity.this.properties.getFingerprintCookieName(), true);
        }

        public boolean apply(final HttpServletResponse httpServletResponse, final Account account) {
            this.prepareSessionSlot(account);

            return this.issue(httpServletResponse, account, UUID.randomUUID(), null);
        }

        public void remove(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse, final Account account) {
            this.deleteCurrentSession(httpServletRequest, account);

            JwtSecurity.this.cookieHelper.deleteCookie(httpServletResponse, JwtSecurity.this.properties.getAccessTokenCookieName(), true);
            JwtSecurity.this.cookieHelper.deleteCookie(httpServletResponse, JwtSecurity.this.properties.getRefreshTokenCookieName(), true);
            JwtSecurity.this.cookieHelper.deleteCookie(httpServletResponse, JwtSecurity.this.properties.getFingerprintCookieName(), true);
        }

        private void deleteCurrentSession(final HttpServletRequest httpServletRequest, final Account account) {
            JwtSecurity.this.validator.parse(this.getToken(httpServletRequest, TokenType.REFRESH_TOKEN), TokenType.REFRESH_TOKEN)
                    .or(() -> JwtSecurity.this.validator.parse(this.getToken(httpServletRequest, TokenType.ACCESS_TOKEN), TokenType.ACCESS_TOKEN))
                    .ifPresent(claims -> this.deleteSession(claims, account));
        }

        private void deleteSession(final Claims claims, final Account account) {
            final Account resolvedAccount = JwtSecurity.this.resolveAccount(claims)
                    .filter(tokenAccount -> account == null || tokenAccount.getId().equals(account.getId()))
                    .orElse(null);

            if (resolvedAccount == null) {
                return;
            }

            JwtSecurity.this.resolveSessionId(claims).ifPresent(sessionId -> JwtSecurity.this.securityAccountProvider.deleteRefreshSession(resolvedAccount, sessionId));
        }

        private void prepareSessionSlot(final Account account) {
            final long now = System.currentTimeMillis();

            final List<RefreshSession> sessionList = new ArrayList<>();

            for (final RefreshSession refreshSession : JwtSecurity.this.securityAccountProvider.getRefreshSessionList(account)) {
                if (refreshSession.getExpireAt() < now) {
                    JwtSecurity.this.securityAccountProvider.deleteRefreshSession(account, refreshSession.getSessionId());
                    continue;
                }

                sessionList.add(refreshSession);
            }

            if (JwtSecurity.this.properties.getMaxSessions() < 1 || sessionList.size() < JwtSecurity.this.properties.getMaxSessions()) {
                return;
            }

            sessionList.sort(Comparator.comparingLong(RefreshSession::getRotatedAt));

            for (final RefreshSession refreshSession : sessionList.subList(0, sessionList.size() - JwtSecurity.this.properties.getMaxSessions() + 1)) {
                JwtSecurity.this.securityAccountProvider.deleteRefreshSession(account, refreshSession.getSessionId());
            }
        }

        private boolean issue(final HttpServletResponse httpServletResponse, final Account account, final UUID sessionId, final String expectedTokenHash) {
            final String rawFingerprint = randomHex();
            final String fingerprintHash = hash(FINGERPRINT_ALGORITHM, rawFingerprint);

            final UUID refreshJti = UUID.randomUUID();

            final String accessToken = JwtSecurity.this.generator.generate(account, TokenType.ACCESS_TOKEN, sessionId, UUID.randomUUID(), fingerprintHash);
            final String refreshToken = JwtSecurity.this.generator.generate(account, TokenType.REFRESH_TOKEN, sessionId, refreshJti, fingerprintHash);

            if (!JwtSecurity.this.rotation.store(account, sessionId, refreshJti, expectedTokenHash)) {
                return false;
            }

            JwtSecurity.this.cookieHelper.setCookie(httpServletResponse, JwtSecurity.this.properties.getAccessTokenCookieName(), accessToken, true, JwtSecurity.this.properties.getAccessTokenDuration(), true);
            JwtSecurity.this.cookieHelper.setCookie(httpServletResponse, JwtSecurity.this.properties.getRefreshTokenCookieName(), refreshToken, true, JwtSecurity.this.properties.getRefreshTokenDuration(), true);
            JwtSecurity.this.cookieHelper.setCookie(httpServletResponse, JwtSecurity.this.properties.getFingerprintCookieName(), rawFingerprint, true, JwtSecurity.this.properties.getRefreshTokenDuration(), true);

            return true;
        }
    }

    public final class Rotation {

        public Optional<Account> resolve(final HttpServletRequest httpServletRequest) {
            return JwtSecurity.this.validator.validate(httpServletRequest, TokenType.REFRESH_TOKEN).flatMap(claims -> JwtSecurity.this.resolveAccount(claims).flatMap(account -> this.verify(httpServletRequest, claims, account)));
        }

        public void revoke(final Account account) {
            JwtSecurity.this.securityAccountProvider.deleteRefreshSessionList(account);
        }

        public void revokeSession(final Account account, final UUID sessionId) {
            JwtSecurity.this.securityAccountProvider.deleteRefreshSession(account, sessionId);
        }

        public List<RefreshSession> getSessionList(final Account account) {
            return JwtSecurity.this.securityAccountProvider.getRefreshSessionList(account);
        }

        private Optional<Account> verify(final HttpServletRequest httpServletRequest, final Claims claims, final Account account) {
            return JwtSecurity.this.resolveSessionId(claims)
                    .flatMap(sessionId -> JwtSecurity.this.securityAccountProvider.getRefreshSession(account, sessionId))
                    .flatMap(refreshSession -> this.verifySession(httpServletRequest, claims, account, refreshSession));
        }

        private Optional<Account> verifySession(final HttpServletRequest httpServletRequest, final Claims claims, final Account account, final RefreshSession refreshSession) {
            if (refreshSession.getExpireAt() < System.currentTimeMillis()) {
                JwtSecurity.this.securityAccountProvider.deleteRefreshSession(account, refreshSession.getSessionId());

                return Optional.empty();
            }

            if (matchesHash(REFRESH_TOKEN_ALGORITHM, claims.getId(), refreshSession.getTokenHash())) {
                httpServletRequest.setAttribute(ROTATION_SESSION_ATTRIBUTE, refreshSession);

                return Optional.of(account);
            }

            if (this.isWithinGrace(refreshSession, claims)) {
                httpServletRequest.setAttribute(ROTATION_GRACE_ATTRIBUTE, Boolean.TRUE);

                return Optional.of(account);
            }

            LOGGER.error("Refresh token re-use detected for account: {} session: {}, revoking all sessions", account.getId(), refreshSession.getSessionId());

            JwtSecurity.this.securityAccountProvider.deleteRefreshSessionList(account);

            return Optional.empty();
        }

        private boolean isWithinGrace(final RefreshSession refreshSession, final Claims claims) {
            if (refreshSession.getPreviousTokenHash() == null) {
                return false;
            }

            if (System.currentTimeMillis() - refreshSession.getRotatedAt() >= JwtSecurity.this.properties.getRotationGrace().toMillis()) {
                return false;
            }

            return matchesHash(REFRESH_TOKEN_ALGORITHM, claims.getId(), refreshSession.getPreviousTokenHash());
        }

        private boolean store(final Account account, final UUID sessionId, final UUID jti, final String expectedTokenHash) {
            final long now = System.currentTimeMillis();

            final RefreshSession refreshSession = new RefreshSession(sessionId, hash(REFRESH_TOKEN_ALGORITHM, jti.toString()), expectedTokenHash, now + JwtSecurity.this.properties.getRefreshTokenDuration().toMillis(), now);

            if (JwtSecurity.this.securityAccountProvider.updateRefreshSession(account, refreshSession, expectedTokenHash)) {
                return true;
            }

            LOGGER.debug("Refresh token rotation lost a concurrent write for account: {} session: {}", account.getId(), sessionId);

            return false;
        }
    }
}