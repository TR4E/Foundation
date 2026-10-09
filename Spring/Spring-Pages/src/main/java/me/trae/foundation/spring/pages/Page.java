package me.trae.foundation.spring.pages;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.spring.pages.cache.CacheControlData;
import me.trae.foundation.spring.pages.role.PageRole;

import java.util.Collections;
import java.util.Set;

@AllArgsConstructor
@Getter
public abstract class Page {

    private final String route;
    private final PageRole requiredRole;

    public Set<String> getAssetPaths() {
        return Collections.emptySet();
    }

    public CacheControlData getRouteCacheControlData() {
        return CacheControlData.PRIVATE_REVALIDATE;
    }

    public CacheControlData getAssetCacheControlData() {
        return this.requiredRole == null ? CacheControlData.SHARED_SHORT : CacheControlData.PRIVATE_NO_STORE;
    }

    public boolean isPublic() {
        return this.requiredRole == null;
    }

    public String getBaseRoute() {
        final int index = this.route.indexOf('{');

        if (index < 0) {
            return this.route;
        }

        final String base = this.route.substring(0, index);

        return base.length() > 1 && base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}