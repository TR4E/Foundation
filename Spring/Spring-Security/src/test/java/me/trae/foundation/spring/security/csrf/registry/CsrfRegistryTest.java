package me.trae.foundation.spring.security.csrf.registry;

import me.trae.foundation.spring.security.csrf.annotation.CsrfExclude;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.pattern.PathPattern;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CsrfRegistryTest {

    @Test
    void anEmptyRegistryExcludesNothing() {
        assertFalse(new CsrfRegistry().isExcluded("/api/webhook/payment"));
    }

    @Test
    void picksUpTheTypeLevelAnnotation() {
        final CsrfRegistry csrfRegistry = registry("/api/webhook/payment", new WebhookController(), "payment");

        assertTrue(csrfRegistry.isExcluded("/api/webhook/payment"));
        assertFalse(csrfRegistry.isExcluded("/api/order/create"));
    }

    @Test
    void picksUpTheMethodLevelAnnotation() {
        final CsrfRegistry csrfRegistry = registry("/api/order/callback", new OrderController(), "callback");

        assertTrue(csrfRegistry.isExcluded("/api/order/callback"));
    }

    @Test
    void ignoresUnannotatedHandlers() {
        final CsrfRegistry csrfRegistry = registry("/api/order/create", new OrderController(), "create");

        assertFalse(csrfRegistry.isExcluded("/api/order/create"));
    }

    @Test
    void matchesWithAndWithoutATrailingSlash() {
        final CsrfRegistry csrfRegistry = registry("/api/webhook/payment/", new WebhookController(), "payment");

        assertTrue(csrfRegistry.isExcluded("/api/webhook/payment"));
        assertTrue(csrfRegistry.isExcluded("/api/webhook/payment/"));
    }

    @Test
    void matchesPathVariables() {
        final CsrfRegistry csrfRegistry = registry("/api/webhook/{provider}", new WebhookController(), "payment");

        assertTrue(csrfRegistry.isExcluded("/api/webhook/stripe"));
        assertFalse(csrfRegistry.isExcluded("/api/webhook/stripe/extra"));
    }

    @Test
    void registeringReplacesWhatWasThereBefore() {
        final CsrfRegistry csrfRegistry = registry("/api/webhook/payment", new WebhookController(), "payment");

        csrfRegistry.register(List.<PathPattern>of());

        assertFalse(csrfRegistry.isExcluded("/api/webhook/payment"));
    }

    private static CsrfRegistry registry(final String path, final Object controller, final String name) {
        final CsrfRegistry csrfRegistry = new CsrfRegistry();

        csrfRegistry.register(CsrfRegistry.build(Map.of(RequestMappingInfo.paths(path).build(), new HandlerMethod(controller, method(controller.getClass(), name)))));

        return csrfRegistry;
    }

    private static Method method(final Class<?> type, final String name) {
        for (final Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }

            return method;
        }

        throw new IllegalStateException("No method %s on %s".formatted(name, type.getName()));
    }

    @CsrfExclude
    static class WebhookController {

        public void payment() {
        }
    }

    static class OrderController {

        @CsrfExclude
        public void callback() {
        }

        public void create() {
        }
    }
}