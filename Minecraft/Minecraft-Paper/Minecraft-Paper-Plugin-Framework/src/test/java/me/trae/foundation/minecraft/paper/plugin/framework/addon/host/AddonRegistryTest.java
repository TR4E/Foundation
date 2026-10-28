package me.trae.foundation.minecraft.paper.plugin.framework.addon.host;

import me.trae.foundation.injector.api.Injector;
import me.trae.foundation.injector.api.annotation.Application;
import me.trae.foundation.minecraft.paper.plugin.framework.PaperPlugin;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.AddonRegistry;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.annotation.Addons;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.fixture.TestAddon;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.fixture.TestService;
import me.trae.foundation.minecraft.paper.plugin.framework.addon.fixture.TestSettings;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class AddonRegistryTest {

    private final TestPlugin testPlugin = mock(TestPlugin.class);

    private final AddonRegistry addonRegistry = new AddonRegistry(this.testPlugin);

    @BeforeEach
    void configureHost() {
        final Server server = mock(Server.class);
        final ServicesManager servicesManager = mock(ServicesManager.class);
        when(this.testPlugin.getServer()).thenReturn(server);
        when(this.testPlugin.getComponentSorter()).thenReturn(Comparator.comparing(Class::getName));
        when(server.getServicesManager()).thenReturn(servicesManager);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(servicesManager.getRegistrations(String.class)).thenReturn(List.of(new RegisteredServiceProvider<>(String.class, "addon:settings-test", ServicePriority.Normal, this.testPlugin)));
    }

    @AfterEach
    void shutdown() {
        this.addonRegistry.shutdown();

        Injector.INSTANCE.shutdown(this.testPlugin);
        Injector.INSTANCE.unprovide(TestSettings.class);
    }

    @Test
    void bindsDefaultsBeforeConstructingAddonComponents() {
        Injector.INSTANCE.initialize(this.testPlugin, this.addonRegistry.initialize());
        this.addonRegistry.start();

        assertEquals(500L, Injector.INSTANCE.get(TestService.class).getTestSettings().getDelay());
        assertEquals(500L, Injector.INSTANCE.get(TestService.class).getInitialDelay());
        assertEquals(List.of(), Injector.INSTANCE.getAll(TestSettings.class));
    }

    @Test
    void discoversAnOverrideConstructedAfterRegistryInitialization() {
        final List<Class<?>> componentClassList = Stream.concat(this.addonRegistry.initialize().stream(), Stream.of(CoreSettings.class)).toList();
        Injector.INSTANCE.initialize(this.testPlugin, componentClassList);
        this.addonRegistry.start();

        assertEquals(300L, Injector.INSTANCE.get(TestService.class).getTestSettings().getDelay());
        assertEquals(300L, Injector.INSTANCE.get(TestService.class).getInitialDelay());
        assertEquals(1, Injector.INSTANCE.getAll(TestSettings.class).size());
    }

    @Application
    @Addons(TestAddon.class)
    public static final class TestPlugin extends PaperPlugin {

        @Override
        public String getPluginName() {
            return "settings-test";
        }
    }

    public static final class CoreSettings implements TestSettings {

        @Override
        public long getDelay() {
            return 300L;
        }
    }
}