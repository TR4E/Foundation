package me.trae.foundation.spring.pages.registry;

import me.trae.foundation.spring.common.account.AccountRoleDefinition;
import me.trae.foundation.spring.pages.asset.AssetProvider;
import me.trae.foundation.spring.pages.asset.PageAsset;
import me.trae.foundation.spring.pages.cache.CacheControlData;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PageRegistryTest {

    @Test
    void emptyDataAndANewRegistryResolveNeitherRolesNorCachePolicies() {
        final PageRegistry pageRegistry = new PageRegistry();

        for (final String path : new String[]{"/", "/unknown", "/assets/x.js"}) {
            assertNull(PageRegistryData.EMPTY.getAssetRole(path));
            assertNull(PageRegistryData.EMPTY.getCacheControlData(path));
            assertNull(pageRegistry.getAssetRole(path));
            assertNull(pageRegistry.getCacheControlData(path));
        }
    }

    @Test
    void registryDelegatesExactLongestPrefixAndCaseInsensitiveRoleResolution() {
        final AssetProvider assetProvider = () -> List.of(new PageAsset("/assets/**", TestRole.MEMBER, CacheControlData.PRIVATE_REVALIDATE), new PageAsset("/assets/admin/**", TestRole.ADMIN, CacheControlData.PRIVATE_NO_STORE), new PageAsset("/assets/admin/help.js", TestRole.MEMBER, CacheControlData.NO_STORE));
        final PageRegistry pageRegistry = new PageRegistry();
        pageRegistry.register(PageRegistryBuilder.build(Collections.emptyList(), Collections.singletonList(assetProvider)));

        assertSame(TestRole.ADMIN, pageRegistry.getAssetRole("/assets/admin/deep/x.js"));
        assertSame(TestRole.ADMIN, pageRegistry.getAssetRole("/ASSETS/ADMIN/deep/x.js"));
        assertSame(CacheControlData.PRIVATE_NO_STORE, pageRegistry.getCacheControlData("/assets/admin/deep/x.js"));
        assertSame(TestRole.MEMBER, pageRegistry.getAssetRole("/assets/admin/help.js"));
        assertSame(CacheControlData.NO_STORE, pageRegistry.getCacheControlData("/assets/admin/help.js"));
        assertSame(TestRole.MEMBER, pageRegistry.getAssetRole("/assets/member.js"));
        assertTrue(pageRegistry.getPageList().isEmpty());
    }

    @Test
    void registeringNewDataReplacesBothLookupKinds() {
        final PageRegistry pageRegistry = new PageRegistry();
        pageRegistry.register(PageRegistryBuilder.build(Collections.emptyList(), Collections.singletonList(() -> Collections.singletonList(new PageAsset("/assets/**", TestRole.ADMIN, CacheControlData.PRIVATE_NO_STORE)))));

        assertSame(TestRole.ADMIN, pageRegistry.getAssetRole("/assets/x.js"));
        pageRegistry.register(PageRegistryData.EMPTY);

        assertNull(pageRegistry.getAssetRole("/assets/x.js"));
        assertNull(pageRegistry.getCacheControlData("/assets/x.js"));
    }

    enum TestRole implements AccountRoleDefinition {

        MEMBER, ADMIN
    }
}