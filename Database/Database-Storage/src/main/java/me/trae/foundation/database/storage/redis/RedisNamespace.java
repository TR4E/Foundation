package me.trae.foundation.database.storage.redis;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.tenant.Tenant;
import me.trae.foundation.database.api.tenant.TenantScope;

import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class RedisNamespace {

    private final String table;
    private final String tenantId;

    public static RedisNamespace of(final String table, final Tenant tenant, final TenantScope tenantScope) {
        return new RedisNamespace(table, tenant.resolve(tenantScope).orElse(null));
    }

    public static RedisNamespace of(final String table, final String tenantId) {
        return new RedisNamespace(table, tenantId);
    }

    public static RedisNamespace of(final String table) {
        return new RedisNamespace(table, null);
    }

    public RedisNamespace withTenant(final String tenantId) {
        return new RedisNamespace(this.table, tenantId);
    }

    public Optional<String> getTenantId() {
        return Optional.ofNullable(this.tenantId);
    }

    public String getKey(final UUID id) {
        return this.getKey(id.toString());
    }

    public String getKey(final String suffix) {
        if (this.tenantId == null) {
            return "%s:%s".formatted(this.table, suffix);
        }

        return "%s:%s:%s".formatted(this.table, this.tenantId, suffix);
    }

    public String getInvalidationChannel() {
        return this.getKey("cache:invalidate");
    }

    public RedisInvalidation getInvalidation(final UUID id, final String instanceId) {
        return new RedisInvalidation(
                this.getKey(id),
                this.getKey("cache:missing:%s".formatted(id)),
                this.getKey("cache:fill:%s".formatted(id)),
                this.getInvalidationChannel(),
                id,
                instanceId
        );
    }
}