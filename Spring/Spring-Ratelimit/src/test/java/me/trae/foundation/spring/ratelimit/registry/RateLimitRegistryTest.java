package me.trae.foundation.spring.ratelimit.registry;

import me.trae.foundation.spring.ratelimit.RateLimitData;
import me.trae.foundation.spring.ratelimit.annotation.RateLimit;
import me.trae.foundation.spring.ratelimit.annotation.RateLimitShared;
import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;
import me.trae.foundation.spring.ratelimit.scope.RateLimitTarget;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RateLimitRegistryTest {

    @Test
    void methodAnnotationIsPickedUp() {
        final Map<Method, RateLimitData> dataMap = build(new MethodController());

        final RateLimitData rateLimitData = dataMap.get(method(MethodController.class, "create"));

        assertEquals(5, rateLimitData.getAttempts());
        assertEquals(TimeUnit.MINUTES.toMillis(1L), rateLimitData.getDurationMillis());
        assertEquals(RateLimitScope.IP_ADDRESS, rateLimitData.getScope());
    }

    @Test
    void unannotatedMethodIsAbsent() {
        final Map<Method, RateLimitData> dataMap = build(new MethodController());

        assertNull(dataMap.get(method(MethodController.class, "fetch")));
    }

    @Test
    void sharedWithTypeTargetGivesEveryMethodTheSameKey() {
        final Map<Method, RateLimitData> dataMap = build(new SharedTypeController());

        final RateLimitData first = dataMap.get(method(SharedTypeController.class, "create"));
        final RateLimitData second = dataMap.get(method(SharedTypeController.class, "fetch"));

        assertEquals(first.getKey(), second.getKey());
        assertEquals(SharedTypeController.class.getName(), first.getKey());
        assertEquals(30, first.getAttempts());
    }

    @Test
    void sharedWithMethodTargetGivesEveryMethodItsOwnKey() {
        final Map<Method, RateLimitData> dataMap = build(new SharedMethodController());

        final RateLimitData first = dataMap.get(method(SharedMethodController.class, "create"));
        final RateLimitData second = dataMap.get(method(SharedMethodController.class, "fetch"));

        assertNotEquals(first.getKey(), second.getKey());
        assertEquals(method(SharedMethodController.class, "create").toString(), first.getKey());
        assertEquals(30, first.getAttempts());
        assertEquals(30, second.getAttempts());
    }

    @Test
    void methodAnnotationReplacesTheSharedOne() {
        final Map<Method, RateLimitData> dataMap = build(new OverrideController());

        final RateLimitData overridden = dataMap.get(method(OverrideController.class, "refund"));
        final RateLimitData shared = dataMap.get(method(OverrideController.class, "fetch"));

        assertEquals(3, overridden.getAttempts());
        assertEquals(TimeUnit.MINUTES.toMillis(10L), overridden.getDurationMillis());
        assertEquals(method(OverrideController.class, "refund").toString(), overridden.getKey());

        assertEquals(OverrideController.class.getName(), shared.getKey());
        assertNotEquals(shared.getKey(), overridden.getKey());
    }

    @Test
    void sharedAnnotationIsInheritedFromTheBaseClass() {
        final Map<Method, RateLimitData> dataMap = build(new ChildController());

        final RateLimitData rateLimitData = dataMap.get(method(ChildController.class, "create"));

        assertEquals(60, rateLimitData.getAttempts());
        assertEquals(ChildController.class.getName(), rateLimitData.getKey());
    }

    @Test
    void registryStartsEmptyAndServesWhatWasRegistered() {
        final RateLimitRegistry rateLimitRegistry = new RateLimitRegistry();
        final HandlerMethod handlerMethod = handlerMethod(new MethodController(), "create");

        assertTrue(rateLimitRegistry.getDataByHandlerMethod(handlerMethod).isEmpty());

        rateLimitRegistry.register(build(new MethodController()));

        assertTrue(rateLimitRegistry.getDataByHandlerMethod(handlerMethod).isPresent());
    }

    private static Map<Method, RateLimitData> build(final Object controller) {
        final Map<Object, HandlerMethod> handlerMethodMap = new HashMap<>();

        for (final Method method : controller.getClass().getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge()) {
                continue;
            }

            handlerMethodMap.put(method, new HandlerMethod(controller, method));
        }

        return RateLimitRegistry.build(handlerMethodMap);
    }

    private static HandlerMethod handlerMethod(final Object controller, final String name) {
        return new HandlerMethod(controller, method(controller.getClass(), name));
    }

    private static Method method(final Class<?> type, final String name) {
        for (final Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }

            return method;
        }

        throw new IllegalStateException("No method %s on %s".formatted(name, type.getName()));
    }

    static class MethodController {

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 5, duration = 1, unit = TimeUnit.MINUTES)
        public void create() {
        }

        public void fetch() {
        }
    }

    @RateLimitShared(target = RateLimitTarget.TYPE, scope = RateLimitScope.IP_ADDRESS, attempts = 30, duration = 1, unit = TimeUnit.MINUTES)
    static class SharedTypeController {

        public void create() {
        }

        public void fetch() {
        }
    }

    @RateLimitShared(target = RateLimitTarget.METHOD, scope = RateLimitScope.IP_ADDRESS, attempts = 30, duration = 1, unit = TimeUnit.MINUTES)
    static class SharedMethodController {

        public void create() {
        }

        public void fetch() {
        }
    }

    @RateLimitShared(target = RateLimitTarget.TYPE, scope = RateLimitScope.IP_ADDRESS, attempts = 30, duration = 1, unit = TimeUnit.MINUTES)
    static class OverrideController {

        @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 3, duration = 10, unit = TimeUnit.MINUTES)
        public void refund() {
        }

        public void fetch() {
        }
    }

    @RateLimitShared(target = RateLimitTarget.TYPE, scope = RateLimitScope.IP_ADDRESS, attempts = 60, duration = 1, unit = TimeUnit.MINUTES)
    static class BaseController {
    }

    static class ChildController extends BaseController {

        public void create() {
        }
    }
}