package me.trae.foundation.spring.pages.registry;

import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.asset.AssetProvider;
import me.trae.foundation.spring.pages.asset.PageAsset;
import me.trae.foundation.spring.pages.cache.CacheControlData;
import me.trae.foundation.spring.pages.exception.PageRegistrationException;
import me.trae.foundation.spring.pages.role.PageRole;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PageRegistryBuilderTest {

    private static final PageRole MEMBER = () -> "MEMBER";

    @Test
    void buildsCanonicalRouteAssetCacheAndRoleIndexes() {
        final TestPage publicPage = new TestPage("//public/", null, Set.of("//css/public.css/"));
        final TestPage protectedPage = new TestPage("/account", MEMBER, Set.of("/js/account.js"));
        final AssetProvider provider = () -> List.of(new PageAsset("//shared/app.js/", MEMBER, CacheControlData.IMMUTABLE));

        final PageRegistryData data = PageRegistryBuilder.build(List.of(publicPage, protectedPage), List.of(provider));

        assertEquals(List.of(publicPage, protectedPage), data.getPageList());
        assertSame(CacheControlData.PRIVATE_REVALIDATE, data.getCacheControlDataMap().get("/public"));
        assertSame(CacheControlData.SHARED_SHORT, data.getCacheControlDataMap().get("/css/public.css"));
        assertSame(CacheControlData.PRIVATE_NO_STORE, data.getCacheControlDataMap().get("/js/account.js"));
        assertSame(CacheControlData.IMMUTABLE, data.getCacheControlDataMap().get("/shared/app.js"));
        assertNull(data.getProtectedAssetMap().get("/css/public.css"));
        assertSame(MEMBER, data.getProtectedAssetMap().get("/js/account.js"));
        assertSame(MEMBER, data.getProtectedAssetMap().get("/shared/app.js"));
    }

    @Test
    void resultIsAnImmutableSnapshot() {
        final var pages = new java.util.ArrayList<Page>();
        pages.add(new TestPage("/first", null, Set.of()));

        final PageRegistryData data = PageRegistryBuilder.build(pages, List.of());
        pages.add(new TestPage("/second", null, Set.of()));

        assertEquals(1, data.getPageList().size());
        assertThrows(UnsupportedOperationException.class, () -> data.getPageList().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.getCacheControlDataMap().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.getProtectedAssetMap().clear());
    }

    @Test
    void rejectsRoutesThatCollideAfterCanonicalisation() {
        final PageRegistrationException exception = assertThrows(PageRegistrationException.class, () -> PageRegistryBuilder.build(
                List.of(new TestPage("/orders", null, Set.of()), new TestPage("//orders/", null, Set.of())),
                List.of()
        ));

        assertTrue(exception.getMessage().contains("Duplicate page route /orders"));
    }

    @Test
    void rejectsAssetCollisionsAndNamesBothOwners() {
        final TestPage page = new TestPage("/orders", null, Set.of("/js/orders.js"));
        final AssetProvider provider = new TestAssetProvider(List.of(new PageAsset("//js/orders.js/")));

        final PageRegistrationException exception = assertThrows(PageRegistrationException.class, () -> PageRegistryBuilder.build(List.of(page), List.of(provider)));

        assertTrue(exception.getMessage().contains("Duplicate asset path /js/orders.js"));
        assertTrue(exception.getMessage().contains(TestPage.class.getName()));
        assertTrue(exception.getMessage().contains(TestAssetProvider.class.getName()));
    }

    private static final class TestPage extends Page {

        private final Set<String> assetPaths;

        private TestPage(final String route, final PageRole requiredRole, final Set<String> assetPaths) {
            super(route, requiredRole);
            this.assetPaths = assetPaths;
        }

        @Override
        public Set<String> getAssetPaths() {
            return this.assetPaths;
        }
    }

    private record TestAssetProvider(Collection<PageAsset> assets) implements AssetProvider {

        @Override
        public Collection<PageAsset> getAssets() {
            return this.assets;
        }
    }
}
