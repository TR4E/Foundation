package me.trae.foundation.spring.ratelimit.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import me.trae.foundation.spring.common.FoundationProperties;
import me.trae.foundation.spring.common.account.AccountIdentity;
import me.trae.foundation.spring.common.account.AccountRoleDefinition;
import me.trae.foundation.spring.common.address.IpAddressProperties;
import me.trae.foundation.spring.common.address.IpAddressResolver;
import me.trae.foundation.spring.ratelimit.annotation.RateLimit;
import me.trae.foundation.spring.ratelimit.registry.RateLimitRegistry;
import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;
import me.trae.foundation.spring.ratelimit.store.MemoryRateLimitStore;
import me.trae.foundation.spring.ratelimit.store.RateLimitStore;
import me.trae.foundation.spring.security.account.EmptySecurityAccountProvider;
import me.trae.foundation.spring.security.account.SecurityAccountProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RateLimitInterceptorTest {

    private final MemoryRateLimitStore store = new MemoryRateLimitStore();

    @AfterEach
    void shutdown() {
        this.store.shutdown();
    }

    @Test
    void passesThroughWhenTheHandlerIsNotAHandlerMethod() throws Exception {
        assertTrue(this.interceptor(new EmptySecurityAccountProvider()).preHandle(request("203.0.113.9"), new MockHttpServletResponse(), new Object()));
    }

    @Test
    void passesThroughWhenTheMethodIsNotAnnotated() throws Exception {
        final RateLimitInterceptor rateLimitInterceptor = this.interceptor(new EmptySecurityAccountProvider());

        assertTrue(rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("fetch")));
    }

    @Test
    void allowsUpToTheLimitThenRejects() throws Exception {
        final RateLimitInterceptor rateLimitInterceptor = this.interceptor(new EmptySecurityAccountProvider());

        assertTrue(rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("create")));
        assertTrue(rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("create")));

        final MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(rateLimitInterceptor.preHandle(request("203.0.113.9"), response, handlerMethod("create")));
        assertEquals(429, response.getStatus());
    }

    @Test
    void rejectionCarriesRetryAfterAndNoStore() throws Exception {
        final RateLimitInterceptor rateLimitInterceptor = this.interceptor(new EmptySecurityAccountProvider());

        rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("create"));
        rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("create"));

        final MockHttpServletResponse response = new MockHttpServletResponse();

        rateLimitInterceptor.preHandle(request("203.0.113.9"), response, handlerMethod("create"));

        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertTrue(Integer.parseInt(response.getHeader("Retry-After")) >= 1);
        assertTrue(response.getContentAsString().contains("\"retryAfter\":"));
    }

    @Test
    void separateAddressesHaveSeparateBudgets() throws Exception {
        final RateLimitInterceptor rateLimitInterceptor = this.interceptor(new EmptySecurityAccountProvider());

        rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("create"));
        rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("create"));

        assertFalse(rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("create")));
        assertTrue(rateLimitInterceptor.preHandle(request("203.0.113.10"), new MockHttpServletResponse(), handlerMethod("create")));
    }

    @Test
    void rejectsWhenTheAddressCannotBeResolved() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(this.interceptor(new EmptySecurityAccountProvider()).preHandle(new MockHttpServletRequest(), response, handlerMethod("create")));
        assertEquals(429, response.getStatus());
    }

    @Test
    void accountScopeKeysByAccountWhenOneIsResolved() throws Exception {
        final RateLimitInterceptor rateLimitInterceptor = this.interceptor(new EmptySecurityAccountProvider() {

            @Override
            public Optional<TestAccount> getAccountByRequest(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
                return Optional.of(new TestAccount(UUID.fromString("00000000-0000-0000-0000-000000000001"), TestRole.MEMBER));
            }
        });

        rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("transfer"));
        rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("transfer"));

        assertFalse(rateLimitInterceptor.preHandle(request("203.0.113.10"), new MockHttpServletResponse(), handlerMethod("transfer")));
    }

    @Test
    void accountScopeFallsBackToTheAddressWhenNobodyIsAuthenticated() throws Exception {
        final RateLimitInterceptor rateLimitInterceptor = this.interceptor(new EmptySecurityAccountProvider());

        rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("transfer"));
        rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("transfer"));

        assertFalse(rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("transfer")));
        assertTrue(rateLimitInterceptor.preHandle(request("203.0.113.10"), new MockHttpServletResponse(), handlerMethod("transfer")));
    }

    private RateLimitInterceptor interceptor(final SecurityAccountProvider<?> accountProvider) {
        return this.interceptor(accountProvider, this.store);
    }

    @Test
    void accountScopeUsesUuidKeysAndFallsBackToIpKeysOnAnEmptyResolution() throws Exception {
        final List<String> keyList = new ArrayList<>();
        final SecurityAccountProvider<?> securityAccountProvider = new EmptySecurityAccountProvider() {

            @Override
            public Optional<TestAccount> getAccountByRequest(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
                httpServletResponse.setHeader("Provider-Response", "used");

                return Optional.ofNullable(httpServletRequest.getHeader("Test-Account")).map(UUID::fromString).map(id -> new TestAccount(id, TestRole.MEMBER));
            }
        };
        final RateLimitStore rateLimitStore = (key, _) -> {
            keyList.add(key);

            return 0L;
        };
        final RateLimitInterceptor rateLimitInterceptor = this.interceptor(securityAccountProvider, rateLimitStore);
        final MockHttpServletRequest authenticatedRequest = new MockHttpServletRequest();
        authenticatedRequest.addHeader("CF-Connecting-IP", "203.0.113.9");
        authenticatedRequest.addHeader("Test-Account", "00000000-0000-0000-0000-000000000001");
        final MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        assertTrue(rateLimitInterceptor.preHandle(authenticatedRequest, httpServletResponse, handlerMethod("transfer")));
        assertTrue(rateLimitInterceptor.preHandle(request("203.0.113.10"), new MockHttpServletResponse(), handlerMethod("transfer")));
        assertTrue(keyList.get(0).endsWith(":account:00000000-0000-0000-0000-000000000001"));
        assertTrue(keyList.get(1).endsWith(":ip:203.0.113.10"));
        assertEquals("used", httpServletResponse.getHeader("Provider-Response"));
    }

    private RateLimitInterceptor interceptor(final SecurityAccountProvider<?> accountProvider, final RateLimitStore rateLimitStore) {
        final RateLimitRegistry rateLimitRegistry = new RateLimitRegistry();
        final TestController controller = new TestController();

        rateLimitRegistry.register(RateLimitRegistry.build(Map.of("create", new HandlerMethod(controller, method("create")), "fetch", new HandlerMethod(controller, method("fetch")), "transfer", new HandlerMethod(controller, method("transfer")))));

        final FoundationProperties foundationProperties = new FoundationProperties();
        foundationProperties.setProduction(true);

        return new RateLimitInterceptor(rateLimitRegistry, rateLimitStore, new IpAddressResolver(foundationProperties, new IpAddressProperties()), accountProvider);
    }

    @Test
    void anUntrustedPeersForgedHeaderCannotChooseTheBudgetKey() throws Exception {
        final List<String> keyList = new ArrayList<>();
        final RateLimitStore rateLimitStore = (key, _) -> {
            keyList.add(key);

            return 0L;
        };
        final RateLimitInterceptor rateLimitInterceptor = trustedProxyInterceptor(rateLimitStore);
        final MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setRemoteAddr("198.51.100.8");
        httpServletRequest.addHeader("CF-Connecting-IP", "203.0.113.9");

        assertTrue(rateLimitInterceptor.preHandle(httpServletRequest, new MockHttpServletResponse(), handlerMethod("create")));
        assertEquals(1, keyList.size());
        assertTrue(keyList.get(0).endsWith(":ip:198.51.100.8"));
        assertFalse(keyList.get(0).endsWith(":ip:203.0.113.9"));
    }

    @Test
    void aTrustedPeerWithoutTheForwardedHeaderReceives429() throws Exception {
        final MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.setRemoteAddr("192.0.2.8");
        final MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        assertFalse(trustedProxyInterceptor(this.store).preHandle(httpServletRequest, httpServletResponse, handlerMethod("create")));
        assertEquals(429, httpServletResponse.getStatus());
    }

    private static RateLimitInterceptor trustedProxyInterceptor(final RateLimitStore rateLimitStore) {
        final FoundationProperties foundationProperties = new FoundationProperties();
        foundationProperties.setProduction(true);
        final IpAddressProperties ipAddressProperties = new IpAddressProperties();
        ipAddressProperties.setTrustedProxyList(Collections.singletonList("192.0.2.0/24"));
        final RateLimitRegistry rateLimitRegistry = new RateLimitRegistry();
        rateLimitRegistry.register(RateLimitRegistry.build(Collections.singletonMap("create", handlerMethod("create"))));

        return new RateLimitInterceptor(rateLimitRegistry, rateLimitStore, new IpAddressResolver(foundationProperties, ipAddressProperties), new EmptySecurityAccountProvider());
    }

    private static HandlerMethod handlerMethod(final String name) {
        return new HandlerMethod(new TestController(), method(name));
    }

    private static Method method(final String name) {
        for (final Method method : TestController.class.getDeclaredMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }

            return method;
        }

        throw new IllegalStateException("No method %s".formatted(name));
    }

    private static HttpServletRequest request(final String address) {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-Connecting-IP", address);

        return request;
    }

    static class TestController {

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 2, duration = 1, unit = TimeUnit.MINUTES)
        public void create() {
        }

        @RateLimit(scope = RateLimitScope.ACCOUNT, attempts = 2, duration = 1, unit = TimeUnit.MINUTES)
        public void transfer() {
        }

        public void fetch() {
        }
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @AllArgsConstructor
    @Getter
    static final class TestAccount implements AccountIdentity<TestRole> {

        @EqualsAndHashCode.Include
        private final UUID id;

        private final TestRole role;
    }

    enum TestRole implements AccountRoleDefinition {

        MEMBER
    }
}