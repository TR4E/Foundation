package me.trae.foundation.spring.pages.role;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import me.trae.foundation.spring.common.account.AccountIdentity;
import me.trae.foundation.spring.common.account.AccountRoleDefinition;
import me.trae.foundation.spring.security.account.SecurityAccountProvider;

import java.util.Optional;

@AllArgsConstructor
public final class AccountPageAccessResolver implements PageAccessResolver {

    private static final Object ANONYMOUS_ACCOUNT = new Object();

    private static final String ACCOUNT_ATTRIBUTE = "foundation.pages.resolved-account";

    private final SecurityAccountProvider<?> accountProvider;

    @Override
    public boolean isAuthenticated(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
        return this.resolveAccount(httpServletRequest, httpServletResponse).isPresent();
    }

    @Override
    public boolean hasRole(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse, final AccountRoleDefinition accountRoleDefinition) {
        return this.resolveAccount(httpServletRequest, httpServletResponse)
                .map(accountIdentity -> accountIdentity.hasRole(accountRoleDefinition))
                .orElse(false);
    }

    private Optional<? extends AccountIdentity<?>> resolveAccount(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
        final Object accountAttribute = httpServletRequest.getAttribute(ACCOUNT_ATTRIBUTE);
        if (accountAttribute instanceof final AccountIdentity<?> accountIdentity) {
            return Optional.of(accountIdentity);
        }

        if (accountAttribute == ANONYMOUS_ACCOUNT) {
            return Optional.empty();
        }

        final Optional<? extends AccountIdentity<?>> accountOptional = this.accountProvider.getAccountByRequest(httpServletRequest, httpServletResponse);

        accountOptional.ifPresentOrElse(accountIdentity -> httpServletRequest.setAttribute(ACCOUNT_ATTRIBUTE, accountIdentity), () -> httpServletRequest.setAttribute(ACCOUNT_ATTRIBUTE, ANONYMOUS_ACCOUNT));

        return accountOptional;
    }
}