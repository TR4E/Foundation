package me.trae.foundation.spring.pages.registry;

import lombok.AllArgsConstructor;
import me.trae.foundation.spring.common.account.AccountRoleDefinition;
import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.asset.AssetProvider;
import me.trae.foundation.spring.pages.asset.PageAsset;
import me.trae.foundation.spring.pages.cache.CacheControlData;
import me.trae.foundation.spring.pages.exception.PageRegistrationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PageRegistryBuilderTest {

    private static final AccountRoleDefinition MEMBER = TestRole.MEMBER;

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
        final List<Page> pages = new ArrayList<>();
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

    @Test
    void wildcardAssetsProtectThePrefixItselfAndDescendantsButNotNeighbours() {
        final PageRegistryData pageRegistryData = PageRegistryBuilder.build(Collections.singletonList(new TestPage("/orders", MEMBER, Collections.singleton("/assets/admin/**"))), Collections.emptyList());

        assertSame(MEMBER, pageRegistryData.getAssetRole("/assets/admin"));
        assertSame(MEMBER, pageRegistryData.getAssetRole("/assets/admin/x.js"));
        assertSame(MEMBER, pageRegistryData.getAssetRole("/assets/admin/deep/y.css"));
        assertNull(pageRegistryData.getAssetRole("/assets/administrator.js"));
        assertSame(MEMBER, pageRegistryData.getProtectedAssetPrefixMap().get("/assets/admin"));
        assertSame(CacheControlData.PRIVATE_NO_STORE, pageRegistryData.getCacheControlDataPrefixMap().get("/assets/admin"));
    }

    @Test
    void longestPrefixAndExactEntriesResolveRolesAndCachePoliciesTogether() {
        final AssetProvider assetProvider = () -> List.of(new PageAsset("/assets/**", MEMBER, CacheControlData.PRIVATE_REVALIDATE), new PageAsset("/assets/admin/**", TestRole.ADMIN, CacheControlData.PRIVATE_NO_STORE), new PageAsset("/assets/admin/help.js", MEMBER, CacheControlData.NO_STORE));
        final PageRegistryData pageRegistryData = PageRegistryBuilder.build(Collections.emptyList(), Collections.singletonList(assetProvider));

        assertSame(TestRole.ADMIN, pageRegistryData.getAssetRole("/assets/admin/deep/x.js"));
        assertSame(CacheControlData.PRIVATE_NO_STORE, pageRegistryData.getCacheControlData("/assets/admin/deep/x.js"));
        assertSame(MEMBER, pageRegistryData.getAssetRole("/assets/member.js"));
        assertSame(CacheControlData.PRIVATE_REVALIDATE, pageRegistryData.getCacheControlData("/assets/member.js"));
        assertSame(MEMBER, pageRegistryData.getAssetRole("/assets/admin/help.js"));
        assertSame(CacheControlData.NO_STORE, pageRegistryData.getCacheControlData("/assets/admin/help.js"));
    }

    @Test
    void exactAndWildcardDeclarationsOfTheSameBasePathCanCoexist() {
        final AssetProvider assetProvider = () -> List.of(new PageAsset("/assets/admin/**", TestRole.ADMIN, CacheControlData.PRIVATE_NO_STORE), new PageAsset("/assets/admin", MEMBER, CacheControlData.PRIVATE_REVALIDATE));
        final PageRegistryData pageRegistryData = PageRegistryBuilder.build(Collections.emptyList(), Collections.singletonList(assetProvider));

        assertSame(MEMBER, pageRegistryData.getAssetRole("/assets/admin"));
        assertSame(CacheControlData.PRIVATE_REVALIDATE, pageRegistryData.getCacheControlData("/assets/admin"));
        assertSame(TestRole.ADMIN, pageRegistryData.getAssetRole("/assets/admin/x.js"));
        assertSame(CacheControlData.PRIVATE_NO_STORE, pageRegistryData.getCacheControlData("/assets/admin/x.js"));
    }

    @Test
    void exactAndWildcardAssetGatesIgnoreCaseButPageRoutesDoNot() {
        final Page firstPage = new TestPage("/orders", MEMBER, Collections.singleton("/js/account.js"));
        final Page secondPage = new TestPage("/Orders", TestRole.ADMIN, Collections.singleton("/assets/admin/**"));
        final PageRegistryData pageRegistryData = PageRegistryBuilder.build(List.of(firstPage, secondPage), Collections.emptyList());

        assertSame(MEMBER, pageRegistryData.getAssetRole("/js/Account.JS"));
        assertSame(TestRole.ADMIN, pageRegistryData.getAssetRole("/ASSETS/Admin/x.js"));
        assertEquals(2, pageRegistryData.getPageList().size());
        assertTrue(pageRegistryData.getCacheControlDataMap().containsKey("/orders"));
        assertTrue(pageRegistryData.getCacheControlDataMap().containsKey("/Orders"));
        assertNull(pageRegistryData.getCacheControlData("/ORDERS"));
    }

    @Test
    void duplicateExactAndWildcardDeclarationsAcrossPagesAreRejected() {
        for (final String asset : new String[]{"/js/account.js", "/assets/admin/**"}) {
            assertThrows(PageRegistrationException.class, () -> PageRegistryBuilder.build(List.of(new TestPage("/first", MEMBER, Collections.singleton(asset)), new TestPage("/second", TestRole.ADMIN, Collections.singleton(asset))), Collections.emptyList()));
        }
    }

    @Test
    void caseAliasesCannotSilentlyOverwriteAnAssetRole() {
        assertThrows(PageRegistrationException.class, () -> PageRegistryBuilder.build(List.of(new TestPage("/first", MEMBER, Collections.singleton("/js/account.js")), new TestPage("/second", TestRole.ADMIN, Collections.singleton("/JS/Account.JS"))), Collections.emptyList()));
        assertThrows(PageRegistrationException.class, () -> PageRegistryBuilder.build(List.of(new TestPage("/first", MEMBER, Collections.singleton("/assets/admin/**")), new TestPage("/second", TestRole.ADMIN, Collections.singleton("/ASSETS/Admin/**"))), Collections.emptyList()));
    }

    @Test
    void allExactAndPrefixIndexesAreImmutable() {
        final PageRegistryData pageRegistryData = PageRegistryBuilder.build(Collections.singletonList(new TestPage("/orders", MEMBER, Set.of("/assets/**", "/js/account.js"))), Collections.emptyList());

        assertThrows(UnsupportedOperationException.class, () -> pageRegistryData.getProtectedAssetMap().clear());
        assertThrows(UnsupportedOperationException.class, () -> pageRegistryData.getProtectedAssetPrefixMap().clear());
        assertThrows(UnsupportedOperationException.class, () -> pageRegistryData.getCacheControlDataMap().clear());
        assertThrows(UnsupportedOperationException.class, () -> pageRegistryData.getCacheControlDataPrefixMap().clear());
    }

    @Test
    void aRootWildcardCoversAssetsAtEveryDepth() {
        final PageRegistryData pageRegistryData = PageRegistryBuilder.build(Collections.singletonList(new TestPage("/orders", MEMBER, Collections.singleton("/**"))), Collections.emptyList());

        assertSame(MEMBER, pageRegistryData.getAssetRole("/"));
        assertSame(MEMBER, pageRegistryData.getAssetRole("/x.js"));
        assertSame(MEMBER, pageRegistryData.getAssetRole("/assets/deep/x.js"));
        assertSame(CacheControlData.PRIVATE_NO_STORE, pageRegistryData.getCacheControlData("/assets/deep/x.js"));
    }

    private static final class TestPage extends Page {

        private final Set<String> assetPaths;

        private TestPage(final String route, final AccountRoleDefinition requiredRole, final Set<String> assetPaths) {
            super(route, requiredRole);
            this.assetPaths = assetPaths;
        }

        @Override
        public Set<String> getAssetPaths() {
            return this.assetPaths;
        }
    }

    @AllArgsConstructor
    private static final class TestAssetProvider implements AssetProvider {

        private final Collection<PageAsset> assetCollection;

        @Override
        public Collection<PageAsset> getAssets() {
            return this.assetCollection;
        }
    }

    enum TestRole implements AccountRoleDefinition {

        MEMBER, ADMIN
    }
}