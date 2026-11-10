package me.trae.foundation.spring.ratelimit.registry;

import me.trae.foundation.spring.common.FoundationProperties;
import me.trae.foundation.spring.common.address.IpAddressProperties;
import me.trae.foundation.spring.common.address.IpAddressResolver;
import me.trae.foundation.spring.ratelimit.RateLimitData;
import me.trae.foundation.spring.ratelimit.annotation.RateLimit;
import me.trae.foundation.spring.ratelimit.annotation.RateLimitShared;
import me.trae.foundation.spring.ratelimit.interceptor.RateLimitInterceptor;
import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;
import me.trae.foundation.spring.ratelimit.scope.RateLimitTarget;
import me.trae.foundation.spring.ratelimit.store.MemoryRateLimitStore;
import me.trae.foundation.spring.security.account.EmptySecurityAccountProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RateLimitConfigurationTest {

    @Test
    void explicitMethodNameReplacesReflectedKey() throws Exception {
        assertEquals("orders", data(new NamedController(), "orders").getKey());
    }

    @Test
    void explicitSharedNameTakesPrecedenceOverMethodTarget() throws Exception {
        assertEquals("invoices", data(new SharedController(), "first").getKey());
        assertEquals("invoices", data(new SharedController(), "second").getKey());
    }

    @Test
    void invalidAttemptCountFailsRegistration() {
        assertInvalid(new InvalidController(), "attempts");
        assertInvalid(new InvalidController(), "negativeAttempts");
    }

    @Test
    void durationRoundedToZeroMillisecondsFailsRegistration() {
        assertInvalid(new InvalidController(), "duration");
        assertInvalid(new InvalidController(), "microseconds");
    }

    @Test
    void namedMethodsInDifferentControllersConsumeOneSharedBucket() throws Exception {
        this.assertBudgets(new LoginController(), "login", new OtherLoginController(), "login", true);
    }

    @Test
    void differentlyNamedMethodsConsumeIndependentBuckets() throws Exception {
        this.assertBudgets(new LoginController(), "login", new NamedController(), "orders", false);
    }

    @Test
    void blankNamesFallBackToMethodSignatureAndTypeName() throws Exception {
        assertEquals(BlankController.class.getDeclaredMethod("orders").toString(), data(new BlankController(), "orders").getKey());
        assertEquals(BlankSharedController.class.getName(), data(new BlankSharedController(), "orders").getKey());
    }

    @Test
    void explicitSharedNameTakesPrecedenceOverTypeTarget() throws Exception {
        assertEquals("invoices", data(new SharedTypeController(), "first").getKey());
        assertEquals("invoices", data(new SharedTypeController(), "second").getKey());
    }

    @Test
    void zeroDurationFailsRegistrationInEveryTimeUnit() {
        for (final Method method : ZeroDurationController.class.getDeclaredMethods()) {
            assertInvalid(new ZeroDurationController(), method.getName());
        }
    }

    private void assertBudgets(final Object firstController, final String firstMethodName, final Object secondController, final String secondMethodName, final boolean shared) throws Exception {
        final HandlerMethod firstHandlerMethod = new HandlerMethod(firstController, firstController.getClass().getDeclaredMethod(firstMethodName));
        final HandlerMethod secondHandlerMethod = new HandlerMethod(secondController, secondController.getClass().getDeclaredMethod(secondMethodName));
        final RateLimitRegistry rateLimitRegistry = new RateLimitRegistry();
        rateLimitRegistry.register(RateLimitRegistry.build(Map.of(firstHandlerMethod.getMethod(), firstHandlerMethod, secondHandlerMethod.getMethod(), secondHandlerMethod)));
        final MemoryRateLimitStore memoryRateLimitStore = new MemoryRateLimitStore();

        try {
            final RateLimitInterceptor rateLimitInterceptor = new RateLimitInterceptor(rateLimitRegistry, memoryRateLimitStore, new IpAddressResolver(new FoundationProperties(), new IpAddressProperties()), new EmptySecurityAccountProvider());
            final MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
            mockHttpServletRequest.addHeader("X-Forwarded-For", "203.0.113.1");
            assertTrue(rateLimitInterceptor.preHandle(mockHttpServletRequest, new MockHttpServletResponse(), firstHandlerMethod));
            final MockHttpServletResponse secondMockHttpServletResponse = new MockHttpServletResponse();
            assertEquals(!shared, rateLimitInterceptor.preHandle(mockHttpServletRequest, secondMockHttpServletResponse, secondHandlerMethod));
            assertEquals(shared ? 429 : 200, secondMockHttpServletResponse.getStatus());
        } finally {
            memoryRateLimitStore.shutdown();
        }
    }

    private static void assertInvalid(final Object controller, final String methodName) {
        final IllegalStateException illegalStateException = assertThrows(IllegalStateException.class, () -> data(controller, methodName));
        assertTrue(illegalStateException.getMessage().contains(controller.getClass().getName()));
        assertTrue(illegalStateException.getMessage().contains(methodName));
    }

    private static RateLimitData data(final Object controller, final String methodName) throws NoSuchMethodException {
        final Method method = controller.getClass().getDeclaredMethod(methodName);

        return RateLimitRegistry.build(Map.of(method, new HandlerMethod(controller, method))).get(method);
    }

    static final class NamedController {

        @RateLimit(name = "orders", scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 1, unit = TimeUnit.MINUTES)
        public void orders() {
        }
    }

    @RateLimitShared(name = "invoices", target = RateLimitTarget.METHOD, scope = RateLimitScope.ACCOUNT, attempts = 1, duration = 1, unit = TimeUnit.MINUTES)
    static final class SharedController {

        public void first() {
        }

        public void second() {
        }
    }

    static final class InvalidController {

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 0, duration = 1, unit = TimeUnit.MINUTES)
        public void attempts() {
        }

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = -1, duration = 1, unit = TimeUnit.MINUTES)
        public void negativeAttempts() {
        }

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 1, unit = TimeUnit.MICROSECONDS)
        public void microseconds() {
        }

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 1, unit = TimeUnit.NANOSECONDS)
        public void duration() {
        }
    }

    static final class LoginController {

        @RateLimit(name = "login", scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 1, unit = TimeUnit.MINUTES)
        public void login() {
        }
    }

    static final class OtherLoginController {

        @RateLimit(name = "login", scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 1, unit = TimeUnit.MINUTES)
        public void login() {
        }
    }

    static final class BlankController {

        @RateLimit(name = "   ", scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 1, unit = TimeUnit.MINUTES)
        public void orders() {
        }
    }

    @RateLimitShared(name = "   ", target = RateLimitTarget.TYPE, scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 1, unit = TimeUnit.MINUTES)
    static final class BlankSharedController {

        public void orders() {
        }
    }

    @RateLimitShared(name = "invoices", target = RateLimitTarget.TYPE, scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 1, unit = TimeUnit.MINUTES)
    static final class SharedTypeController {

        public void first() {
        }

        public void second() {
        }
    }

    static final class ZeroDurationController {

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 0, unit = TimeUnit.NANOSECONDS)
        public void nanoseconds() {
        }

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 0, unit = TimeUnit.MICROSECONDS)
        public void microseconds() {
        }

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 0, unit = TimeUnit.MILLISECONDS)
        public void milliseconds() {
        }

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 0, unit = TimeUnit.SECONDS)
        public void seconds() {
        }

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 0, unit = TimeUnit.MINUTES)
        public void minutes() {
        }

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 0, unit = TimeUnit.HOURS)
        public void hours() {
        }

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 1, duration = 0, unit = TimeUnit.DAYS)
        public void days() {
        }

    }
}