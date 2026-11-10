package me.trae.foundation.spring.security.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NoArgsConstructor;
import me.trae.foundation.spring.common.account.AccountIdentity;
import me.trae.foundation.spring.security.jwt.refresh.RefreshSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NoArgsConstructor
public class EmptySecurityAccountProvider implements SecurityAccountProvider<AccountIdentity<?>> {

    @Override
    public Optional<? extends AccountIdentity<?>> getAccountByRequest(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
        return Optional.empty();
    }

    @Override
    public Optional<RefreshSession> getRefreshSession(final AccountIdentity<?> accountIdentity, final UUID sessionId) {
        throw new UnsupportedOperationException("No security account provider configured");
    }

    @Override
    public List<RefreshSession> getRefreshSessionList(final AccountIdentity<?> accountIdentity) {
        throw new UnsupportedOperationException("No security account provider configured");
    }

    @Override
    public boolean updateRefreshSession(final AccountIdentity<?> accountIdentity, final RefreshSession refreshSession, final String expectedTokenHash) {
        throw new UnsupportedOperationException("No security account provider configured");
    }

    @Override
    public void deleteRefreshSession(final AccountIdentity<?> accountIdentity, final UUID sessionId) {
        throw new UnsupportedOperationException("No security account provider configured");
    }

    @Override
    public void deleteRefreshSessionList(final AccountIdentity<?> accountIdentity) {
        throw new UnsupportedOperationException("No security account provider configured");
    }
}