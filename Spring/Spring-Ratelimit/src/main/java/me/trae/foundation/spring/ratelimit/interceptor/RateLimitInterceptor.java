package me.trae.foundation.spring.ratelimit.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import me.trae.foundation.spring.common.address.IpAddressResolver;
import me.trae.foundation.spring.ratelimit.RateLimitData;
import me.trae.foundation.spring.ratelimit.account.RateLimitAccountResolver;
import me.trae.foundation.spring.ratelimit.registry.RateLimitRegistry;
import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;
import me.trae.foundation.spring.ratelimit.store.RateLimitStore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@AllArgsConstructor
public final class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitRegistry registry;
    private final RateLimitStore store;
    private final IpAddressResolver ipAddressResolver;
    private final RateLimitAccountResolver accountResolver;

    @Override
    public boolean preHandle(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse, final Object handler) throws IOException {
        if (!(handler instanceof final HandlerMethod handlerMethod)) {
            return true;
        }

        final Optional<RateLimitData> rateLimitDataOptional = this.registry.getDataByHandlerMethod(handlerMethod);

        if (rateLimitDataOptional.isEmpty()) {
            return true;
        }

        final RateLimitData rateLimitData = rateLimitDataOptional.get();

        final Optional<String> identifierOptional = this.getIdentifier(httpServletRequest, rateLimitData);

        if (identifierOptional.isEmpty()) {
            this.reject(httpServletResponse, 0L);
            return false;
        }

        final long remainingMillis = this.store.tryConsume(rateLimitData.getKey() + ":" + identifierOptional.get(), rateLimitData);

        if (remainingMillis <= 0L) {
            return true;
        }

        this.reject(httpServletResponse, remainingMillis);

        return false;
    }

    private Optional<String> getIdentifier(final HttpServletRequest httpServletRequest, final RateLimitData rateLimitData) {
        if (rateLimitData.getScope() == RateLimitScope.ACCOUNT) {
            final Optional<String> accountOptional = this.accountResolver.getAccountIdByRequest(httpServletRequest);

            if (accountOptional.isPresent()) {
                return accountOptional.map(accountId -> "account:" + accountId);
            }
        }

        return this.ipAddressResolver.getIpAddressByRequest(httpServletRequest).map(address -> "ip:" + address);
    }

    private void reject(final HttpServletResponse httpServletResponse, final long remainingMillis) throws IOException {
        final long seconds = Math.max(1L, (remainingMillis + 999L) / 1000L);

        httpServletResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        httpServletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        httpServletResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        httpServletResponse.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
        httpServletResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        httpServletResponse.getWriter().write("{\"message\":\"Too many requests.\",\"retryAfter\":" + seconds + "}");
    }
}