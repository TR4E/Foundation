package me.trae.foundation.database.storage.local;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
public final class CacheEntry<Value> {

    private static final long NEVER_EXPIRES = Long.MIN_VALUE;

    private final Value value;
    private final long expiryNanos;
    private volatile long expiresAt;
    private final long absoluteExpiresAt;

    @Setter
    private volatile boolean pinned;

    public CacheEntry(final Value value, final Duration expiry, final boolean pinned) {
        this(value, expiry, pinned, expiry);
    }

    public CacheEntry(final Value value, final Duration expiry, final boolean pinned, final Duration maximumAge) {
        this.value = value;
        this.expiryNanos = expiry == null ? -1L : Math.max(0L, expiry.toNanos());
        final long now = System.nanoTime();
        this.absoluteExpiresAt = maximumAge == null ? NEVER_EXPIRES : now + Math.max(0L, maximumAge.toNanos());
        this.expiresAt = expiry == null ? NEVER_EXPIRES : now + this.expiryNanos;
        this.expiresAt = this.clampExpiry(this.expiresAt);
        this.pinned = pinned;
    }

    public void refreshExpiry() {
        if (!this.pinned && this.expiryNanos >= 0L) {
            this.expiresAt = this.clampExpiry(System.nanoTime() + this.expiryNanos);
        }
    }

    public boolean isExpired() {
        if (this.pinned) {
            return false;
        }

        final long now = System.nanoTime();

        return this.expiresAt != NEVER_EXPIRES && now - this.expiresAt >= 0 || this.absoluteExpiresAt != NEVER_EXPIRES && now - this.absoluteExpiresAt >= 0;
    }

    private long clampExpiry(final long expiryAt) {
        return this.absoluteExpiresAt != NEVER_EXPIRES && expiryAt - this.absoluteExpiresAt > 0 ? this.absoluteExpiresAt : expiryAt;
    }
}