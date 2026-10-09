package me.trae.foundation.spring.pages.registry;

import lombok.experimental.UtilityClass;
import me.trae.foundation.spring.common.utility.UtilRequestPath;
import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.asset.AssetProvider;
import me.trae.foundation.spring.pages.asset.PageAsset;
import me.trae.foundation.spring.pages.cache.CacheControlData;
import me.trae.foundation.spring.pages.exception.PageRegistrationException;
import me.trae.foundation.spring.pages.role.PageRole;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UtilityClass
public class PageRegistryBuilder {

    public static PageRegistryData build(final List<Page> pageList, final List<AssetProvider> assetProviderList) {
        final Map<String, CacheControlData> cacheControlMap = new HashMap<>();
        final Map<String, PageRole> assetRoleMap = new HashMap<>();
        final Map<String, String> ownerMap = new HashMap<>();

        for (final Page page : pageList) {
            final String route = UtilRequestPath.canonicalise(page.getRoute());

            if (cacheControlMap.put(route, page.getRouteCacheControlData()) != null) {
                throw new PageRegistrationException("Duplicate page route %s on %s".formatted(route, page.getClass().getName()));
            }

            for (final String assetPath : page.getAssetPaths()) {
                register(cacheControlMap, assetRoleMap, ownerMap, new PageAsset(UtilRequestPath.canonicalise(assetPath), page.getRequiredRole(), page.getAssetCacheControlData()), page.getClass().getName());
            }
        }

        for (final AssetProvider assetProvider : assetProviderList) {
            for (final PageAsset pageAsset : assetProvider.getAssets()) {
                register(cacheControlMap, assetRoleMap, ownerMap, new PageAsset(UtilRequestPath.canonicalise(pageAsset.getPath()), pageAsset.getRequiredRole(), pageAsset.getCacheControlData()), assetProvider.getClass().getName());
            }
        }

        return new PageRegistryData(List.copyOf(pageList), Map.copyOf(cacheControlMap), Map.copyOf(assetRoleMap));
    }

    private static void register(final Map<String, CacheControlData> cacheControlMap, final Map<String, PageRole> assetRoleMap, final Map<String, String> ownerMap, final PageAsset pageAsset, final String owner) {
        final String previousOwner = ownerMap.put(pageAsset.getPath(), owner);

        if (previousOwner != null) {
            throw new PageRegistrationException("Duplicate asset path %s on %s, already declared by %s".formatted(pageAsset.getPath(), owner, previousOwner));
        }

        cacheControlMap.put(pageAsset.getPath(), pageAsset.getCacheControlData());

        if (pageAsset.getRequiredRole() == null) {
            return;
        }

        assetRoleMap.put(pageAsset.getPath(), pageAsset.getRequiredRole());
    }
}