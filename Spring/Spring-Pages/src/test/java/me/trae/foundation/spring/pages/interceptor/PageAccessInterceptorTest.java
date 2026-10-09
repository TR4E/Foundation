package me.trae.foundation.spring.pages.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.PageProperties;
import me.trae.foundation.spring.pages.annotation.Render;
import me.trae.foundation.spring.pages.role.PageAccessResolver;
import me.trae.foundation.spring.pages.role.PageRole;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PageAccessInterceptorTest {

    private static final PageRole MEMBER = () -> "MEMBER";

    @Test
    void ignoresNonPageHandlers() throws Exception {
        final var interceptor = interceptor(false, false);

        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()));
    }

    @Test
    void allowsPublicPagesWithoutConsultingAuthentication() throws Exception {
        final RecordingResolver resolver = new RecordingResolver(false, false);
        final var interceptor = new PageAccessInterceptor(new PageProperties(), resolver);

        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler(new TestPage(null))));
        assertEquals(0, resolver.authenticationChecks);
        assertEquals(0, resolver.roleChecks);
    }

    @Test
    void redirectsUnauthenticatedRequestsToLoginWithTheFullEncodedTarget() throws Exception {
        final PageProperties properties = new PageProperties();
        properties.setLoginPath("/sign-in");
        properties.setRedirectParameter("returnTo");
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/42");
        request.setQueryString("tab=history&sort=newest");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        final boolean allowed = new PageAccessInterceptor(properties, new RecordingResolver(false, false)).preHandle(request, response, handler(new TestPage(MEMBER)));

        assertFalse(allowed);
        assertEquals(302, response.getStatus());
        assertEquals("/sign-in?returnTo=%2Forders%2F42%3Ftab%3Dhistory%26sort%3Dnewest", response.getRedirectedUrl());
    }

    @Test
    void returnsForbiddenWhenAuthenticatedViewerLacksTheRole() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        final boolean allowed = interceptor(true, false).preHandle(new MockHttpServletRequest(), response, handler(new TestPage(MEMBER)));

        assertFalse(allowed);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    }

    @Test
    void allowsAuthenticatedViewerWithTheRequiredRole() throws Exception {
        final RecordingResolver resolver = new RecordingResolver(true, true);

        assertTrue(new PageAccessInterceptor(new PageProperties(), resolver).preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler(new TestPage(MEMBER))));
        assertEquals(1, resolver.authenticationChecks);
        assertEquals(1, resolver.roleChecks);
    }

    private static PageAccessInterceptor interceptor(final boolean authenticated, final boolean hasRole) {
        return new PageAccessInterceptor(new PageProperties(), new RecordingResolver(authenticated, hasRole));
    }

    private static HandlerMethod handler(final TestPage page) throws NoSuchMethodException {
        return new HandlerMethod(page, TestPage.class.getMethod("render"));
    }

    private static final class TestPage extends Page {

        private TestPage(final PageRole requiredRole) {
            super("/orders/{id}", requiredRole);
        }

        @Render
        public String render() {
            return "orders";
        }
    }

    private static final class RecordingResolver implements PageAccessResolver {

        private final boolean authenticated;
        private final boolean hasRole;
        private int authenticationChecks;
        private int roleChecks;

        private RecordingResolver(final boolean authenticated, final boolean hasRole) {
            this.authenticated = authenticated;
            this.hasRole = hasRole;
        }

        @Override
        public boolean isAuthenticated(final HttpServletRequest request, final HttpServletResponse response) {
            this.authenticationChecks++;
            return this.authenticated;
        }

        @Override
        public boolean hasRole(final HttpServletRequest request, final HttpServletResponse response, final PageRole pageRole) {
            this.roleChecks++;
            return this.hasRole;
        }
    }
}