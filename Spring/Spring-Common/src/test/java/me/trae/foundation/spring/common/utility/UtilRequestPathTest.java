package me.trae.foundation.spring.common.utility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UtilRequestPathTest {

    @Test
    void canonicaliseHandlesNullAndBlank() {
        assertEquals("/", UtilRequestPath.canonicalise(null));
        assertEquals("/", UtilRequestPath.canonicalise(""));
        assertEquals("/", UtilRequestPath.canonicalise("   "));
    }

    @Test
    void canonicaliseKeepsRoot() {
        assertEquals("/", UtilRequestPath.canonicalise("/"));
        assertEquals("/", UtilRequestPath.canonicalise("//"));
        assertEquals("/", UtilRequestPath.canonicalise("/////"));
    }

    @Test
    void canonicaliseCollapsesSlashRuns() {
        assertEquals("/orders/create", UtilRequestPath.canonicalise("//orders///create"));
    }

    @Test
    void canonicaliseStripsTrailingSlash() {
        assertEquals("/orders", UtilRequestPath.canonicalise("/orders/"));
        assertEquals("/orders", UtilRequestPath.canonicalise("/orders//"));
        assertEquals("/orders", UtilRequestPath.canonicalise("/orders/////"));
    }

    @Test
    void canonicaliseAddsLeadingSlash() {
        assertEquals("/orders", UtilRequestPath.canonicalise("orders"));
    }

    @Test
    void canonicalisePreservesCase() {
        assertEquals("/Orders/Create", UtilRequestPath.canonicalise("/Orders/Create"));
    }

    @Test
    void matchesPrefixIsSegmentAware() {
        assertTrue(UtilRequestPath.matchesPrefix("/api", "/api"));
        assertTrue(UtilRequestPath.matchesPrefix("/api/invoice", "/api"));
        assertFalse(UtilRequestPath.matchesPrefix("/apifoo", "/api"));
        assertFalse(UtilRequestPath.matchesPrefix("/ap", "/api"));
    }
}
