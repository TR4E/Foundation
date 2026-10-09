package me.trae.foundation.utilities;

import com.sun.net.httpserver.HttpServer;
import me.trae.foundation.utilities.enums.HttpMethod;
import me.trae.foundation.utilities.exceptions.HttpException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UtilHttpTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startLocalServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/ok", exchange -> respond(exchange, 200, "ok"));
        this.server.createContext("/created", exchange -> respond(exchange, 201, exchange.getRequestMethod() + ":" + new String(exchange.getRequestBody().readAllBytes())));
        this.server.createContext("/bad", exchange -> respond(exchange, 400, "bad"));
        this.server.start();
        this.baseUrl = "http://127.0.0.1:%d".formatted(this.server.getAddress().getPort());
        UtilHttp.setHttpClient(HttpClient.newHttpClient());
        UtilHttp.setDefaultRequestTimeout(Duration.ofSeconds(2));
    }

    @AfterEach
    void stopLocalServer() {
        this.server.stop(0);
        UtilHttp.setHttpClient(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build());
        UtilHttp.setDefaultRequestTimeout(Duration.ofSeconds(10));
    }

    @Test
    void suppliesRequestsWithMethodsBodiesHeadersAndStatusPredicates() {
        final HttpResponse<String> getResponse = UtilHttp.supply(HttpMethod.GET, this.baseUrl + "/ok", HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResponse.statusCode());
        assertEquals("ok", getResponse.body());
        assertTrue(UtilHttp.isSuccess(getResponse));
        assertTrue(UtilHttp.isStatus(getResponse, 200));
        assertTrue(UtilHttp.isStatusBetween(getResponse, 200, 299));
        assertTrue(UtilHttp.isStatusClass(getResponse, 2));
        assertFalse(UtilHttp.isError(getResponse));

        final HttpResponse<String> postResponse = UtilHttp.supply(
                HttpMethod.POST,
                this.baseUrl + "/created",
                "payload",
                "text/plain",
                Map.of("X-Test", "header"),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(201, postResponse.statusCode());
        assertEquals("POST:payload", postResponse.body());
    }

    @Test
    void statusHelpersClassifyErrorsAndRejectNullResponses() {
        final HttpResponse<String> response = UtilHttp.supply(HttpMethod.GET, this.baseUrl + "/bad", HttpResponse.BodyHandlers.ofString());

        assertTrue(UtilHttp.isClientError(response));
        assertTrue(UtilHttp.isError(response));
        assertFalse(UtilHttp.isServerError(response));
        assertFalse(UtilHttp.isInformational(response));
        assertFalse(UtilHttp.isRedirect(response));
        assertFalse(UtilHttp.isStatus(null, 400));
        assertFalse(UtilHttp.isStatusBetween(null, 100, 599));
        assertFalse(UtilHttp.isStatusClass(null, 4));
    }

    @Test
    void asynchronousSupplyAndDispatchCompleteWithExpectedResponse() throws Exception {
        final HttpResponse<String> response = UtilHttp.supplyAsynchronous(HttpMethod.GET, this.baseUrl + "/ok", HttpResponse.BodyHandlers.ofString()).get(2, TimeUnit.SECONDS);
        assertEquals("ok", response.body());

        final CompletableFuture<HttpResponse<String>> dispatched = new CompletableFuture<>();
        UtilHttp.dispatchAsynchronous(HttpMethod.GET, this.baseUrl + "/ok", HttpResponse.BodyHandlers.ofString(), dispatched::complete, dispatched::completeExceptionally);
        assertEquals(200, dispatched.get(2, TimeUnit.SECONDS).statusCode());

        final AtomicReference<HttpResponse<String>> synchronousDispatch = new AtomicReference<>();
        UtilHttp.dispatch(HttpMethod.GET, this.baseUrl + "/ok", HttpResponse.BodyHandlers.ofString(), synchronousDispatch::set, throwable -> {
            throw new AssertionError("Unexpected request error", throwable);
        });
        assertNotNull(synchronousDispatch.get());
    }

    @Test
    void wrapsSynchronousRequestFailuresAndReportsDispatchFailures() {
        assertThrows(HttpException.class, () -> UtilHttp.supply(HttpMethod.GET, "not a URI", HttpResponse.BodyHandlers.ofString()));

        final AtomicReference<Throwable> failure = new AtomicReference<>();
        UtilHttp.dispatch(HttpMethod.GET, "not a URI", HttpResponse.BodyHandlers.ofString(), null, failure::set);
        assertNotNull(failure.get());
    }

    private static void respond(final com.sun.net.httpserver.HttpExchange exchange, final int status, final String body) throws IOException {
        final byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (final var responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }
}