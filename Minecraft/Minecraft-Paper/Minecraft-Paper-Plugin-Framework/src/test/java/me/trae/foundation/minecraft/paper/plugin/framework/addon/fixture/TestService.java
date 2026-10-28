package me.trae.foundation.minecraft.paper.plugin.framework.addon.fixture;

import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;

@Getter
@AddonSingleton
public final class TestService {

    private final TestSettings testSettings;

    private final long initialDelay;

    public TestService(final TestSettings testSettings) {
        this.testSettings = testSettings;
        this.initialDelay = testSettings.getDelay();
    }
}