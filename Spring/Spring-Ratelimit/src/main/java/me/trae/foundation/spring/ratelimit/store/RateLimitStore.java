package me.trae.foundation.spring.ratelimit.store;

import me.trae.foundation.spring.ratelimit.RateLimitData;

public interface RateLimitStore {

    long tryConsume(final String key, final RateLimitData rateLimitData);
}