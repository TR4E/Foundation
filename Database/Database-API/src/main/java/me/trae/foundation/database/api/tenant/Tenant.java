package me.trae.foundation.database.api.tenant;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;
import java.util.Optional;

@AllArgsConstructor
@Getter
public final class Tenant {

    public static final String GLOBAL = "*";

    private final String group;
    private final int instance;

    public static Tenant of(final String string) {
        Objects.requireNonNull(string, "string");

        final int separator = string.lastIndexOf('-');

        if (separator == -1) {
            return new Tenant(string, 1);
        }

        final String suffix = string.substring(separator + 1);

        if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) {
            return new Tenant(string, 1);
        }

        return new Tenant(string.substring(0, separator), Integer.parseInt(suffix));
    }

    public Optional<String> resolve(final TenantScope tenantScope) {
        return switch (tenantScope) {
            case NONE -> Optional.empty();
            case GLOBAL -> Optional.of(GLOBAL);
            case GROUP -> Optional.of(this.group);
            case INSTANCE -> Optional.of("%s-%s".formatted(this.group, this.instance));
        };
    }
}