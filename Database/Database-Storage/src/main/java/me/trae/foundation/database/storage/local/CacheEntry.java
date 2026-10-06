package me.trae.foundation.database.storage.local;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
public final class CacheEntry<Value> {

    private static final long NEVER_EXPIRES = Long.MIN_VALUE;

    private final Value value;
    private final long expiresAt;

    @Setter
    private volatile boolean pinned;

    public CacheEntry(final Value value, final Duration expiry, final boolean pinned) {
        this.value = value;
        this.expiresAt = expiry == null ? NEVER_EXPIRES : System.nanoTime() + expiry.toNanos();
        this.pinned = pinned;
    }

    public boolean isExpired() {
        if (this.pinned || this.expiresAt == NEVER_EXPIRES) {
            return false;
        }

        return System.nanoTime() - this.expiresAt >= 0;
    }
}