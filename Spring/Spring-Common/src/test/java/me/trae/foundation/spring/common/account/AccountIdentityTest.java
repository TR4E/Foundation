package me.trae.foundation.spring.common.account;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AccountIdentityTest {

    @Test
    void higherRolesInheritLowerRoleAccess() {
        final Account account = new Account(UUID.randomUUID(), AccountRole.OWNER);

        assertTrue(account.hasRole(AccountRole.ADMIN));
        assertTrue(account.hasRole(AccountRole.MEMBER));
    }

    @Test
    void lowerRolesCannotAccessHigherRoles() {
        final Account account = new Account(UUID.randomUUID(), AccountRole.MODERATOR);

        assertFalse(account.hasRole(AccountRole.ADMIN));
    }

    @Test
    void accountsHaveAccessToTheirOwnRole() {
        final Account account = new Account(UUID.randomUUID(), AccountRole.ADMIN);

        assertTrue(account.hasRole(AccountRole.ADMIN));
    }

    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @AllArgsConstructor
    @Getter
    static final class Account implements AccountIdentity<AccountRole> {

        @EqualsAndHashCode.Include
        private final UUID id;

        private final AccountRole role;
    }

    enum AccountRole implements AccountRoleDefinition {

        MEMBER, MODERATOR, ADMIN, OWNER
    }
}