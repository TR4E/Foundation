package me.trae.foundation.spring.ratelimit.registry;

import me.trae.foundation.spring.ratelimit.RateLimitData;
import me.trae.foundation.spring.ratelimit.annotation.RateLimit;
import me.trae.foundation.spring.ratelimit.annotation.RateLimitShared;
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
                rateLimitDataMap.put(method, new RateLimitData(method.toString(), rateLimit.scope(), rateLimit.unit().toMillis(rateLimit.duration()), rateLimit.attempts()));
                continue;
            }

            final RateLimitShared rateLimitShared = type.getAnnotation(RateLimitShared.class);
            if (rateLimitShared != null) {
                rateLimitDataMap.put(method, new RateLimitData(type.getName(), rateLimitShared.scope(), rateLimitShared.unit().toMillis(rateLimitShared.duration()), rateLimitShared.attempts()));
            }
        }

        return rateLimitDataMap;
    }
}