package me.trae.foundation.spring.security.csrf.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import me.trae.foundation.spring.common.FoundationProperties;
import me.trae.foundation.spring.security.csrf.CsrfProperties;

import java.security.SecureRandom;
import java.util.Base64;

@AllArgsConstructor
public final class CsrfTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final String HOST_PREFIX = "__Host-";

    private final FoundationProperties foundationProperties;
    private final CsrfProperties csrfProperties;

    public String getCookieName() {
        return this.foundationProperties.isProduction() ? HOST_PREFIX + this.csrfProperties.getCookieName() : this.csrfProperties.getCookieName();
    }

    public String getToken(final HttpServletRequest httpServletRequest) {
        final Cookie[] cookies = httpServletRequest.getCookies();

        if (cookies == null) {
            return null;
        }

        final String cookieName = this.getCookieName();

        for (final Cookie cookie : cookies) {
            if (!cookieName.equals(cookie.getName())) {
                continue;
            }

            return cookie.getValue();
        }

        return null;
    }

    public void issue(final HttpServletResponse httpServletResponse) {
        final byte[] bytes = new byte[TOKEN_BYTE_LENGTH];

        SECURE_RANDOM.nextBytes(bytes);

        final boolean production = this.foundationProperties.isProduction();

        final StringBuilder builder = new StringBuilder();

        builder.append(this.getCookieName()).append('=').append(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        builder.append("; Path=/");
        builder.append("; Max-Age=").append(this.csrfProperties.getCookieDuration().toSeconds());
        builder.append("; SameSite=").append(production ? "Strict" : "Lax");

        if (production) {
            builder.append("; Secure");
        }

        httpServletResponse.addHeader("Set-Cookie", builder.toString());
    }
}