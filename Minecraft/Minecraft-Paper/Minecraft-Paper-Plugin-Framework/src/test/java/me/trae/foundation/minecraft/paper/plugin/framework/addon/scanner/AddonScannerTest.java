package me.trae.foundation.minecraft.paper.plugin.framework.addon.scanner;

import me.trae.foundation.minecraft.paper.plugin.framework.addon.Addon;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.AddonSingleton;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.settings.AddonSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AddonScannerTest {

    @Test
    void discoversOnlySettingsInterfaces() {
        assertEquals(List.of(TestSettings.class), AddonScanner.scanSettings(TestAddon.class));
        assertEquals(List.of(TestService.class), AddonScanner.scan(TestAddon.class));
    }

    public interface TestSettings extends AddonSettings {

        default long getDelay() {
            return 500L;
        }
    }

    public interface UnrelatedSettings {

    }

    public static final class ConcreteSettings implements TestSettings {

    }

    @AddonSingleton
    public static final class TestService {

    }

    public static final class TestAddon implements Addon {

        @Override
        public String getKey() {
            return "scanner-test";
        }
    }
}