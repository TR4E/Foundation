package me.trae.foundation.spring.ratelimit.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import me.trae.foundation.spring.common.FoundationProperties;
import me.trae.foundation.spring.common.address.IpAddressProperties;
import me.trae.foundation.spring.common.address.IpAddressResolver;
import me.trae.foundation.spring.ratelimit.account.EmptyRateLimitAccountResolver;
import me.trae.foundation.spring.ratelimit.account.RateLimitAccountResolver;
import me.trae.foundation.spring.ratelimit.annotation.RateLimit;
import me.trae.foundation.spring.ratelimit.registry.RateLimitRegistry;
import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;
import me.trae.foundation.spring.ratelimit.store.MemoryRateLimitStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
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
        assertTrue(interceptor(new EmptyRateLimitAccountResolver()).preHandle(request("203.0.113.9"), new MockHttpServletResponse(), new Object()));
    }

    @Test
    void passesThroughWhenTheMethodIsNotAnnotated() throws Exception {
        final RateLimitInterceptor rateLimitInterceptor = interceptor(new EmptyRateLimitAccountResolver());

        assertTrue(rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("fetch")));
    }

    @Test
    void allowsUpToTheLimitThenRejects() throws Exception {
        final RateLimitInterceptor rateLimitInterceptor = interceptor(new EmptyRateLimitAccountResolver());

        assertTrue(rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("create")));
        assertTrue(rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("create")));

        final MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(rateLimitInterceptor.preHandle(request("203.0.113.9"), response, handlerMethod("create")));
        assertEquals(429, response.getStatus());
    }

    @Test
    void rejectionCarriesRetryAfterAndNoStore() throws Exception {
        final RateLimitInterceptor rateLimitInterceptor = interceptor(new EmptyRateLimitAccountResolver());

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
        final RateLimitInterceptor rateLimitInterceptor = interceptor(new EmptyRateLimitAccountResolver());

        rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("create"));
        rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("create"));

        assertFalse(rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("create")));
        assertTrue(rateLimitInterceptor.preHandle(request("203.0.113.10"), new MockHttpServletResponse(), handlerMethod("create")));
    }

    @Test
    void rejectsWhenTheAddressCannotBeResolved() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor(new EmptyRateLimitAccountResolver()).preHandle(new MockHttpServletRequest(), response, handlerMethod("create")));
        assertEquals(429, response.getStatus());
    }

    @Test
    void accountScopeKeysByAccountWhenOneIsResolved() throws Exception {
        final RateLimitInterceptor rateLimitInterceptor = interceptor(httpServletRequest -> Optional.of("alice"));

        rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("transfer"));
        rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("transfer"));

        assertFalse(rateLimitInterceptor.preHandle(request("203.0.113.10"), new MockHttpServletResponse(), handlerMethod("transfer")));
    }

    @Test
    void accountScopeFallsBackToTheAddressWhenNobodyIsAuthenticated() throws Exception {
        final RateLimitInterceptor rateLimitInterceptor = interceptor(new EmptyRateLimitAccountResolver());

        rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("transfer"));
        rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("transfer"));

        assertFalse(rateLimitInterceptor.preHandle(request("203.0.113.9"), new MockHttpServletResponse(), handlerMethod("transfer")));
        assertTrue(rateLimitInterceptor.preHandle(request("203.0.113.10"), new MockHttpServletResponse(), handlerMethod("transfer")));
    }

    private RateLimitInterceptor interceptor(final RateLimitAccountResolver rateLimitAccountResolver) {
        final RateLimitRegistry rateLimitRegistry = new RateLimitRegistry();
        final TestController controller = new TestController();

        rateLimitRegistry.register(RateLimitRegistry.build(Map.of(
                "create", new HandlerMethod(controller, method("create")),
                "fetch", new HandlerMethod(controller, method("fetch")),
                "transfer", new HandlerMethod(controller, method("transfer"))
        )));

        final FoundationProperties foundationProperties = new FoundationProperties();
        foundationProperties.setProduction(true);

        return new RateLimitInterceptor(rateLimitRegistry, this.store, new IpAddressResolver(foundationProperties, new IpAddressProperties()), rateLimitAccountResolver);
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
}