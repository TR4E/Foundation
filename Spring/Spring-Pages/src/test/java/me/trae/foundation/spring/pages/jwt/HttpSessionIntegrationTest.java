package me.trae.foundation.spring.pages.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = JwtIntegrationTest.TestConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "foundation.production=false")
final class HttpSessionIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void twoBrowsersReachProtectedPageAndLogoutEndsOnlyOneSession() throws Exception {
        final Browser firstBrowser = this.login();
        final Browser secondBrowser = this.login();
        assertEquals(200, this.send(firstBrowser, "/orders", false).statusCode());
        assertEquals(200, this.send(secondBrowser, "/orders", false).statusCode());
        assertEquals(200, this.send(firstBrowser, "/session/logout", true).statusCode());
        assertEquals(302, this.send(firstBrowser, "/orders", false).statusCode());
        assertEquals(200, this.send(secondBrowser, "/orders", false).statusCode());
    }

    @Test
    void protectedAssetIsHiddenFromAnonymousBrowserAndServedToAuthorizedBrowser() throws Exception {
        assertEquals(404, this.send(new Browser(), "/js/jwt-orders.js", false).statusCode());
        final HttpResponse<String> httpResponse = this.send(this.login(), "/js/jwt-orders.js", false);
        assertEquals(200, httpResponse.statusCode());
        assertTrue(httpResponse.body().contains("orders"));
        assertEquals("private, no-store", httpResponse.headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test
    void accountBudgetIsSharedAcrossTwoIndependentBrowserSessions() throws Exception {
        final Browser firstBrowser = this.login();
        final Browser secondBrowser = this.login();
        assertEquals(200, this.send(firstBrowser, "/session/http-limited", false).statusCode());
        assertEquals(200, this.send(secondBrowser, "/session/http-limited", false).statusCode());
        final HttpResponse<String> httpResponse = this.send(firstBrowser, "/session/http-limited", false);
        assertEquals(429, httpResponse.statusCode());
        assertEquals("no-store", httpResponse.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(Long.parseLong(httpResponse.headers().firstValue("Retry-After").orElseThrow()) >= 1L);
    }

    private Browser login() throws Exception {
        final Browser browser = new Browser();
        this.send(browser, "/session/account", false);
        assertEquals(200, this.send(browser, "/session/login", true).statusCode());
        assertTrue(browser.cookieManager.getCookieStore().getCookies().stream().anyMatch(httpCookie -> httpCookie.getName().equals("ACCESS-TOKEN") && httpCookie.isHttpOnly()));

        return browser;
    }

    private HttpResponse<String> send(final Browser browser, final String path, final boolean write) throws Exception {
        final URI uri = URI.create("http://localhost:%s%s".formatted(this.port, path));
        final HttpRequest httpRequest = write ? HttpRequest.newBuilder(uri).header("Origin", "http://localhost:%s".formatted(this.port)).header("X-XSRF-TOKEN", browser.cookieManager.getCookieStore().getCookies().stream().filter(httpCookie -> httpCookie.getName().equals("XSRF-TOKEN")).findFirst().orElseThrow().getValue()).POST(HttpRequest.BodyPublishers.noBody()).build() : HttpRequest.newBuilder(uri).GET().build();

        final HttpResponse<String> httpResponse = browser.httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        browser.cookieManager.getCookieStore().getCookies().forEach(httpCookie -> httpCookie.setVersion(0));

        return httpResponse;
    }

    static final class Browser {

        private final CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

        private final HttpClient httpClient = HttpClient.newBuilder().cookieHandler(this.cookieManager).followRedirects(HttpClient.Redirect.NEVER).build();
    }
}