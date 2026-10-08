package me.trae.foundation.spring.security.csrf.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import me.trae.foundation.spring.common.utility.UtilRequestPath;
import me.trae.foundation.spring.security.csrf.CsrfProperties;
import me.trae.foundation.spring.security.csrf.registry.CsrfRegistry;
import me.trae.foundation.spring.security.csrf.service.CsrfTokenService;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@AllArgsConstructor
public final class CsrfFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_METHOD_SET = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final CsrfProperties properties;
    private final CsrfTokenService tokenService;
    private final CsrfRegistry registry;

    @Override
    protected void doFilterInternal(final @NonNull HttpServletRequest httpServletRequest, final @NonNull HttpServletResponse httpServletResponse, final @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!PROTECTED_METHOD_SET.contains(httpServletRequest.getMethod())) {
            if (this.tokenService.getToken(httpServletRequest) == null) {
                this.tokenService.issue(httpServletResponse);
            }

            filterChain.doFilter(httpServletRequest, httpServletResponse);
            return;
        }

        if (this.registry.isExcluded(UtilRequestPath.getCanonicalPath(httpServletRequest))) {
            filterChain.doFilter(httpServletRequest, httpServletResponse);
            return;
        }

        if (this.properties.isRequireSameOrigin() && !this.isSameOrigin(httpServletRequest)) {
            httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        if (!this.isTokenValid(httpServletRequest)) {
            httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }

    private boolean isTokenValid(final HttpServletRequest httpServletRequest) {
        final String cookieToken = this.tokenService.getToken(httpServletRequest);
        final String headerToken = httpServletRequest.getHeader(this.properties.getHeaderName());

        if (cookieToken == null || cookieToken.isBlank() || headerToken == null || headerToken.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(cookieToken.getBytes(StandardCharsets.UTF_8), headerToken.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isSameOrigin(final HttpServletRequest httpServletRequest) {
        String source = httpServletRequest.getHeader(HttpHeaders.ORIGIN);

        if (source == null || source.isBlank()) {
            source = httpServletRequest.getHeader(HttpHeaders.REFERER);
        }

        if (source == null || source.isBlank()) {
            return false;
        }

        try {
            return httpServletRequest.getServerName().equalsIgnoreCase(URI.create(source).getHost());
        } catch (final IllegalArgumentException exception) {
            return false;
        }
    }
}