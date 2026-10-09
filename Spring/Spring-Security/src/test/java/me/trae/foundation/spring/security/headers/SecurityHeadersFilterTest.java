package me.trae.foundation.spring.security.headers;

import me.trae.foundation.spring.common.FoundationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class SecurityHeadersFilterTest {

    @Test
    void writesTheFixedHeaders() throws Exception {
        final MockHttpServletResponse response = filter(false, new SecurityHeadersProperties());

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"));
        assertEquals("camera=(), microphone=(), geolocation=()", response.getHeader("Permissions-Policy"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertNotNull(response.getHeader("Content-Security-Policy"));
    }

    @Test
    void hstsIsProductionOnly() throws Exception {
        assertNull(filter(false, new SecurityHeadersProperties()).getHeader("Strict-Transport-Security"));
        assertEquals("max-age=31536000; includeSubDomains; preload", filter(true, new SecurityHeadersProperties()).getHeader("Strict-Transport-Security"));
    }

    @Test
    void blankValuesDropTheirHeader() throws Exception {
        final SecurityHeadersProperties properties = new SecurityHeadersProperties();
        properties.setFrameOptions("");
        properties.setReferrerPolicy("   ");

        final MockHttpServletResponse response = filter(false, properties);

        assertNull(response.getHeader("X-Frame-Options"));
        assertNull(response.getHeader("Referrer-Policy"));
    }

    @Test
    void disablingTheContentSecurityPolicyDropsTheHeader() throws Exception {
        final SecurityHeadersProperties properties = new SecurityHeadersProperties();
        properties.setContentSecurityPolicyEnabled(false);

        assertNull(filter(false, properties).getHeader("Content-Security-Policy"));
    }

    @Test
    void theChainStillRuns() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain filterChain = new MockFilterChain();

        new SecurityHeadersFilter(new FoundationProperties(), new SecurityHeadersProperties()).doFilter(request, response, filterChain);

        assertNotNull(filterChain.getRequest());
    }

    private static MockHttpServletResponse filter(final boolean production, final SecurityHeadersProperties securityHeadersProperties) throws Exception {
        final FoundationProperties foundationProperties = new FoundationProperties();
        foundationProperties.setProduction(production);

        final MockHttpServletResponse response = new MockHttpServletResponse();

        new SecurityHeadersFilter(foundationProperties, securityHeadersProperties).doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        return response;
    }
}