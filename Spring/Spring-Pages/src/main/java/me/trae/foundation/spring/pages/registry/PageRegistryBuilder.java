package me.trae.foundation.spring.pages.registry;

import lombok.experimental.UtilityClass;
import me.trae.foundation.spring.common.account.AccountRoleDefinition;
import me.trae.foundation.spring.common.utility.UtilRequestPath;
import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.asset.AssetProvider;
import me.trae.foundation.spring.pages.asset.PageAsset;
import me.trae.foundation.spring.pages.cache.CacheControlData;
import me.trae.foundation.spring.pages.exception.PageRegistrationException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@UtilityClass
public class PageRegistryBuilder {

    private static final String WILDCARD_SUFFIX = "/**";

    public static PageRegistryData build(final List<Page> pageList, final List<AssetProvider> assetProviderList) {
        final Map<String, PageAsset> assetMap = new HashMap<>();
        final Map<String, PageAsset> assetPrefixMap = new HashMap<>();
        final Map<String, String> ownerMap = new HashMap<>();
        final Map<String, CacheControlData> cacheControlMap = new HashMap<>();

        for (final Page page : pageList) {
            final String route = UtilRequestPath.canonicalise(page.getRoute());

            if (cacheControlMap.put(route, page.getRouteCacheControlData()) != null) {
                throw new PageRegistrationException("Duplicate page route %s on %s".formatted(route, page.getClass().getName()));
            }

            for (final String assetPath : page.getAssetPaths()) {
                register(assetMap, assetPrefixMap, ownerMap, new PageAsset(assetPath, page.getRequiredRole(), page.getAssetCacheControlData()), page.getClass().getName());
            }
        }

        for (final AssetProvider assetProvider : assetProviderList) {
            for (final PageAsset pageAsset : assetProvider.getAssets()) {
                register(assetMap, assetPrefixMap, ownerMap, pageAsset, assetProvider.getClass().getName());
            }
        }

        final Map<String, CacheControlData> cacheControlPrefixMap = new HashMap<>();
        final Map<String, AccountRoleDefinition> assetRoleMap = new HashMap<>();
        final Map<String, AccountRoleDefinition> assetRolePrefixMap = new HashMap<>();

        expand(assetMap, cacheControlMap, assetRoleMap);
        expand(assetPrefixMap, cacheControlPrefixMap, assetRolePrefixMap);

        return new PageRegistryData(List.copyOf(pageList), Map.copyOf(cacheControlMap), Map.copyOf(cacheControlPrefixMap), Map.copyOf(assetRoleMap), Map.copyOf(assetRolePrefixMap));
    }

    private static void register(final Map<String, PageAsset> assetMap, final Map<String, PageAsset> assetPrefixMap, final Map<String, String> ownerMap, final PageAsset pageAsset, final String owner) {
        final boolean wildcard = pageAsset.getPath().endsWith(WILDCARD_SUFFIX);

        final String path = UtilRequestPath.canonicalise(wildcard ? pageAsset.getPath().substring(0, pageAsset.getPath().length() - WILDCARD_SUFFIX.length()) : pageAsset.getPath());

        final String declarationPath = wildcard ? path + WILDCARD_SUFFIX : path;
        final String previousOwner = ownerMap.put(declarationPath.toLowerCase(Locale.ROOT), owner);

        if (previousOwner != null) {
            throw new PageRegistrationException("Duplicate asset path %s on %s, already declared by %s".formatted(declarationPath, owner, previousOwner));
        }

        if (wildcard) {
            assetPrefixMap.put(path, pageAsset);
            return;
        }

        assetMap.put(path, pageAsset);
    }

    private static void expand(final Map<String, PageAsset> source, final Map<String, CacheControlData> cacheControlTarget, final Map<String, AccountRoleDefinition> roleTarget) {
        for (final Map.Entry<String, PageAsset> entry : source.entrySet()) {
            cacheControlTarget.put(entry.getKey(), entry.getValue().getCacheControlData());

            if (entry.getValue().getRequiredRole() == null) {
                continue;
            }

            roleTarget.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().getRequiredRole());
        }
    }
}