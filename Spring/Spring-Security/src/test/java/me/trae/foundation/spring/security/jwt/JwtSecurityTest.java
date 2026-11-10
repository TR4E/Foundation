package me.trae.foundation.spring.security.jwt;

import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import me.trae.foundation.spring.common.FoundationProperties;
import me.trae.foundation.spring.security.jwt.enums.TokenType;
import me.trae.foundation.spring.security.jwt.refresh.RefreshSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JwtSecurityTest {

    private final JwtTestFixture jwtTestFixture = new JwtTestFixture();

    @Test
    void applyWritesThreeHttpOnlyCookiesAndTheBrowserRoundTripAuthenticates() {
        final MockHttpServletResponse mockHttpServletResponse = this.jwtTestFixture.login();

        assertEquals(3, mockHttpServletResponse.getHeaders("Set-Cookie").size());
        assertTrue(mockHttpServletResponse.getHeaders("Set-Cookie").stream().allMatch(header -> header.contains("HttpOnly")));
        assertEquals(this.jwtTestFixture.account, this.jwtTestFixture.jwtSecurity.getAuthenticator().getAccountByRequest(JwtTestFixture.request(mockHttpServletResponse), new MockHttpServletResponse()).orElseThrow());
        assertNotSame(this.session(mockHttpServletResponse), this.session(mockHttpServletResponse));
    }

    @Test
    void productionCookiesHaveHostPrefixSecureStrictAndRootPath() {
        final JwtTestFixture jwtTestFixture = new JwtTestFixture(true);
        final MockHttpServletResponse mockHttpServletResponse = jwtTestFixture.login();

        assertTrue(mockHttpServletResponse.getHeaders("Set-Cookie").stream().allMatch(header -> header.startsWith("__Host-") && header.contains("Secure") && header.contains("SameSite=Strict") && header.contains("Path=/") && header.contains("HttpOnly")));
        assertTrue(jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(JwtTestFixture.request(mockHttpServletResponse), new MockHttpServletResponse()));
    }

    @Test
    void aValidAccessTokenAuthenticatesWithoutWritingCookies() {
        final MockHttpServletRequest mockHttpServletRequest = JwtTestFixture.request(this.jwtTestFixture.login());
        final MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();

        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(mockHttpServletRequest, mockHttpServletResponse));
        assertTrue(mockHttpServletResponse.getHeaders("Set-Cookie").isEmpty());
    }

    @Test
    void anAbsentAccessTokenRefreshesTheSessionAndWritesThreeCookies() {
        final MockHttpServletRequest mockHttpServletRequest = this.refreshRequest(this.jwtTestFixture.login());
        final MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();

        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(mockHttpServletRequest, mockHttpServletResponse));
        assertEquals(3, mockHttpServletResponse.getHeaders("Set-Cookie").size());
    }

    @Test
    void anExpiredAccessTokenFallsThroughToTheRefreshCookie() {
        this.jwtTestFixture.jwtProperties.setAccessTokenDuration(Duration.ofSeconds(-30));
        final MockHttpServletRequest mockHttpServletRequest = JwtTestFixture.request(this.jwtTestFixture.login());
        this.jwtTestFixture.jwtProperties.setAccessTokenDuration(Duration.ofMinutes(15));
        final MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();

        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(mockHttpServletRequest, mockHttpServletResponse));
        assertEquals(3, mockHttpServletResponse.getHeaders("Set-Cookie").size());
        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(JwtTestFixture.request(mockHttpServletResponse), new MockHttpServletResponse()));
    }

    @Test
    void rotationReplacesTheFingerprint() {
        final MockHttpServletResponse loginMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletResponse rotationMockHttpServletResponse = this.rotate(loginMockHttpServletResponse);

        assertNotEquals(this.jwtTestFixture.jwtSecurity.getCookie().getFingerprint(JwtTestFixture.request(loginMockHttpServletResponse)), this.jwtTestFixture.jwtSecurity.getCookie().getFingerprint(JwtTestFixture.request(rotationMockHttpServletResponse)));
    }

    @Test
    void aMissingFingerprintRejectsAuthentication() {
        final MockHttpServletRequest mockHttpServletRequest = this.without(JwtTestFixture.request(this.jwtTestFixture.login()), "FINGERPRINT");

        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(mockHttpServletRequest, new MockHttpServletResponse()));
    }

    @Test
    void aFingerprintFromAnotherSessionRejectsAuthentication() {
        final MockHttpServletRequest mockHttpServletRequest = JwtTestFixture.request(this.jwtTestFixture.login());
        this.replaceCookie(mockHttpServletRequest, "FINGERPRINT", "another-session");

        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(mockHttpServletRequest, new MockHttpServletResponse()));
    }

    @Test
    void aSignedTokenWithoutFingerprintClaimIsRejected() throws Exception {
        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(this.signedAccessRequest(this.jwtTestFixture.account.getId().toString(), this.signingSessionId(), 60000L, false, JwtTestFixture.properties()), new MockHttpServletResponse()));
    }

    @Test
    void tokenTypeClaimRejectsRefreshAsAccessAndAccessAsRefreshEvenWithTheExpectedSigningKey() throws Exception {
        for (final TokenType tokenType : TokenType.values()) {
            final TokenType claimedTokenType = tokenType == TokenType.ACCESS_TOKEN ? TokenType.REFRESH_TOKEN : TokenType.ACCESS_TOKEN;
            final String token = this.sign(tokenType, claimedTokenType, this.jwtTestFixture.account.getId().toString(), this.signingSessionId(), 60000L, true, JwtTestFixture.properties());
            final MockHttpServletRequest mockHttpServletRequest = this.tokenRequest(token, tokenType);

            assertTrue(this.jwtTestFixture.jwtSecurity.getValidator().validate(mockHttpServletRequest, tokenType).isEmpty());
        }

        final MockHttpServletRequest swappedMockHttpServletRequest = JwtTestFixture.request(this.jwtTestFixture.login());
        final String accessToken = this.jwtTestFixture.jwtSecurity.getCookie().getToken(swappedMockHttpServletRequest, TokenType.ACCESS_TOKEN);
        final String refreshToken = this.jwtTestFixture.jwtSecurity.getCookie().getToken(swappedMockHttpServletRequest, TokenType.REFRESH_TOKEN);
        this.replaceCookie(swappedMockHttpServletRequest, "ACCESS-TOKEN", refreshToken);
        this.replaceCookie(swappedMockHttpServletRequest, "REFRESH-TOKEN", accessToken);

        assertTrue(this.jwtTestFixture.jwtSecurity.getValidator().validate(swappedMockHttpServletRequest, TokenType.ACCESS_TOKEN).isEmpty());
        assertTrue(this.jwtTestFixture.jwtSecurity.getValidator().validate(swappedMockHttpServletRequest, TokenType.REFRESH_TOKEN).isEmpty());
    }

    @Test
    void aDifferentSigningSeedRejectsAuthentication() throws Exception {
        final JwtProperties jwtProperties = JwtTestFixture.properties();
        jwtProperties.setAccessTokenKeySeed(JwtTestFixture.seed(3));

        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(this.signedAccessRequest(this.jwtTestFixture.account.getId().toString(), this.signingSessionId(), 60000L, true, jwtProperties), new MockHttpServletResponse()));
    }

    @Test
    void aNonUuidSubjectIsRejected() throws Exception {
        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(this.signedAccessRequest("alice", this.signingSessionId(), 60000L, true, JwtTestFixture.properties()), new MockHttpServletResponse()));
    }

    @Test
    void anUnknownAccountSubjectIsRejected() throws Exception {
        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(this.signedAccessRequest(UUID.randomUUID().toString(), this.signingSessionId(), 60000L, true, JwtTestFixture.properties()), new MockHttpServletResponse()));
    }

    @Test
    void thePreviousRefreshInsideGraceDoesNotRotateOrMutateStoredState() {
        final MockHttpServletResponse loginMockHttpServletResponse = this.jwtTestFixture.login();
        this.rotate(loginMockHttpServletResponse);
        final RefreshSession refreshSession = this.session(loginMockHttpServletResponse);
        final MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();

        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(this.refreshRequest(loginMockHttpServletResponse), mockHttpServletResponse));
        assertTrue(mockHttpServletResponse.getHeaders("Set-Cookie").isEmpty());
        this.assertSessionEquals(refreshSession, this.session(loginMockHttpServletResponse));
    }

    @Test
    void thePreviousRefreshOutsideGraceRevokesEvenTheRotatedAccessToken() {
        final MockHttpServletResponse loginMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletResponse rotationMockHttpServletResponse = this.rotate(loginMockHttpServletResponse);
        final RefreshSession refreshSession = this.session(loginMockHttpServletResponse);
        assertTrue(this.jwtTestFixture.securityAccountProvider.updateRefreshSession(this.jwtTestFixture.account, new RefreshSession(refreshSession.getSessionId(), refreshSession.getTokenHash(), refreshSession.getPreviousTokenHash(), refreshSession.getExpireAt(), System.currentTimeMillis() - this.jwtTestFixture.jwtProperties.getRotationGrace().toMillis() - 1L), refreshSession.getTokenHash()));

        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(this.refreshRequest(loginMockHttpServletResponse), new MockHttpServletResponse()));
        assertTrue(this.jwtTestFixture.jwtSecurity.getRotation().getSessionList(this.jwtTestFixture.account).isEmpty());
        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(JwtTestFixture.request(rotationMockHttpServletResponse), new MockHttpServletResponse()));
    }

    @Test
    void aRefreshFromTwoRotationsBackRevokesInsideTheGraceWindow() {
        final MockHttpServletResponse loginMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletResponse firstRotationMockHttpServletResponse = this.rotate(loginMockHttpServletResponse);
        final MockHttpServletResponse secondRotationMockHttpServletResponse = this.rotate(firstRotationMockHttpServletResponse);

        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(this.refreshRequest(loginMockHttpServletResponse), new MockHttpServletResponse()));
        assertTrue(this.jwtTestFixture.jwtSecurity.getRotation().getSessionList(this.jwtTestFixture.account).isEmpty());
        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(JwtTestFixture.request(secondRotationMockHttpServletResponse), new MockHttpServletResponse()));
    }

    @Test
    void twoResolutionsOnOneRequestRotateOnlyOnce() {
        final MockHttpServletRequest mockHttpServletRequest = this.refreshRequest(this.jwtTestFixture.login());
        final MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();

        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(mockHttpServletRequest, mockHttpServletResponse));
        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().hasRole(mockHttpServletRequest, mockHttpServletResponse, JwtTestFixture.TestRole.ADMIN));
        assertEquals(3, mockHttpServletResponse.getHeaders("Set-Cookie").size());
        assertEquals(this.jwtTestFixture.account.getId(), mockHttpServletRequest.getAttribute("foundation.security.jwt.resolved-account"));
        assertNull(mockHttpServletRequest.getAttribute("foundation.security.jwt.rotation-grace"));
    }

    @Test
    void removingCookiesAlsoRevokesThePreviouslyValidAccessToken() {
        final MockHttpServletRequest mockHttpServletRequest = JwtTestFixture.request(this.jwtTestFixture.login());
        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(mockHttpServletRequest, new MockHttpServletResponse()));

        final MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        this.jwtTestFixture.jwtSecurity.getCookie().remove(mockHttpServletRequest, mockHttpServletResponse, this.jwtTestFixture.account);

        assertEquals(3, mockHttpServletResponse.getHeaders("Set-Cookie").size());
        assertTrue(mockHttpServletResponse.getHeaders("Set-Cookie").stream().allMatch(header -> header.contains("Max-Age=0")));
        final MockHttpServletRequest nextMockHttpServletRequest = new MockHttpServletRequest();
        nextMockHttpServletRequest.setCookies(mockHttpServletRequest.getCookies());

        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(nextMockHttpServletRequest, new MockHttpServletResponse()));
    }

    @Test
    void twoLoginsCreateDistinctIndependentSessions() {
        final MockHttpServletResponse firstMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletResponse secondMockHttpServletResponse = this.jwtTestFixture.login();

        assertNotEquals(this.session(firstMockHttpServletResponse).getSessionId(), this.session(secondMockHttpServletResponse).getSessionId());
        assertTrue(this.authenticates(firstMockHttpServletResponse));
        assertTrue(this.authenticates(secondMockHttpServletResponse));
        assertEquals(2, this.jwtTestFixture.jwtSecurity.getRotation().getSessionList(this.jwtTestFixture.account).size());
    }

    @Test
    void explicitRevocationInvalidatesAnUnexpiredAccessToken() {
        final MockHttpServletRequest mockHttpServletRequest = JwtTestFixture.request(this.jwtTestFixture.login());
        this.jwtTestFixture.jwtSecurity.getRotation().revoke(this.jwtTestFixture.account);

        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(mockHttpServletRequest, new MockHttpServletResponse()));
    }

    @Test
    void aLostConditionalWriteLeavesPersistentStateAndCookiesUntouched() {
        final MockHttpServletResponse loginMockHttpServletResponse = this.jwtTestFixture.login();
        final RefreshSession refreshSession = this.session(loginMockHttpServletResponse);
        this.jwtTestFixture.securityAccountProvider.failConditionalWrite = true;
        final MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        this.jwtTestFixture.jwtSecurity.getAuthenticator().getAccountByRequest(this.refreshRequest(loginMockHttpServletResponse), mockHttpServletResponse);

        assertTrue(mockHttpServletResponse.getHeaders("Set-Cookie").isEmpty());
        this.assertSessionEquals(refreshSession, this.session(loginMockHttpServletResponse));
    }

    @Test
    void rolesRequireAnAuthenticatedAccountWithEqualOrHigherPrivilege() {
        final MockHttpServletRequest mockHttpServletRequest = JwtTestFixture.request(this.jwtTestFixture.login());

        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().hasRole(mockHttpServletRequest, new MockHttpServletResponse(), JwtTestFixture.TestRole.MEMBER));
        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().hasRole(mockHttpServletRequest, new MockHttpServletResponse(), JwtTestFixture.TestRole.ADMIN));
        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().hasRole(mockHttpServletRequest, new MockHttpServletResponse(), JwtTestFixture.TestRole.OWNER));
        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().hasRole(new MockHttpServletRequest(), new MockHttpServletResponse(), JwtTestFixture.TestRole.MEMBER));
    }

    @Test
    void expirationRespectsConfiguredClockSkew() throws Exception {
        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(this.signedAccessRequest(this.jwtTestFixture.account.getId().toString(), this.signingSessionId(), -1000L, true, JwtTestFixture.properties()), new MockHttpServletResponse()));
        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(this.signedAccessRequest(this.jwtTestFixture.account.getId().toString(), this.signingSessionId(), -10000L, true, JwtTestFixture.properties()), new MockHttpServletResponse()));

        final JwtProperties jwtProperties = JwtTestFixture.properties();
        jwtProperties.setClockSkew(Duration.ZERO);
        final JwtTestFixture jwtTestFixture = new JwtTestFixture(false, jwtProperties);
        jwtTestFixture.accountMap.put(this.jwtTestFixture.account.getId(), this.jwtTestFixture.account);

        final String sessionId = this.signingSessionId();
        assertTrue(jwtTestFixture.securityAccountProvider.updateRefreshSession(this.jwtTestFixture.account, this.jwtTestFixture.securityAccountProvider.getRefreshSession(this.jwtTestFixture.account, UUID.fromString(sessionId)).orElseThrow(), null));
        assertTrue(jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(this.signedAccessRequest(this.jwtTestFixture.account.getId().toString(), sessionId, 60000L, true, JwtTestFixture.properties()), new MockHttpServletResponse()));
        assertFalse(jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(this.signedAccessRequest(this.jwtTestFixture.account.getId().toString(), sessionId, -1000L, true, JwtTestFixture.properties()), new MockHttpServletResponse()));
    }

    @Test
    void persistedCompareAndSetRequiresTheExpectedHashIncludingNoRecord() {
        final UUID sessionId = UUID.randomUUID();
        final RefreshSession firstRefreshSession = new RefreshSession(sessionId, "first", null, Long.MAX_VALUE, 1L);
        assertTrue(this.jwtTestFixture.securityAccountProvider.updateRefreshSession(this.jwtTestFixture.account, firstRefreshSession, null));

        final RefreshSession secondRefreshSession = new RefreshSession(sessionId, "second", "first", Long.MAX_VALUE, 2L);
        assertFalse(this.jwtTestFixture.securityAccountProvider.updateRefreshSession(this.jwtTestFixture.account, secondRefreshSession, null));
        assertEquals("first", this.jwtTestFixture.securityAccountProvider.getRefreshSession(this.jwtTestFixture.account, sessionId).orElseThrow().getTokenHash());
        assertTrue(this.jwtTestFixture.securityAccountProvider.updateRefreshSession(this.jwtTestFixture.account, secondRefreshSession, "first"));
        assertEquals("second", this.jwtTestFixture.securityAccountProvider.getRefreshSession(this.jwtTestFixture.account, sessionId).orElseThrow().getTokenHash());
        assertNotSame(secondRefreshSession, this.jwtTestFixture.securityAccountProvider.getRefreshSession(this.jwtTestFixture.account, sessionId).orElseThrow());
    }

    @Test
    void loginEvictsLeastRecentlyRotatedSessionOnly() {
        this.jwtTestFixture.jwtProperties.setMaxSessions(2);
        final MockHttpServletResponse firstMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletResponse secondMockHttpServletResponse = this.jwtTestFixture.login();
        this.age(firstMockHttpServletResponse, Long.MAX_VALUE, 1L);
        this.age(secondMockHttpServletResponse, Long.MAX_VALUE, 2L);
        final MockHttpServletResponse thirdMockHttpServletResponse = this.jwtTestFixture.login();

        assertFalse(this.authenticates(firstMockHttpServletResponse));
        assertTrue(this.authenticates(secondMockHttpServletResponse));
        assertTrue(this.authenticates(thirdMockHttpServletResponse));
    }

    @Test
    void expiredSessionsArePrunedBeforeAnyLiveEviction() {
        this.jwtTestFixture.jwtProperties.setMaxSessions(2);
        final MockHttpServletResponse expiredMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletResponse liveMockHttpServletResponse = this.jwtTestFixture.login();
        this.age(expiredMockHttpServletResponse, 1L, 1L);
        final MockHttpServletResponse newMockHttpServletResponse = this.jwtTestFixture.login();

        assertTrue(this.jwtTestFixture.securityAccountProvider.getRefreshSession(this.jwtTestFixture.account, this.sessionId(expiredMockHttpServletResponse)).isEmpty());
        assertTrue(this.authenticates(liveMockHttpServletResponse));
        assertTrue(this.authenticates(newMockHttpServletResponse));
        assertEquals(2, this.jwtTestFixture.jwtSecurity.getRotation().getSessionList(this.jwtTestFixture.account).size());
    }

    @Test
    void logoutDeletesOnlyThePresentingSessionWithAccessStillValid() {
        final MockHttpServletResponse firstMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletResponse secondMockHttpServletResponse = this.jwtTestFixture.login();
        this.jwtTestFixture.jwtSecurity.getCookie().remove(JwtTestFixture.request(firstMockHttpServletResponse), new MockHttpServletResponse(), this.jwtTestFixture.account);

        assertTrue(this.jwtTestFixture.jwtSecurity.getValidator().validate(JwtTestFixture.request(firstMockHttpServletResponse), TokenType.ACCESS_TOKEN).isPresent());
        assertFalse(this.authenticates(firstMockHttpServletResponse));
        assertTrue(this.authenticates(secondMockHttpServletResponse));
    }

    @Test
    void logoutFallsBackToTheAccessCookie() {
        final MockHttpServletResponse firstMockHttpServletResponse = this.jwtTestFixture.login();
        this.jwtTestFixture.jwtSecurity.getCookie().remove(this.without(JwtTestFixture.request(firstMockHttpServletResponse), "REFRESH-TOKEN"), new MockHttpServletResponse(), this.jwtTestFixture.account);

        assertFalse(this.authenticates(firstMockHttpServletResponse));
    }

    @Test
    void revokeDeletesEveryDeviceSession() {
        final MockHttpServletResponse firstMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletResponse secondMockHttpServletResponse = this.jwtTestFixture.login();
        this.jwtTestFixture.jwtSecurity.getRotation().revoke(this.jwtTestFixture.account);

        assertFalse(this.authenticates(firstMockHttpServletResponse));
        assertFalse(this.authenticates(secondMockHttpServletResponse));
    }

    @Test
    void revokeSessionDeletesExactlyOneDevice() {
        final MockHttpServletResponse firstMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletResponse secondMockHttpServletResponse = this.jwtTestFixture.login();
        this.jwtTestFixture.jwtSecurity.getRotation().revokeSession(this.jwtTestFixture.account, this.sessionId(firstMockHttpServletResponse));

        assertFalse(this.authenticates(firstMockHttpServletResponse));
        assertTrue(this.authenticates(secondMockHttpServletResponse));
    }

    @Test
    void sessionListReflectsLoginsLogoutsAndEviction() {
        this.jwtTestFixture.jwtProperties.setMaxSessions(2);
        assertTrue(this.jwtTestFixture.jwtSecurity.getRotation().getSessionList(this.jwtTestFixture.account).isEmpty());
        final MockHttpServletResponse firstMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletResponse secondMockHttpServletResponse = this.jwtTestFixture.login();
        assertEquals(2, this.jwtTestFixture.jwtSecurity.getRotation().getSessionList(this.jwtTestFixture.account).size());
        this.age(firstMockHttpServletResponse, Long.MAX_VALUE, 1L);
        final MockHttpServletResponse thirdMockHttpServletResponse = this.jwtTestFixture.login();

        assertEquals(java.util.Set.of(this.sessionId(secondMockHttpServletResponse), this.sessionId(thirdMockHttpServletResponse)), this.jwtTestFixture.jwtSecurity.getRotation().getSessionList(this.jwtTestFixture.account).stream().map(RefreshSession::getSessionId).collect(java.util.stream.Collectors.toSet()));
        this.jwtTestFixture.jwtSecurity.getCookie().remove(JwtTestFixture.request(secondMockHttpServletResponse), new MockHttpServletResponse(), this.jwtTestFixture.account);
        assertEquals(List.of(this.sessionId(thirdMockHttpServletResponse)), this.jwtTestFixture.jwtSecurity.getRotation().getSessionList(this.jwtTestFixture.account).stream().map(RefreshSession::getSessionId).toList());
    }

    @Test
    void deletedSessionRejectsCryptographicallyValidAccessDirectly() {
        final MockHttpServletResponse loginMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletRequest accessMockHttpServletRequest = this.without(JwtTestFixture.request(loginMockHttpServletResponse), "REFRESH-TOKEN");
        this.jwtTestFixture.securityAccountProvider.deleteRefreshSession(this.jwtTestFixture.account, this.sessionId(loginMockHttpServletResponse));

        assertTrue(this.jwtTestFixture.jwtSecurity.getValidator().validate(accessMockHttpServletRequest, TokenType.ACCESS_TOKEN).isPresent());
        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(accessMockHttpServletRequest, new MockHttpServletResponse()));
    }

    @Test
    void rotationChangesOnlyThePresentingSessionAndPreservesHashOrdering() throws Exception {
        final MockHttpServletResponse firstMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletResponse secondMockHttpServletResponse = this.jwtTestFixture.login();
        final RefreshSession firstRefreshSession = this.session(firstMockHttpServletResponse);
        final RefreshSession secondRefreshSession = this.session(secondMockHttpServletResponse);
        final MockHttpServletResponse rotationMockHttpServletResponse = this.rotate(firstMockHttpServletResponse);
        final RefreshSession rotatedRefreshSession = this.session(rotationMockHttpServletResponse);

        this.assertSessionEquals(secondRefreshSession, this.session(secondMockHttpServletResponse));
        assertEquals(firstRefreshSession.getTokenHash(), rotatedRefreshSession.getPreviousTokenHash());
        assertNotEquals(firstRefreshSession.getTokenHash(), rotatedRefreshSession.getTokenHash());
        final String jti = this.jwtTestFixture.jwtSecurity.getValidator().validate(JwtTestFixture.request(rotationMockHttpServletResponse), TokenType.REFRESH_TOKEN).orElseThrow().getId();
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(jti.getBytes(StandardCharsets.UTF_8))), rotatedRefreshSession.getTokenHash());
    }

    @Test
    void graceAcceptsOnlyThePresentingSessionsPreviousToken() {
        final MockHttpServletResponse firstMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletResponse secondMockHttpServletResponse = this.jwtTestFixture.login();
        final RefreshSession secondRefreshSession = this.session(secondMockHttpServletResponse);
        final MockHttpServletResponse rotatedMockHttpServletResponse = this.rotate(firstMockHttpServletResponse);
        final RefreshSession rotatedRefreshSession = this.session(rotatedMockHttpServletResponse);
        final MockHttpServletResponse graceMockHttpServletResponse = new MockHttpServletResponse();

        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(this.refreshRequest(firstMockHttpServletResponse), graceMockHttpServletResponse));
        assertTrue(graceMockHttpServletResponse.getHeaders("Set-Cookie").isEmpty());
        this.assertSessionEquals(rotatedRefreshSession, this.session(rotatedMockHttpServletResponse));
        this.assertSessionEquals(secondRefreshSession, this.session(secondMockHttpServletResponse));
        assertTrue(this.authenticates(secondMockHttpServletResponse));
    }

    @Test
    void reuseOnOneSessionRevokesEveryDevice() {
        final MockHttpServletResponse firstMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletResponse secondMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletResponse rotatedMockHttpServletResponse = this.rotate(firstMockHttpServletResponse);
        this.age(rotatedMockHttpServletResponse, Long.MAX_VALUE, 1L);

        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(this.refreshRequest(firstMockHttpServletResponse), new MockHttpServletResponse()));
        assertFalse(this.authenticates(secondMockHttpServletResponse));
        assertFalse(this.authenticates(rotatedMockHttpServletResponse));
        assertTrue(this.jwtTestFixture.jwtSecurity.getRotation().getSessionList(this.jwtTestFixture.account).isEmpty());
    }

    @Test
    void signedTokenWithoutSessionClaimIsRejected() throws Exception {
        assertTrue(this.jwtTestFixture.jwtSecurity.getValidator().validate(this.signedAccessRequest(this.jwtTestFixture.account.getId().toString(), null, 60000L, true, JwtTestFixture.properties()), TokenType.ACCESS_TOKEN).isEmpty());
    }

    @Test
    void malformedSessionIdAndAnotherAccountsSessionAreRejected() throws Exception {
        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(this.signedAccessRequest(this.jwtTestFixture.account.getId().toString(), "alice", 60000L, true, JwtTestFixture.properties()), new MockHttpServletResponse()));
        final JwtTestFixture.Account otherAccount = new JwtTestFixture.Account(UUID.randomUUID(), JwtTestFixture.TestRole.MEMBER);
        this.jwtTestFixture.accountMap.put(otherAccount.getId(), otherAccount);
        final String sessionId = this.signingSessionId();

        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(this.signedAccessRequest(otherAccount.getId().toString(), sessionId, 60000L, true, JwtTestFixture.properties()), new MockHttpServletResponse()));
    }

    @Test
    void fingerprintsArePerSessionAndChangeOnRotation() {
        final MockHttpServletResponse firstMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletResponse secondMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletRequest mixedMockHttpServletRequest = JwtTestFixture.request(firstMockHttpServletResponse);
        final String secondFingerprint = this.jwtTestFixture.jwtSecurity.getCookie().getFingerprint(JwtTestFixture.request(secondMockHttpServletResponse));
        this.replaceCookie(mixedMockHttpServletRequest, "FINGERPRINT", secondFingerprint);

        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(mixedMockHttpServletRequest, new MockHttpServletResponse()));
        final MockHttpServletResponse rotationMockHttpServletResponse = this.rotate(firstMockHttpServletResponse);
        assertNotEquals(this.jwtTestFixture.jwtSecurity.getCookie().getFingerprint(JwtTestFixture.request(firstMockHttpServletResponse)), this.jwtTestFixture.jwtSecurity.getCookie().getFingerprint(JwtTestFixture.request(rotationMockHttpServletResponse)));
        assertTrue(this.authenticates(secondMockHttpServletResponse));
    }

    @Test
    void nonpositiveSessionCapsAllowMultipleSessionsWithoutEviction() {
        for (final int maxSessions : new int[]{0, -1}) {
            final JwtProperties jwtProperties = JwtTestFixture.properties();
            jwtProperties.setMaxSessions(maxSessions);
            final JwtTestFixture jwtTestFixture = new JwtTestFixture(false, jwtProperties);
            final List<MockHttpServletResponse> loginResponseList = IntStream.range(0, 12).mapToObj(_ -> jwtTestFixture.login()).toList();

            assertEquals(12, jwtTestFixture.jwtSecurity.getRotation().getSessionList(jwtTestFixture.account).size());
            assertTrue(loginResponseList.stream().allMatch(mockHttpServletResponse -> jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(JwtTestFixture.request(mockHttpServletResponse), new MockHttpServletResponse())));
        }
    }

    @Test
    void maximumOneSessionReproducesSingleDeviceBehavior() {
        this.jwtTestFixture.jwtProperties.setMaxSessions(1);
        final MockHttpServletResponse firstMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletResponse secondMockHttpServletResponse = this.jwtTestFixture.login();

        assertFalse(this.authenticates(firstMockHttpServletResponse));
        assertTrue(this.authenticates(secondMockHttpServletResponse));
    }

    @Test
    void logoutCanDeleteSignedSessionWithoutFingerprintOrAccountArgument() {
        final MockHttpServletResponse loginMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletRequest mockHttpServletRequest = this.without(JwtTestFixture.request(loginMockHttpServletResponse), "FINGERPRINT");
        this.jwtTestFixture.jwtSecurity.getCookie().remove(mockHttpServletRequest, new MockHttpServletResponse(), null);

        assertFalse(this.authenticates(loginMockHttpServletResponse));
        assertTrue(this.jwtTestFixture.jwtSecurity.getRotation().getSessionList(this.jwtTestFixture.account).isEmpty());
    }

    @Test
    void successfulRequestCacheDoesNotExtendToTheNextRequest() {
        final MockHttpServletResponse loginMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletRequest mockHttpServletRequest = JwtTestFixture.request(loginMockHttpServletResponse);
        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(mockHttpServletRequest, new MockHttpServletResponse()));
        this.jwtTestFixture.jwtSecurity.getRotation().revoke(this.jwtTestFixture.account);

        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(mockHttpServletRequest, new MockHttpServletResponse()));
        assertFalse(this.authenticates(loginMockHttpServletResponse));
    }

    @Test
    void unlimitedCapsStillPruneExpiredSessions() {
        for (final int maxSessions : new int[]{0, -1}) {
            this.jwtTestFixture.jwtSecurity.getRotation().revoke(this.jwtTestFixture.account);
            this.jwtTestFixture.jwtProperties.setMaxSessions(maxSessions);
            final MockHttpServletResponse expiredMockHttpServletResponse = this.jwtTestFixture.login();
            final MockHttpServletResponse liveMockHttpServletResponse = this.jwtTestFixture.login();
            this.age(expiredMockHttpServletResponse, 1L, 1L);
            final MockHttpServletResponse newMockHttpServletResponse = this.jwtTestFixture.login();

            assertTrue(this.jwtTestFixture.securityAccountProvider.getRefreshSession(this.jwtTestFixture.account, this.sessionId(expiredMockHttpServletResponse)).isEmpty());
            assertEquals(2, this.jwtTestFixture.jwtSecurity.getRotation().getSessionList(this.jwtTestFixture.account).size());
            assertTrue(this.authenticates(liveMockHttpServletResponse));
            assertTrue(this.authenticates(newMockHttpServletResponse));
        }
    }

    @Test
    void logoutWithoutFingerprintDeletesSessionWithResolvedAccount() {
        final MockHttpServletResponse loginMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletRequest mockHttpServletRequest = this.without(JwtTestFixture.request(loginMockHttpServletResponse), "FINGERPRINT");
        this.jwtTestFixture.jwtSecurity.getCookie().remove(mockHttpServletRequest, new MockHttpServletResponse(), this.jwtTestFixture.account);

        assertTrue(this.jwtTestFixture.securityAccountProvider.getRefreshSession(this.jwtTestFixture.account, this.sessionId(loginMockHttpServletResponse)).isEmpty());
        assertFalse(this.authenticates(loginMockHttpServletResponse));
    }

    @Test
    void logoutWithMangledFingerprintDeletesSession() {
        final MockHttpServletResponse loginMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletRequest mockHttpServletRequest = JwtTestFixture.request(loginMockHttpServletResponse);
        this.replaceCookie(mockHttpServletRequest, "FINGERPRINT", "mangled");
        this.jwtTestFixture.jwtSecurity.getCookie().remove(mockHttpServletRequest, new MockHttpServletResponse(), null);

        assertTrue(this.jwtTestFixture.securityAccountProvider.getRefreshSession(this.jwtTestFixture.account, this.sessionId(loginMockHttpServletResponse)).isEmpty());
        assertFalse(this.authenticates(loginMockHttpServletResponse));
    }

    @Test
    void logoutWithoutTokensOnlyClearsCookies() {
        final MockHttpServletResponse loginMockHttpServletResponse = this.jwtTestFixture.login();
        final RefreshSession refreshSession = this.session(loginMockHttpServletResponse);
        final MockHttpServletResponse logoutMockHttpServletResponse = new MockHttpServletResponse();
        this.jwtTestFixture.jwtSecurity.getCookie().remove(new MockHttpServletRequest(), logoutMockHttpServletResponse, null);

        assertEquals(3, logoutMockHttpServletResponse.getHeaders("Set-Cookie").size());
        assertTrue(logoutMockHttpServletResponse.getHeaders("Set-Cookie").stream().allMatch(header -> header.contains("Max-Age=0")));
        this.assertSessionEquals(refreshSession, this.session(loginMockHttpServletResponse));
        assertTrue(this.authenticates(loginMockHttpServletResponse));
    }

    @Test
    void logoutCannotDeleteAnotherAccountsSessionEvenWithItsKnownId() throws Exception {
        final JwtTestFixture.Account otherAccount = new JwtTestFixture.Account(UUID.randomUUID(), JwtTestFixture.TestRole.ADMIN);
        this.jwtTestFixture.accountMap.put(otherAccount.getId(), otherAccount);
        final MockHttpServletResponse otherMockHttpServletResponse = new MockHttpServletResponse();
        assertTrue(this.jwtTestFixture.jwtSecurity.getCookie().apply(otherMockHttpServletResponse, otherAccount));
        final UUID otherSessionId = this.sessionId(otherMockHttpServletResponse);
        final MockHttpServletRequest signedMockHttpServletRequest = this.signedAccessRequest(this.jwtTestFixture.account.getId().toString(), otherSessionId.toString(), 60000L, true, JwtTestFixture.properties());
        this.jwtTestFixture.jwtSecurity.getCookie().remove(signedMockHttpServletRequest, new MockHttpServletResponse(), otherAccount);

        assertTrue(this.jwtTestFixture.securityAccountProvider.getRefreshSession(otherAccount, otherSessionId).isPresent());
        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(JwtTestFixture.request(otherMockHttpServletResponse), new MockHttpServletResponse()));
        this.jwtTestFixture.jwtSecurity.getCookie().remove(signedMockHttpServletRequest, new MockHttpServletResponse(), null);
        assertTrue(this.jwtTestFixture.securityAccountProvider.getRefreshSession(otherAccount, otherSessionId).isPresent());
    }

    @Test
    void zeroGraceStillRotatesOnlyOnceWithinOneRequest() {
        this.jwtTestFixture.jwtProperties.setRotationGrace(Duration.ZERO);
        final MockHttpServletResponse loginMockHttpServletResponse = this.jwtTestFixture.login();
        final MockHttpServletRequest mockHttpServletRequest = this.refreshRequest(loginMockHttpServletResponse);
        final MockHttpServletResponse rotationMockHttpServletResponse = new MockHttpServletResponse();

        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(mockHttpServletRequest, rotationMockHttpServletResponse));
        final RefreshSession refreshSession = this.session(loginMockHttpServletResponse);
        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(mockHttpServletRequest, rotationMockHttpServletResponse));
        assertEquals(3, rotationMockHttpServletResponse.getHeaders("Set-Cookie").size());
        assertNull(mockHttpServletRequest.getAttribute("foundation.security.jwt.rotation-grace"));
        this.assertSessionEquals(refreshSession, this.session(loginMockHttpServletResponse));
        assertTrue(this.authenticates(rotationMockHttpServletResponse));
    }

    @Test
    void freshRequestWithPreviousRefreshDoesNotReuseSuccessfulCache() {
        this.jwtTestFixture.jwtProperties.setRotationGrace(Duration.ZERO);
        final MockHttpServletResponse loginMockHttpServletResponse = this.jwtTestFixture.login();
        this.rotate(loginMockHttpServletResponse);
        final MockHttpServletRequest freshMockHttpServletRequest = this.refreshRequest(loginMockHttpServletResponse);
        assertNull(freshMockHttpServletRequest.getAttribute("foundation.security.jwt.resolved-account"));

        assertFalse(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(freshMockHttpServletRequest, new MockHttpServletResponse()));
        assertTrue(this.jwtTestFixture.jwtSecurity.getRotation().getSessionList(this.jwtTestFixture.account).isEmpty());
    }

    @Test
    void cachedAccountIdIsLookedUpAgainRatherThanCachingTheAccountObject() {
        final MockHttpServletRequest mockHttpServletRequest = JwtTestFixture.request(this.jwtTestFixture.login());
        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(mockHttpServletRequest, new MockHttpServletResponse()));
        this.jwtTestFixture.accountMap.remove(this.jwtTestFixture.account.getId());

        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().getAccountByRequest(mockHttpServletRequest, new MockHttpServletResponse()).isEmpty());
    }

    @Test
    void unauthenticatedRequestCachesNothing() {
        final MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().getAccountByRequest(mockHttpServletRequest, new MockHttpServletResponse()).isEmpty());
        assertNull(mockHttpServletRequest.getAttribute("foundation.security.jwt.resolved-account"));
        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().getAccountByRequest(mockHttpServletRequest, new MockHttpServletResponse()).isEmpty());
        assertNull(mockHttpServletRequest.getAttribute("foundation.security.jwt.resolved-account"));
    }

    private boolean authenticates(final MockHttpServletResponse mockHttpServletResponse) {
        return this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(JwtTestFixture.request(mockHttpServletResponse), new MockHttpServletResponse());
    }

    private UUID sessionId(final MockHttpServletResponse mockHttpServletResponse) {
        return UUID.fromString(this.jwtTestFixture.jwtSecurity.getValidator().validate(JwtTestFixture.request(mockHttpServletResponse), TokenType.ACCESS_TOKEN).orElseThrow().get("sid", String.class));
    }

    private RefreshSession session(final MockHttpServletResponse mockHttpServletResponse) {
        return this.jwtTestFixture.securityAccountProvider.getRefreshSession(this.jwtTestFixture.account, this.sessionId(mockHttpServletResponse)).orElseThrow();
    }

    private void age(final MockHttpServletResponse mockHttpServletResponse, final long expireAt, final long rotatedAt) {
        final RefreshSession refreshSession = this.session(mockHttpServletResponse);
        assertTrue(this.jwtTestFixture.securityAccountProvider.updateRefreshSession(this.jwtTestFixture.account, new RefreshSession(refreshSession.getSessionId(), refreshSession.getTokenHash(), refreshSession.getPreviousTokenHash(), expireAt, rotatedAt), refreshSession.getTokenHash()));
    }

    private String signingSessionId() {
        return this.sessionId(this.jwtTestFixture.login()).toString();
    }

    private MockHttpServletResponse rotate(final MockHttpServletResponse previousMockHttpServletResponse) {
        final MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        assertTrue(this.jwtTestFixture.jwtSecurity.getAuthenticator().isAuthenticated(this.refreshRequest(previousMockHttpServletResponse), mockHttpServletResponse));
        assertEquals(3, mockHttpServletResponse.getHeaders("Set-Cookie").size());

        return mockHttpServletResponse;
    }

    private MockHttpServletRequest refreshRequest(final MockHttpServletResponse mockHttpServletResponse) {
        return this.without(JwtTestFixture.request(mockHttpServletResponse), "ACCESS-TOKEN");
    }

    private MockHttpServletRequest without(final MockHttpServletRequest mockHttpServletRequest, final String name) {
        mockHttpServletRequest.setCookies(Arrays.stream(mockHttpServletRequest.getCookies()).filter(cookie -> !cookie.getName().equals(name)).toArray(Cookie[]::new));

        return mockHttpServletRequest;
    }

    private void replaceCookie(final MockHttpServletRequest mockHttpServletRequest, final String name, final String value) {
        final MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        this.jwtTestFixture.cookieHelper.setCookie(mockHttpServletResponse, name, value, true, Duration.ofDays(1), true);
        final Cookie cookie = JwtTestFixture.cookies(mockHttpServletResponse)[0];
        mockHttpServletRequest.setCookies(Arrays.stream(mockHttpServletRequest.getCookies()).map(existingCookie -> existingCookie.getName().equals(name) ? cookie : existingCookie).toArray(Cookie[]::new));
    }

    private MockHttpServletRequest signedAccessRequest(final String subject, final String sessionId, final long expiresInMillis, final boolean fingerprint, final JwtProperties signingProperties) throws Exception {
        return this.tokenRequest(this.sign(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, subject, sessionId, expiresInMillis, fingerprint, signingProperties), TokenType.ACCESS_TOKEN);
    }

    private MockHttpServletRequest tokenRequest(final String token, final TokenType tokenType) {
        final MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        this.jwtTestFixture.cookieHelper.setCookie(mockHttpServletResponse, tokenType.getCookieNameFunction().apply(this.jwtTestFixture.jwtProperties), token, true, Duration.ofDays(1), true);
        this.jwtTestFixture.cookieHelper.setCookie(mockHttpServletResponse, "FINGERPRINT", "test-fingerprint", true, Duration.ofDays(1), true);

        return JwtTestFixture.request(mockHttpServletResponse);
    }

    private String sign(final TokenType signingTokenType, final TokenType claimedTokenType, final String subject, final String sessionId, final long expiresInMillis, final boolean fingerprint, final JwtProperties signingProperties) throws Exception {
        final JwtKeys jwtKeys = new JwtKeys(signingProperties, new FoundationProperties());
        final String fingerprintHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("test-fingerprint".getBytes(StandardCharsets.UTF_8)));

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer("foundation")
                .audience().add("foundation").and()
                .subject(subject)
                .claim("sid", sessionId)
                .claim("token_type", claimedTokenType.getKey())
                .claim("fingerprint", fingerprint ? fingerprintHash : null)
                .issuedAt(new Date(System.currentTimeMillis() - 60000L))
                .notBefore(new Date(System.currentTimeMillis() - 60000L))
                .expiration(new Date(System.currentTimeMillis() + expiresInMillis))
                .signWith(jwtKeys.getSigningKey(signingTokenType), Jwts.SIG.EdDSA)
                .compact();
    }

    private void assertSessionEquals(final RefreshSession expectedRefreshSession, final RefreshSession actualRefreshSession) {
        assertEquals(expectedRefreshSession.getSessionId(), actualRefreshSession.getSessionId());
        assertEquals(expectedRefreshSession.getPreviousTokenHash(), actualRefreshSession.getPreviousTokenHash());
        assertEquals(expectedRefreshSession.getTokenHash(), actualRefreshSession.getTokenHash());
        assertEquals(expectedRefreshSession.getExpireAt(), actualRefreshSession.getExpireAt());
        assertEquals(expectedRefreshSession.getRotatedAt(), actualRefreshSession.getRotatedAt());
    }
}