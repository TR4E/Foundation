package me.trae.foundation.minecraft.paper.plugin.framework.addon.fixture;

import me.trae.foundation.minecraft.paper.plugin.framework.addon.Addon;

public final class TestAddon implements Addon {

    @Override
    public String getKey() {
        return "settings-test";
    }
}