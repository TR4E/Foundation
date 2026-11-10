package me.trae.foundation.spring.pages.registry;

import me.trae.foundation.spring.common.account.AccountRoleDefinition;
import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.cache.CacheControlData;

import java.util.List;

public final class PageRegistry {

    private volatile PageRegistryData data = PageRegistryData.EMPTY;

    public void register(final PageRegistryData data) {
        this.data = data;
    }

    public List<Page> getPageList() {
        return this.data.getPageList();
    }

    public CacheControlData getCacheControlData(final String path) {
        return this.data.getCacheControlData(path);
    }

    public AccountRoleDefinition getAssetRole(final String path) {
        return this.data.getAssetRole(path);
    }
}