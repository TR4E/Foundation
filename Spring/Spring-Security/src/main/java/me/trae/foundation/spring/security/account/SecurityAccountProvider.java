package me.trae.foundation.spring.security.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.trae.foundation.spring.common.account.AccountIdentity;
import me.trae.foundation.spring.security.jwt.refresh.RefreshSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SecurityAccountProvider<Account extends AccountIdentity<?>> {

    Optional<? extends Account> getAccountByRequest(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse);

    Optional<RefreshSession> getRefreshSession(final Account account, final UUID sessionId);

    List<RefreshSession> getRefreshSessionList(final Account account);

    boolean updateRefreshSession(final Account account, final RefreshSession refreshSession, final String expectedTokenHash);

    void deleteRefreshSession(final Account account, final UUID sessionId);

    void deleteRefreshSessionList(final Account account);
}