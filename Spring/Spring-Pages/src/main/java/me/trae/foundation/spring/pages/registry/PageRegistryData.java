package me.trae.foundation.spring.pages.registry;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.cache.CacheControlData;
import me.trae.foundation.spring.pages.role.PageRole;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Getter
public final class PageRegistryData {

    public static final PageRegistryData EMPTY = new PageRegistryData(Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());

    private final List<Page> pageList;
    private final Map<String, CacheControlData> cacheControlDataMap;
    private final Map<String, PageRole> protectedAssetMap;
}