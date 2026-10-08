package me.trae.foundation.spring.ratelimit.account;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public final class EmptyRateLimitAccountResolver implements RateLimitAccountResolver {

    @Override
    public Optional<String> getAccountIdByRequest(final HttpServletRequest httpServletRequest) {
        return Optional.empty();
    }
}