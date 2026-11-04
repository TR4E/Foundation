package me.trae.foundation.spring.common.cookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import me.trae.foundation.spring.common.FoundationProperties;
import me.trae.foundation.utilities.UtilBase64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.function.Function;

@AllArgsConstructor
public final class CookieHelper {

    private static final Function<String, String> SERIALIZER_FUNCTION = cookieValue -> cookieValue == null ? null : UtilBase64.encodeToString(cookieValue);
    private static final Function<String, String> DESERIALIZER_FUNCTION = encodedCookieValue -> encodedCookieValue == null ? null : UtilBase64.decodeToString(encodedCookieValue);

    private final FoundationProperties foundationProperties;

    public void setCookie(final HttpServletResponse httpServletResponse, final String cookieName, final String cookieValue, final boolean httpOnly, final Duration maxAge, final boolean serialize) {
        final ResponseCookie responseCookie = ResponseCookie.from(this.resolveCookieName(cookieName), serialize ? SERIALIZER_FUNCTION.apply(cookieValue) : cookieValue)
                .httpOnly(httpOnly)
                .secure(this.foundationProperties.isProduction())
                .path("/")
                .sameSite(this.foundationProperties.isProduction() ? "Strict" : "Lax")
                .maxAge(maxAge)
                .build();

        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
    }

    public void deleteCookie(final HttpServletResponse httpServletResponse, final String cookieName, final boolean httpOnly) {
        final ResponseCookie responseCookie = ResponseCookie.from(this.resolveCookieName(cookieName), "")
                .httpOnly(httpOnly)
                .secure(this.foundationProperties.isProduction())
                .path("/")
                .sameSite(this.foundationProperties.isProduction() ? "Strict" : "Lax")
                .maxAge(Duration.ZERO)
                .build();

        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
    }

    public String getCookie(final HttpServletRequest httpServletRequest, final String cookieName, final boolean deserialize) {
        if (cookieName == null) {
            return null;
        }

        final Cookie[] cookies = httpServletRequest.getCookies();

        if (cookies == null) {
            return null;
        }

        final String resolvedCookieName = this.resolveCookieName(cookieName);

        for (final Cookie cookie : cookies) {
            if (cookie.getName() == null) {
                continue;
            }

            if (!cookie.getName().equals(resolvedCookieName)) {
                continue;
            }

            try {
                final String value = cookie.getValue();

                return deserialize ? DESERIALIZER_FUNCTION.apply(value) : value;
            } catch (final Exception e) {
                return null;
            }
        }

        return null;
    }

    private String resolveCookieName(final String cookieName) {
        return this.foundationProperties.isProduction() ? "__Host-%s".formatted(cookieName) : cookieName;
    }
}