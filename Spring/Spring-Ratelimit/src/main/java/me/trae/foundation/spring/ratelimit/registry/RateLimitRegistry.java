package me.trae.foundation.spring.ratelimit.registry;

import me.trae.foundation.spring.ratelimit.RateLimitData;
import me.trae.foundation.spring.ratelimit.annotation.RateLimit;
import me.trae.foundation.spring.ratelimit.annotation.RateLimitShared;
import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;
import me.trae.foundation.spring.ratelimit.scope.RateLimitTarget;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class RateLimitRegistry {

    private volatile Map<Method, RateLimitData> dataMap = Map.of();

    public void register(final Map<Method, RateLimitData> dataMap) {
        this.dataMap = Map.copyOf(dataMap);
    }

    public Optional<RateLimitData> getDataByHandlerMethod(final HandlerMethod handlerMethod) {
        return Optional.ofNullable(this.dataMap.get(handlerMethod.getMethod()));
    }

    public static Map<Method, RateLimitData> build(final Map<?, HandlerMethod> handlerMethodMap) {
        final Map<Method, RateLimitData> rateLimitDataMap = new HashMap<>();

        for (final HandlerMethod handlerMethod : handlerMethodMap.values()) {
            final Method method = handlerMethod.getMethod();

            if (rateLimitDataMap.containsKey(method)) {
                continue;
            }

            final Class<?> type = handlerMethod.getBeanType();

            final RateLimit rateLimit = method.getAnnotation(RateLimit.class);
            if (rateLimit != null) {
                rateLimitDataMap.put(method, create(resolveKey(rateLimit.name(), method.toString()), rateLimit.scope(), rateLimit.attempts(), rateLimit.unit().toMillis(rateLimit.duration()), method));
                continue;
            }

            final RateLimitShared rateLimitShared = type.getAnnotation(RateLimitShared.class);
            if (rateLimitShared != null) {
                rateLimitDataMap.put(method, create(resolveKey(rateLimitShared.name(), rateLimitShared.target() == RateLimitTarget.TYPE ? type.getName() : method.toString()), rateLimitShared.scope(), rateLimitShared.attempts(), rateLimitShared.unit().toMillis(rateLimitShared.duration()), method));
            }
        }

        return rateLimitDataMap;
    }

    private static String resolveKey(final String name, final String fallback) {
        return name == null || name.isBlank() ? fallback : name;
    }

    private static RateLimitData create(final String key, final RateLimitScope scope, final int attempts, final long durationMillis, final Method method) {
        if (attempts < 1) {
            throw new IllegalStateException("Rate limit on %s declares %s attempts, expected at least 1".formatted(method, attempts));
        }

        if (durationMillis < 1L) {
            throw new IllegalStateException("Rate limit on %s declares a duration of %s milliseconds, expected at least 1".formatted(method, durationMillis));
        }

        return new RateLimitData(key, scope, durationMillis, attempts);
    }
}