package me.trae.foundation.spring.ratelimit.store;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public final class RateLimitWindow {

    private final long expirationMillis;

    private int attempts;

    public boolean isExpired(final long now) {
        return now >= this.expirationMillis;
    }

    public long getRemainingMillis(final long now) {
        return this.expirationMillis - now;
    }

    public void addAttempt() {
        this.attempts++;
    }
}