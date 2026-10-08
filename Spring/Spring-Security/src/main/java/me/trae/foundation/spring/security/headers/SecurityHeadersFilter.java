package me.trae.foundation.spring.security.headers;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.trae.foundation.spring.common.FoundationProperties;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public final class SecurityHeadersFilter extends OncePerRequestFilter {

    private final FoundationProperties foundationProperties;
    private final SecurityHeadersProperties securityHeadersProperties;
    private final String contentSecurityPolicy;

    public SecurityHeadersFilter(final FoundationProperties foundationProperties, final SecurityHeadersProperties securityHeadersProperties) {
        this.foundationProperties = foundationProperties;
        this.securityHeadersProperties = securityHeadersProperties;
        this.contentSecurityPolicy = securityHeadersProperties.isContentSecurityPolicyEnabled() ? ContentSecurityPolicyBuilder.build(securityHeadersProperties) : null;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(final @NonNull HttpServletRequest httpServletRequest, final @NonNull HttpServletResponse httpServletResponse, final @NonNull FilterChain filterChain) throws ServletException, IOException {
        this.setHeader(httpServletResponse, "X-Content-Type-Options", this.securityHeadersProperties.getContentTypeOptions());
        this.setHeader(httpServletResponse, "Referrer-Policy", this.securityHeadersProperties.getReferrerPolicy());
        this.setHeader(httpServletResponse, "Permissions-Policy", this.securityHeadersProperties.getPermissionsPolicy());
        this.setHeader(httpServletResponse, "X-Frame-Options", this.securityHeadersProperties.getFrameOptions());
        this.setHeader(httpServletResponse, "Content-Security-Policy", this.contentSecurityPolicy);

        if (this.foundationProperties.isProduction()) {
            this.setHeader(httpServletResponse, "Strict-Transport-Security", this.securityHeadersProperties.getStrictTransportSecurity());
        }

        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }

    private void setHeader(final HttpServletResponse httpServletResponse, final String name, final String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        httpServletResponse.setHeader(name, value);
    }
}