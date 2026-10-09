package me.trae.foundation.spring.pages.asset;

import java.util.Collection;

public interface AssetProvider {

    Collection<PageAsset> getAssets();
}