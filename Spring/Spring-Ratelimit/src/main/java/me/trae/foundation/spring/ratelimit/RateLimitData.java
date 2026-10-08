package me.trae.foundation.spring.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;

@AllArgsConstructor
@Getter
public final class RateLimitData {

    private final String key;
    private final RateLimitScope scope;
    private final long durationMillis;
    private final int attempts;
}