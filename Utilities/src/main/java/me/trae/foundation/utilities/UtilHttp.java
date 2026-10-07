package me.trae.foundation.utilities;

import lombok.Setter;
import lombok.experimental.UtilityClass;
import me.trae.foundation.utilities.enums.HttpMethod;
import me.trae.foundation.utilities.exceptions.HttpException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

@UtilityClass
public class UtilHttp {

    @Setter
    private static Duration defaultRequestTimeout = Duration.ofSeconds(10);

    @Setter
    private static HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public static boolean isInformational(final HttpResponse<?> httpResponse) {
        return isStatusClass(httpResponse, 1);
    }

    public static boolean isSuccess(final HttpResponse<?> httpResponse) {
        return isStatusClass(httpResponse, 2);
    }

    public static boolean isRedirect(final HttpResponse<?> httpResponse) {
        return isStatusClass(httpResponse, 3);
    }

    public static boolean isClientError(final HttpResponse<?> httpResponse) {
        return isStatusClass(httpResponse, 4);
    }

    public static boolean isServerError(final HttpResponse<?> httpResponse) {
        return isStatusClass(httpResponse, 5);
    }

    public static boolean isError(final HttpResponse<?> httpResponse) {
        return isClientError(httpResponse) || isServerError(httpResponse);
    }

    public static boolean isStatus(final HttpResponse<?> httpResponse, final int statusCode) {
        return httpResponse != null && httpResponse.statusCode() == statusCode;
    }

    public static boolean isStatusBetween(final HttpResponse<?> httpResponse, final int minimum, final int maximum) {
        return httpResponse != null && httpResponse.statusCode() >= minimum && httpResponse.statusCode() <= maximum;
    }

    public static boolean isStatusClass(final HttpResponse<?> httpResponse, final int statusClass) {
        return isStatusBetween(httpResponse, statusClass * 100, (statusClass * 100) + 99);
    }

    public static <T> void dispatch(final HttpMethod httpMethod, final String url, final String body, final String contentType, final Map<String, String> headers, final HttpResponse.BodyHandler<T> bodyHandler, final Consumer<HttpResponse<T>> successConsumer, final Consumer<Throwable> errorConsumer) {
        try {
            final HttpResponse<T> httpResponse = httpClient.send(buildHttpRequest(httpMethod, url, body, contentType, headers), bodyHandler);

            if (successConsumer != null) {
                successConsumer.accept(httpResponse);
            }
        } catch (final Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            if (errorConsumer != null) {
                errorConsumer.accept(exception);
            }
        }
    }

    public static <T> void dispatch(final HttpMethod httpMethod, final String url, final HttpResponse.BodyHandler<T> bodyHandler, final Consumer<HttpResponse<T>> successConsumer, final Consumer<Throwable> errorConsumer) {
        dispatch(httpMethod, url, null, null, null, bodyHandler, successConsumer, errorConsumer);
    }

    public static <T> void dispatchAsynchronous(final HttpMethod httpMethod, final String url, final String body, final String contentType, final Map<String, String> headers, final HttpResponse.BodyHandler<T> bodyHandler, final Consumer<HttpResponse<T>> successConsumer, final Consumer<Throwable> errorConsumer) {
        supplyAsynchronous(httpMethod, url, body, contentType, headers, bodyHandler)
                .thenAccept(httpResponse -> {
                    if (successConsumer != null) {
                        successConsumer.accept(httpResponse);
                    }
                })
                .exceptionally(throwable -> {
                    if (errorConsumer != null) {
                        errorConsumer.accept(throwable instanceof CompletionException && throwable.getCause() != null ? throwable.getCause() : throwable);
                    }
                    return null;
                });
    }

    public static <T> void dispatchAsynchronous(final HttpMethod httpMethod, final String url, final HttpResponse.BodyHandler<T> bodyHandler, final Consumer<HttpResponse<T>> successConsumer, final Consumer<Throwable> errorConsumer) {
        dispatchAsynchronous(httpMethod, url, null, null, null, bodyHandler, successConsumer, errorConsumer);
    }

    public static <T> HttpResponse<T> supply(final HttpMethod httpMethod, final String url, final String body, final String contentType, final Map<String, String> headers, final HttpResponse.BodyHandler<T> bodyHandler) {
        try {
            return httpClient.send(buildHttpRequest(httpMethod, url, body, contentType, headers), bodyHandler);
        } catch (final Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            throw new HttpException("Request failed: %s %s".formatted(httpMethod.name(), url), exception);
        }
    }

    public static <T> HttpResponse<T> supply(final HttpMethod httpMethod, final String url, final HttpResponse.BodyHandler<T> bodyHandler) {
        return supply(httpMethod, url, null, null, null, bodyHandler);
    }

    public static <T> CompletableFuture<HttpResponse<T>> supplyAsynchronous(final HttpMethod httpMethod, final String url, final String body, final String contentType, final Map<String, String> headers, final HttpResponse.BodyHandler<T> bodyHandler) {
        try {
            return httpClient.sendAsync(buildHttpRequest(httpMethod, url, body, contentType, headers), bodyHandler);
        } catch (final Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public static <T> CompletableFuture<HttpResponse<T>> supplyAsynchronous(final HttpMethod httpMethod, final String url, final HttpResponse.BodyHandler<T> bodyHandler) {
        return supplyAsynchronous(httpMethod, url, null, null, null, bodyHandler);
    }

    private static HttpRequest buildHttpRequest(final HttpMethod httpMethod, final String url, final String body, final String contentType, final Map<String, String> headers) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).timeout(defaultRequestTimeout);

        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }

        if (headers != null) {
            headers.forEach(builder::header);
        }

        final HttpRequest.BodyPublisher bodyPublisher = body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody();

        switch (httpMethod) {
            case GET -> builder.GET();
            case POST -> builder.POST(bodyPublisher);
            case PUT -> builder.PUT(bodyPublisher);
            case DELETE -> builder.method("DELETE", bodyPublisher);
            case PATCH -> builder.method("PATCH", bodyPublisher);
            case HEAD -> builder.HEAD();
            case OPTIONS -> builder.method("OPTIONS", bodyPublisher);
        }

        return builder.build();
    }
}