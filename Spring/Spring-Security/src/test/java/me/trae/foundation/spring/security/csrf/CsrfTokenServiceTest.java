package me.trae.foundation.spring.security.csrf;

import jakarta.servlet.http.Cookie;
import me.trae.foundation.spring.common.FoundationProperties;
import me.trae.foundation.spring.security.csrf.service.CsrfTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CsrfTokenServiceTest {

    @Test
    void cookieNameGainsTheHostPrefixInProduction() {
        assertEquals("XSRF-TOKEN", service(false).getCookieName());
        assertEquals("__Host-XSRF-TOKEN", service(true).getCookieName());
    }

    @Test
    void productionCookieIsSecureAndStrict() {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        service(true).issue(response);

        final String setCookie = response.getHeader("Set-Cookie");

        assertTrue(setCookie.startsWith("__Host-XSRF-TOKEN="));
        assertTrue(setCookie.contains("; Path=/"));
        assertTrue(setCookie.contains("; SameSite=Strict"));
        assertTrue(setCookie.contains("; Secure"));
        assertFalse(setCookie.contains("Domain="));
    }

    @Test
    void developmentCookieIsLaxAndNotSecure() {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        service(false).issue(response);

        final String setCookie = response.getHeader("Set-Cookie");

        assertTrue(setCookie.startsWith("XSRF-TOKEN="));
        assertTrue(setCookie.contains("; SameSite=Lax"));
        assertFalse(setCookie.contains("; Secure"));
    }

    @Test
    void cookieCarriesTheConfiguredLifetime() {
        final CsrfProperties csrfProperties = new CsrfProperties();
        csrfProperties.setCookieDuration(Duration.ofHours(2L));

        final MockHttpServletResponse response = new MockHttpServletResponse();

        new CsrfTokenService(new FoundationProperties(), csrfProperties).issue(response);

        assertTrue(response.getHeader("Set-Cookie").contains("; Max-Age=7200"));
    }

    @Test
    void everyIssuedTokenIsDifferent() {
        final MockHttpServletResponse first = new MockHttpServletResponse();
        final MockHttpServletResponse second = new MockHttpServletResponse();

        service(false).issue(first);
        service(false).issue(second);

        assertNotEquals(first.getHeader("Set-Cookie"), second.getHeader("Set-Cookie"));
    }

    @Test
    void readsTheTokenBackFromTheCookie() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("XSRF-TOKEN", "value"));

        assertEquals("value", service(false).getToken(request));
    }

    @Test
    void readsTheProductionCookieName() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("XSRF-TOKEN", "wrong"), new Cookie("__Host-XSRF-TOKEN", "right"));

        assertEquals("right", service(true).getToken(request));
    }

    @Test
    void missingCookieReadsAsNull() {
        assertNull(service(false).getToken(new MockHttpServletRequest()));

        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "value"));

        assertNull(service(false).getToken(request));
    }

    private static CsrfTokenService service(final boolean production) {
        final FoundationProperties foundationProperties = new FoundationProperties();
        foundationProperties.setProduction(production);

        return new CsrfTokenService(foundationProperties, new CsrfProperties());
    }
}
