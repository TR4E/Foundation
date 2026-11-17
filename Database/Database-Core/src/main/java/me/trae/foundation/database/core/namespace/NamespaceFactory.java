package me.trae.foundation.database.core.namespace;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.database.api.converter.ValueConverter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.index.Index;
import me.trae.foundation.database.api.storage.Namespace;
import me.trae.foundation.database.core.converter.UuidConverter;
import me.trae.foundation.database.core.settings.DatabaseSettings;
import me.trae.foundation.database.tenant.TenantScope;

import java.util.UUID;

@AllArgsConstructor
@Getter
public final class NamespaceFactory {

    private final UuidConverter uuidConverter = new UuidConverter();

    private final DatabaseSettings settings;
    private final TenantScope tenantScope;

    public <EntityType extends Entity> Namespace<UUID, EntityType> entity(final Class<EntityType> entityType, final String table, final ValueConverter<EntityType, String> converter) {
        return new Namespace<>(this.name(table), entityType, UUID::toString, converter, this.settings.getEntityTtl());
    }

    public Namespace<Object, UUID> index(final Index<?, ?> index) {
        return new Namespace<>(this.name(index.getName()), UUID.class, String::valueOf, this.uuidConverter, this.settings.getEntityTtl());
    }

    private String name(final String name) {
        return this.tenantScope.getTenant().map(tenant -> tenant.prefix(name)).orElse(name);
    }
}