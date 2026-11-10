package me.trae.foundation.spring.pages.registry;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.spring.common.account.AccountRoleDefinition;
import me.trae.foundation.spring.common.utility.UtilRequestPath;
import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.cache.CacheControlData;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@AllArgsConstructor
@Getter
public final class PageRegistryData {

    public static final PageRegistryData EMPTY = new PageRegistryData(Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

    private final List<Page> pageList;
    private final Map<String, CacheControlData> cacheControlDataMap, cacheControlDataPrefixMap;
    private final Map<String, AccountRoleDefinition> protectedAssetMap, protectedAssetPrefixMap;

    public CacheControlData getCacheControlData(final String path) {
        final CacheControlData cacheControlData = this.cacheControlDataMap.get(path);

        return cacheControlData == null ? resolveByPrefix(this.cacheControlDataPrefixMap, path) : cacheControlData;
    }

    public AccountRoleDefinition getAssetRole(final String path) {
        final String lowerCasePath = path.toLowerCase(Locale.ROOT);

        final AccountRoleDefinition requiredRole = this.protectedAssetMap.get(lowerCasePath);

        return requiredRole == null ? resolveByPrefix(this.protectedAssetPrefixMap, lowerCasePath) : requiredRole;
    }

    private static <Value> Value resolveByPrefix(final Map<String, Value> prefixMap, final String path) {
        Value resolved = null;

        int length = -1;

        for (final Map.Entry<String, Value> entry : prefixMap.entrySet()) {
            if (!UtilRequestPath.matchesPrefix(path, entry.getKey()) || entry.getKey().length() <= length) {
                continue;
            }

            resolved = entry.getValue();

            length = entry.getKey().length();
        }

        return resolved;
    }
}