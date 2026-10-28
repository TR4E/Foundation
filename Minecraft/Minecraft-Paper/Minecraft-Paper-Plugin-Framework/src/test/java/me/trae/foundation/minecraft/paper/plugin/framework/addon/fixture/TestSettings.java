package me.trae.foundation.minecraft.paper.plugin.framework.addon.fixture;

import me.trae.foundation.minecraft.paper.plugin.framework.addon.settings.AddonSettings;

public interface TestSettings extends AddonSettings {

    default long getDelay() {
        return 500L;
    }
}