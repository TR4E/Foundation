package me.trae.foundation.minecraft.paper.plugin.framework.addon;

import me.trae.foundation.minecraft.paper.plugin.framework.addon.settings.AddonSettings;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.settings.AddonSettingsFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class AddonSettingsFactoryTest {

    @Test
    void invokesDefaultsWithArgumentsAndNestedDefaultCalls() {
        final TestSettings testSettings = AddonSettingsFactory.create(TestSettings.class, List.of());

        assertEquals("default", testSettings.getName());
        assertEquals("default:damage", testSettings.describe("damage"));
    }

    @Test
    void returnsTheLastOverrideAsAWhole() {
        final FirstSettings firstSettings = new FirstSettings();
        final LastSettings lastSettings = new LastSettings();
        final TestSettings testSettings = AddonSettingsFactory.create(TestSettings.class, List.of(firstSettings, lastSettings));

        assertSame(lastSettings, testSettings);
        assertEquals("last", testSettings.getName());
        assertEquals(500L, testSettings.getDelay());
    }

    public interface TestSettings extends AddonSettings {

        default String getName() {
            return "default";
        }

        default long getDelay() {
            return 500L;
        }

        default String describe(final String context) {
            return "%s:%s".formatted(this.getName(), context);
        }
    }

    private static final class FirstSettings implements TestSettings {

        @Override
        public long getDelay() {
            return 300L;
        }
    }

    private static final class LastSettings implements TestSettings {

        @Override
        public String getName() {
            return "last";
        }
    }
}