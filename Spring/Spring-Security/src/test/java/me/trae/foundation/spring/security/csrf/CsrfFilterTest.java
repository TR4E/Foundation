package me.trae.foundation.spring.security.csrf;

import jakarta.servlet.http.Cookie;
import me.trae.foundation.spring.common.FoundationProperties;
import me.trae.foundation.spring.security.csrf.annotation.CsrfExclude;
import me.trae.foundation.spring.security.csrf.filter.CsrfFilter;
import me.trae.foundation.spring.security.csrf.registry.CsrfRegistry;
import me.trae.foundation.spring.security.csrf.service.CsrfTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CsrfFilterTest {

    private static final String TOKEN = "a-token-value";

    @Test
    void safeRequestsIssueACookieAndPassThrough() throws Exception {
        final MockHttpServletRequest request = request("GET", "/orders");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain filterChain = new MockFilterChain();

        filter(new CsrfRegistry()).doFilter(request, response, filterChain);

        assertNotNull(filterChain.getRequest());
        assertTrue(response.getHeader("Set-Cookie").startsWith("XSRF-TOKEN="));
    }

    @Test
    void safeRequestsWithACookieAreLeftAlone() throws Exception {
        final MockHttpServletRequest request = request("GET", "/orders");
        request.setCookies(new Cookie("XSRF-TOKEN", TOKEN));

        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter(new CsrfRegistry()).doFilter(request, response, new MockFilterChain());

        assertNull(response.getHeader("Set-Cookie"));
    }

    @Test
    void headAndOptionsAreNeverProtected() throws Exception {
        for (final String method : new String[]{"HEAD", "OPTIONS"}) {
            final MockFilterChain filterChain = new MockFilterChain();

            filter(new CsrfRegistry()).doFilter(request(method, "/api/order/create"), new MockHttpServletResponse(), filterChain);

            assertNotNull(filterChain.getRequest());
        }
    }

    @Test
    void writesWithMatchingTokenAndOriginPassThrough() throws Exception {
        final MockHttpServletRequest request = write("POST", "/api/order/create", TOKEN, TOKEN, "https://example.com");
        final MockFilterChain filterChain = new MockFilterChain();

        filter(new CsrfRegistry()).doFilter(request, new MockHttpServletResponse(), filterChain);

        assertNotNull(filterChain.getRequest());
    }

    @Test
    void everyWriteMethodIsProtected() throws Exception {
        for (final String method : new String[]{"POST", "PUT", "PATCH", "DELETE"}) {
            final MockHttpServletResponse response = new MockHttpServletResponse();

            filter(new CsrfRegistry()).doFilter(write(method, "/api/order/create", null, null, "https://example.com"), response, new MockFilterChain());

            assertEquals(403, response.getStatus());
        }
    }

    @Test
    void aMissingHeaderIsRejected() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter(new CsrfRegistry()).doFilter(write("POST", "/api/order/create", TOKEN, null, "https://example.com"), response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void aMissingCookieIsRejected() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter(new CsrfRegistry()).doFilter(write("POST", "/api/order/create", null, TOKEN, "https://example.com"), response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void aMismatchedTokenIsRejected() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter(new CsrfRegistry()).doFilter(write("POST", "/api/order/create", TOKEN, "other-value", "https://example.com"), response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void aForeignOriginIsRejected() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter(new CsrfRegistry()).doFilter(write("POST", "/api/order/create", TOKEN, TOKEN, "https://attacker.example.net"), response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void aMissingOriginAndRefererIsRejected() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter(new CsrfRegistry()).doFilter(write("POST", "/api/order/create", TOKEN, TOKEN, null), response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void theRefererIsUsedWhenThereIsNoOrigin() throws Exception {
        final MockHttpServletRequest request = write("POST", "/api/order/create", TOKEN, TOKEN, null);
        request.addHeader("Referer", "https://example.com/orders");

        final MockFilterChain filterChain = new MockFilterChain();

        filter(new CsrfRegistry()).doFilter(request, new MockHttpServletResponse(), filterChain);

        assertNotNull(filterChain.getRequest());
    }

    @Test
    void theSameOriginCheckCanBeTurnedOff() throws Exception {
        final CsrfProperties csrfProperties = new CsrfProperties();
        csrfProperties.setRequireSameOrigin(false);

        final MockFilterChain filterChain = new MockFilterChain();

        filter(new CsrfRegistry(), csrfProperties).doFilter(write("POST", "/api/order/create", TOKEN, TOKEN, null), new MockHttpServletResponse(), filterChain);

        assertNotNull(filterChain.getRequest());
    }

    @Test
    void anExcludedPathSkipsEveryCheck() throws Exception {
        final MockFilterChain filterChain = new MockFilterChain();

        filter(excludedRegistry()).doFilter(write("POST", "/api/webhook/payment", null, null, null), new MockHttpServletResponse(), filterChain);

        assertNotNull(filterChain.getRequest());
    }

    @Test
    void anExcludedPathDoesNotExcludeItsNeighbours() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter(excludedRegistry()).doFilter(write("POST", "/api/webhook/payments", null, null, null), response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void anExcludedPathIsMatchedCanonically() throws Exception {
        final MockFilterChain filterChain = new MockFilterChain();

        filter(excludedRegistry()).doFilter(write("POST", "//api//webhook//payment/", null, null, null), new MockHttpServletResponse(), filterChain);

        assertNotNull(filterChain.getRequest());
    }

    private static CsrfRegistry excludedRegistry() {
        final CsrfRegistry csrfRegistry = new CsrfRegistry();
        final WebhookController controller = new WebhookController();

        csrfRegistry.register(CsrfRegistry.build(Map.of(RequestMappingInfo.paths("/api/webhook/payment").build(), new HandlerMethod(controller, method()))));

        return csrfRegistry;
    }

    private static Method method() {
        for (final Method method : WebhookController.class.getDeclaredMethods()) {
            if (!method.getName().equals("payment")) {
                continue;
            }

            return method;
        }

        throw new IllegalStateException("No method payment");
    }

    private static CsrfFilter filter(final CsrfRegistry csrfRegistry) {
        return filter(csrfRegistry, new CsrfProperties());
    }

    private static CsrfFilter filter(final CsrfRegistry csrfRegistry, final CsrfProperties csrfProperties) {
        return new CsrfFilter(csrfProperties, new CsrfTokenService(new FoundationProperties(), csrfProperties), csrfRegistry);
    }

    private static MockHttpServletRequest write(final String method, final String path, final String cookieToken, final String headerToken, final String origin) {
        final MockHttpServletRequest request = request(method, path);

        if (cookieToken != null) {
            request.setCookies(new Cookie("XSRF-TOKEN", cookieToken));
        }

        if (headerToken != null) {
            request.addHeader("X-XSRF-TOKEN", headerToken);
        }

        if (origin != null) {
            request.addHeader("Origin", origin);
        }

        return request;
    }

    private static MockHttpServletRequest request(final String method, final String path) {
        final MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServerName("example.com");

        return request;
    }

    @CsrfExclude
    static class WebhookController {

        public void payment() {
        }
    }
}