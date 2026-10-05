package me.trae.foundation.database.api.tenant;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;

@AllArgsConstructor
@Getter
public final class Tenant {

    public static final String GLOBAL = "*";

    private final String group;
    private final int instance;

    public Optional<String> resolve(final TenantScope tenantScope) {
        return switch (tenantScope) {
            case NONE -> Optional.empty();
            case GLOBAL -> Optional.of(GLOBAL);
            case GROUP -> Optional.of(this.group);
            case INSTANCE -> Optional.of("%s-%s".formatted(this.group, this.instance));
        };
    }
}