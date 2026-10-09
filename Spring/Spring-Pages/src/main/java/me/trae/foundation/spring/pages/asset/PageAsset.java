package me.trae.foundation.spring.pages.asset;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.spring.pages.cache.CacheControlData;
import me.trae.foundation.spring.pages.role.PageRole;

@AllArgsConstructor
@Getter
public final class PageAsset {

    private final String path;
    private final PageRole requiredRole;
    private final CacheControlData cacheControlData;

    public PageAsset(final String path) {
        this(path, null, CacheControlData.SHARED_SHORT);
    }
}