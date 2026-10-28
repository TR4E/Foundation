package me.trae.foundation.minecraft.paper.plugin.framework.addon;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.trae.foundation.injector.api.annotation.Application;
import me.trae.foundation.injector.core.CoreInjector;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.settings.AddonSettings;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.settings.AddonSettingsBinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AddonSettingsBindingTest {

    private final CoreInjector injector = new CoreInjector();

    @Test
    void existingConsumersFollowWholeOverridesAndReturnToDefaults() {
        final TestSettings testSettings = AddonSettingsBinding.create(TestSettings.class, this.injector);
        this.injector.provide(TestSettings.class, testSettings);
        this.injector.initialize(new CoreApplication(), List.of(SettingsConsumer.class));

        final SettingsConsumer settingsConsumer = this.injector.get(SettingsConsumer.class);
        assertEquals("default", settingsConsumer.getTestSettings().getName());

        this.injector.attach(new CoreApplication(), List.of(CoreSettings.class));
        assertEquals("core", settingsConsumer.getTestSettings().getName());
        assertEquals(300L, settingsConsumer.getTestSettings().getDelay());

        this.injector.initialize(new DependentApplication(), List.of(DependentSettings.class));
        assertSame(testSettings, settingsConsumer.getTestSettings());
        assertEquals("dependent", settingsConsumer.getTestSettings().getName());
        assertEquals(500L, settingsConsumer.getTestSettings().getDelay());
        assertEquals(2, this.injector.getAll(TestSettings.class).size());

        this.injector.shutdown(new DependentApplication());
        assertEquals("core", settingsConsumer.getTestSettings().getName());

        this.injector.detach(new CoreApplication(), List.of(CoreSettings.class));
        assertEquals("default", settingsConsumer.getTestSettings().getName());
        assertEquals("default:damage", settingsConsumer.getTestSettings().describe("damage"));
        assertThrows(IllegalStateException.class, settingsConsumer.getTestSettings()::fail);
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

        default void fail() {
            throw new IllegalStateException("failure");
        }
    }

    @AllArgsConstructor
    @Getter
    private static final class SettingsConsumer {

        private final TestSettings testSettings;
    }

    private static final class CoreSettings implements TestSettings {

        @Override
        public String getName() {
            return "core";
        }

        @Override
        public long getDelay() {
            return 300L;
        }
    }

    private static final class DependentSettings implements TestSettings {

        @Override
        public String getName() {
            return "dependent";
        }
    }

    @NoArgsConstructor
    @Application
    private static final class CoreApplication {

    }

    @NoArgsConstructor
    @Application(dependencies = CoreApplication.class)
    private static final class DependentApplication {

    }
}