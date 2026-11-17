package me.trae.foundation.database.tenant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import me.trae.foundation.database.tenant.exception.TenantException;

import java.util.UUID;

@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class Tenant {

    private final String name;

    public static Tenant of(final String name) {
        if (name == null || name.isBlank()) {
            throw new TenantException("Tenant name must not be blank");
        }

        return new Tenant(name);
    }

    public String prefix(final String table) {
        return this.name + ":" + table;
    }

    public String key(final String table, final UUID id) {
        return this.prefix(table) + ":" + id;
    }
}