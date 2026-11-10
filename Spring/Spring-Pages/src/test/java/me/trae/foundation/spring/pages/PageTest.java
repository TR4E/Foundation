package me.trae.foundation.spring.pages;

import me.trae.foundation.spring.common.account.AccountRoleDefinition;
import me.trae.foundation.spring.pages.cache.CacheControlData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PageTest {

    private static final AccountRoleDefinition MEMBER = TestRole.MEMBER;

    @Test
    void publicPageUsesSharedAssetCaching() {
        final Page page = page("/about", null);

        assertTrue(page.isPublic());
        assertSame(CacheControlData.PRIVATE_REVALIDATE, page.getRouteCacheControlData());
        assertSame(CacheControlData.SHARED_SHORT, page.getAssetCacheControlData());
    }

    @Test
    void protectedPageUsesPrivateAssetCaching() {
        final Page page = page("/account", MEMBER);

        assertFalse(page.isPublic());
        assertSame(CacheControlData.PRIVATE_NO_STORE, page.getAssetCacheControlData());
    }

    @Test
    void baseRouteStopsAtTheFirstPathVariableAndTrimsItsSeparator() {
        assertEquals("/orders", page("/orders/{id}/history", null).getBaseRoute());
        assertEquals("/orders", page("/orders", null).getBaseRoute());
        assertEquals("/", page("/{section}", null).getBaseRoute());
    }

    private static Page page(final String route, final AccountRoleDefinition role) {
        return new Page(route, role) {};
    }

    enum TestRole implements AccountRoleDefinition {

        MEMBER
    }
}