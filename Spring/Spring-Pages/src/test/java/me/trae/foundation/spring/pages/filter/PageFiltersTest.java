package me.trae.foundation.spring.pages.filter;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.PageProperties;
import me.trae.foundation.spring.pages.cache.CacheControlData;
import me.trae.foundation.spring.pages.registry.PageRegistry;
import me.trae.foundation.spring.pages.registry.PageRegistryBuilder;
import me.trae.foundation.spring.pages.role.PageAccessResolver;
import me.trae.foundation.spring.pages.role.PageRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class PageFiltersTest {

    private static final PageRole MEMBER = () -> "MEMBER";

    @Test
    void trailingSlashRedirectCanonicalisesThePathAndPreservesTheQuery() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "//orders///");
        request.setQueryString("page=2");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        new PageTrailingSlashFilter(new PageProperties()).doFilter(request, response, chain);

        assertEquals(308, response.getStatus());
        assertEquals("/orders?page=2", response.getHeader(HttpHeaders.LOCATION));
        assertNull(chain.getRequest());
    }

    @Test
    void trailingSlashFilterPassesCanonicalPathsAndCanBeDisabled() throws Exception {
        final PageProperties enabled = new PageProperties();
        final MockFilterChain canonicalChain = new MockFilterChain();
        new PageTrailingSlashFilter(enabled).doFilter(new MockHttpServletRequest("GET", "/orders"), new MockHttpServletResponse(), canonicalChain);
        assertNotNull(canonicalChain.getRequest());

        final PageProperties disabled = new PageProperties();
        disabled.setTrailingSlashRedirect(false);
        final MockFilterChain disabledChain = new MockFilterChain();
        new PageTrailingSlashFilter(disabled).doFilter(new MockHttpServletRequest("GET", "/orders/"), new MockHttpServletResponse(), disabledChain);
        assertNotNull(disabledChain.getRequest());
    }

    @Test
    void robotsTagIsAppliedExceptToExcludedPathPrefixes() throws Exception {
        final PageProperties properties = new PageProperties();
        properties.setRobotsTag("noindex, nofollow");
        properties.setRobotsTagExcludedPathList(List.of("/public"));

        assertEquals("noindex, nofollow", robotsHeader(properties, "/private"));
        assertNull(robotsHeader(properties, "/public"));
        assertNull(robotsHeader(properties, "/public/article"));
        assertEquals("noindex, nofollow", robotsHeader(properties, "/publicity"));
    }

    @Test
    void blankRobotsTagIsDisabledButTheChainStillRuns() throws Exception {
        final PageProperties properties = new PageProperties();
        properties.setRobotsTag("  ");
        final MockFilterChain chain = new MockFilterChain();
        final MockHttpServletResponse response = new MockHttpServletResponse();

        new RobotsTagFilter(properties).doFilter(new MockHttpServletRequest("GET", "/private"), response, chain);

        assertNull(response.getHeader("X-Robots-Tag"));
        assertNotNull(chain.getRequest());
    }

    @Test
    void cacheControlUsesSystemRegisteredAndFallbackPolicies() throws Exception {
        final PageProperties properties = new PageProperties();
        properties.setSystemPathList(List.of("/api"));
        final PageRegistry registry = registry();

        assertEquals(CacheControlData.NO_STORE.getValue(), cacheHeader(properties, registry, "/api/orders"));
        assertEquals(CacheControlData.PRIVATE_REVALIDATE.getValue(), cacheHeader(properties, registry, "/orders"));
        assertEquals(CacheControlData.PRIVATE_NO_STORE.getValue(), cacheHeader(properties, registry, "/js/orders.js"));
        assertEquals(CacheControlData.PRIVATE_REVALIDATE.getValue(), cacheHeader(properties, registry, "/unknown"));
    }

    @Test
    void cacheControlUsesOriginalErrorRequestPath() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "//api/orders/");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        new PageCacheControlFilter(new PageProperties(), registry()).doFilter(request, response, new MockFilterChain());

        assertEquals(CacheControlData.NO_STORE.getValue(), response.getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void protectedAssetsAreHiddenAndForcedToPrivateNoStore() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        new StaticResourceFilter(registry(), new FixedResolver(false)).doFilter(new MockHttpServletRequest("GET", "/js/orders.js"), response, chain);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        assertEquals(CacheControlData.PRIVATE_NO_STORE.getValue(), response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertNull(chain.getRequest());
    }

    @Test
    void authorisedProtectedAssetsContinueThroughTheChain() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        new StaticResourceFilter(registry(), new FixedResolver(true)).doFilter(new MockHttpServletRequest("GET", "//js/orders.js/"), response, chain);

        assertEquals(CacheControlData.PRIVATE_NO_STORE.getValue(), response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertNotNull(chain.getRequest());
    }

    @Test
    void unprotectedResourcesContinueWithoutChangingCacheControl() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final MockFilterChain chain = new MockFilterChain();

        new StaticResourceFilter(registry(), new FixedResolver(false)).doFilter(new MockHttpServletRequest("GET", "/css/site.css"), response, chain);

        assertNull(response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertNotNull(chain.getRequest());
    }

    private static String robotsHeader(final PageProperties properties, final String path) throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();
        new RobotsTagFilter(properties).doFilter(new MockHttpServletRequest("GET", path), response, new MockFilterChain());
        return response.getHeader("X-Robots-Tag");
    }

    private static String cacheHeader(final PageProperties properties, final PageRegistry registry, final String path) throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();
        new PageCacheControlFilter(properties, registry).doFilter(new MockHttpServletRequest("GET", path), response, new MockFilterChain());
        return response.getHeader(HttpHeaders.CACHE_CONTROL);
    }

    private static PageRegistry registry() {
        final PageRegistry registry = new PageRegistry();
        registry.register(PageRegistryBuilder.build(List.of(new AssetPage()), List.of()));
        return registry;
    }

    private static final class AssetPage extends Page {

        private AssetPage() {
            super("/orders", MEMBER);
        }

        @Override
        public Set<String> getAssetPaths() {
            return Set.of("/js/orders.js");
        }
    }

    private record FixedResolver(boolean allowed) implements PageAccessResolver {

        @Override
        public boolean isAuthenticated(final HttpServletRequest request, final HttpServletResponse response) {
            return this.allowed;
        }

        @Override
        public boolean hasRole(final HttpServletRequest request, final HttpServletResponse response, final PageRole pageRole) {
            return this.allowed;
        }
    }
}
