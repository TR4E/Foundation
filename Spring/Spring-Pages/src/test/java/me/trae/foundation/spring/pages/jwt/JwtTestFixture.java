package me.trae.foundation.spring.pages.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import me.trae.foundation.spring.common.FoundationProperties;
import me.trae.foundation.spring.common.account.AccountIdentity;
import me.trae.foundation.spring.common.account.AccountRoleDefinition;
import me.trae.foundation.spring.common.account.CommonAccountProvider;
import me.trae.foundation.spring.common.cookie.CookieHelper;
import me.trae.foundation.spring.security.account.SecurityAccountProvider;
import me.trae.foundation.spring.security.jwt.JwtProperties;
import me.trae.foundation.spring.security.jwt.JwtSecurity;
import me.trae.foundation.spring.security.jwt.refresh.RefreshSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.HttpCookie;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class JwtTestFixture {

    final Map<UUID, Account> accountMap = new HashMap<>();

    final FoundationProperties foundationProperties = new FoundationProperties();
    final Account account = new Account(UUID.randomUUID(), TestRole.ADMIN);
    final InMemorySecurityAccountProvider securityAccountProvider = new InMemorySecurityAccountProvider();
    final CommonAccountProvider<Account> commonAccountProvider = id -> Optional.ofNullable(this.accountMap.get(id));
    final CookieHelper cookieHelper = new CookieHelper(this.foundationProperties);

    final JwtProperties jwtProperties;
    final JwtSecurity<Account> jwtSecurity;

    JwtTestFixture() {
        this(false);
    }

    JwtTestFixture(final boolean production) {
        this(production, properties());
    }

    JwtTestFixture(final boolean production, final JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.foundationProperties.setProduction(production);
        this.accountMap.put(this.account.getId(), this.account);
        this.jwtSecurity = new JwtSecurity<>(this.jwtProperties, this.foundationProperties, this.commonAccountProvider, this.securityAccountProvider, this.cookieHelper);
        this.securityAccountProvider.jwtSecurity = this.jwtSecurity;
    }

    static JwtProperties properties() {
        final JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setAccessTokenKeySeed(seed(1));
        jwtProperties.setRefreshTokenKeySeed(seed(2));

        return jwtProperties;
    }

    static String seed(final int value) {
        final byte[] bytes = new byte[32];
        bytes[0] = (byte) value;

        return Base64.getEncoder().encodeToString(bytes);
    }

    MockHttpServletResponse login() {
        final MockHttpServletResponse mockHttpServletResponse = new MockHttpServletResponse();
        this.jwtSecurity.getCookie().apply(mockHttpServletResponse, this.account);

        return mockHttpServletResponse;
    }

    static Cookie[] cookies(final MockHttpServletResponse mockHttpServletResponse) {
        return mockHttpServletResponse.getHeaders("Set-Cookie").stream()
                .flatMap(value -> HttpCookie.parse(value).stream())
                .map(httpCookie -> new Cookie(httpCookie.getName(), httpCookie.getValue()))
                .toArray(Cookie[]::new);
    }

    static MockHttpServletRequest request(final MockHttpServletResponse mockHttpServletResponse) {
        final MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        mockHttpServletRequest.setCookies(cookies(mockHttpServletResponse));

        return mockHttpServletRequest;
    }

    static RefreshSession copy(final RefreshSession refreshSession) {
        return new RefreshSession(refreshSession.getSessionId(), refreshSession.getTokenHash(), refreshSession.getPreviousTokenHash(), refreshSession.getExpireAt(), refreshSession.getRotatedAt());
    }

    static final class InMemorySecurityAccountProvider implements SecurityAccountProvider<Account> {

        private final Map<UUID, Map<UUID, RefreshSession>> refreshSessionMap = new HashMap<>();

        boolean failConditionalWrite;

        JwtSecurity<Account> jwtSecurity;

        @Override
        public Optional<Account> getAccountByRequest(final HttpServletRequest mockHttpServletRequest, final HttpServletResponse mockHttpServletResponse) {
            return this.jwtSecurity.getAuthenticator().getAccountByRequest(mockHttpServletRequest, mockHttpServletResponse);
        }

        @Override
        public synchronized Optional<RefreshSession> getRefreshSession(final Account account, final UUID sessionId) {
            return Optional.ofNullable(this.refreshSessionMap.getOrDefault(account.getId(), Collections.emptyMap()).get(sessionId)).map(JwtTestFixture::copy);
        }

        @Override
        public synchronized List<RefreshSession> getRefreshSessionList(final Account account) {
            return this.refreshSessionMap.getOrDefault(account.getId(), Collections.emptyMap()).values().stream().map(JwtTestFixture::copy).toList();
        }

        @Override
        public synchronized boolean updateRefreshSession(final Account account, final RefreshSession refreshSession, final String expectedTokenHash) {
            final String persistedTokenHash = this.getRefreshSession(account, refreshSession.getSessionId()).map(RefreshSession::getTokenHash).orElse(null);
            if (this.failConditionalWrite || !Objects.equals(persistedTokenHash, expectedTokenHash)) {
                return false;
            }

            this.refreshSessionMap.computeIfAbsent(account.getId(), _ -> new HashMap<>()).put(refreshSession.getSessionId(), copy(refreshSession));

            return true;
        }

        @Override
        public synchronized void deleteRefreshSession(final Account account, final UUID sessionId) {
            this.refreshSessionMap.getOrDefault(account.getId(), new HashMap<>()).remove(sessionId);
        }

        @Override
        public synchronized void deleteRefreshSessionList(final Account account) {
            this.refreshSessionMap.remove(account.getId());
        }
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @AllArgsConstructor
    @Getter
    static final class Account implements AccountIdentity<TestRole> {

        @EqualsAndHashCode.Include
        private final UUID id;

        private final TestRole role;
    }

    enum TestRole implements AccountRoleDefinition {

        MEMBER, ADMIN, OWNER
    }
}