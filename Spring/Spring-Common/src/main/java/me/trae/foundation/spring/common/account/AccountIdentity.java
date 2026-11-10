package me.trae.foundation.spring.common.account;

import java.util.UUID;

public interface AccountIdentity<AccountRole extends AccountRoleDefinition> {

    UUID getId();

    AccountRole getRole();

    default boolean hasRole(final AccountRoleDefinition accountRoleDefinition) {
        return this.getRole().ordinal() >= accountRoleDefinition.ordinal();
    }
}