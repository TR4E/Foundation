package me.trae.foundation.spring.pages.utility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UtilRedirectTest {

    @ParameterizedTest
    @ValueSource(strings = {"/", "/orders", "/orders/42?tab=history", "/path%20with%20spaces"})
    void acceptsSameSitePaths(final String redirect) {
        assertTrue(UtilRedirect.isSafe(redirect));
        assertEquals(redirect, UtilRedirect.sanitise(redirect));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "orders", "https://evil.example", "//evil.example/path", "/\\evil.example", "/order/{id}", "/orders\nLocation: https://evil.example", "/orders\rLocation: https://evil.example"})
    void rejectsExternalMalformedAndHeaderInjectionTargets(final String redirect) {
        assertFalse(UtilRedirect.isSafe(redirect));
        assertNull(UtilRedirect.sanitise(redirect));
    }

    @Test
    void aBackslashLaterInAPathDoesNotTurnItIntoANetworkPath() {
        assertTrue(UtilRedirect.isSafe("/safe/path\\segment"));
    }
}