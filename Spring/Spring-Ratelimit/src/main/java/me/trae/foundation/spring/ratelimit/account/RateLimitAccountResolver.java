package me.trae.foundation.spring.ratelimit.account;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public interface RateLimitAccountResolver {

    Optional<String> getAccountIdByRequest(final HttpServletRequest httpServletRequest);
}