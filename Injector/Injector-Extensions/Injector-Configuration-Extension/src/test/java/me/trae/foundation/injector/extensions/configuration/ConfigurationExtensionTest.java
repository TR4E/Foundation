package me.trae.foundation.injector.extensions.configuration;

import me.trae.foundation.injector.api.annotation.Application;
import me.trae.foundation.injector.api.callback.ApplicationCallback;
import me.trae.foundation.injector.core.CoreInjector;
import me.trae.foundation.injector.extensions.configuration.annotation.Comment;
import me.trae.foundation.injector.extensions.configuration.annotation.Configuration;
import me.trae.foundation.injector.extensions.configuration.callback.ConfigurationCallback;
import me.trae.foundation.injector.extensions.configuration.enums.ConfigType;
import me.trae.foundation.injector.extensions.configuration.exception.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigurationExtensionTest {

    private final CoreInjector injector = new CoreInjector();

    @Test
    void writesDefaultsWithCommentsOnFirstStart(@TempDir final Path dataFolder) throws IOException {
        this.injector.initialize(new TestApplication(dataFolder));

        final String json = Files.readString(dataFolder.resolve("general.json"));
        assertTrue(json.contains("// Shown to players when they join"));
        assertTrue(json.contains("\"motd\": \"Welcome\""));

        final String yaml = Files.readString(dataFolder.resolve("database.yml"));
        assertTrue(yaml.contains("# PostgreSQL host"));
        assertTrue(yaml.contains("  # Maximum open connections"));
    }

    @Test
    void readsJsonAndKeepsDefaultsForMissingValues(@TempDir final Path dataFolder) throws IOException {
        Files.writeString(dataFolder.resolve("general.json"), "{\n  // Shown to players when they join\n  \"motd\": \"Hello\",\n  \"website\": \"https://trae.dev\"\n}\n");

        this.injector.initialize(new TestApplication(dataFolder));

        final GeneralConfig generalConfig = this.injector.get(GeneralConfig.class);
        assertEquals("Hello", generalConfig.motd);
        assertEquals("https://trae.dev", generalConfig.website);
        assertEquals(100, generalConfig.maxPlayers);
    }

    @Test
    void readsYamlIncludingNestedValues(@TempDir final Path dataFolder) throws IOException {
        Files.writeString(dataFolder.resolve("database.yml"), "# PostgreSQL host\nhost: db.internal\nport: 6543\npool:\n  maximumSize: 25\n");

        this.injector.initialize(new TestApplication(dataFolder));

        final DatabaseConfig databaseConfig = this.injector.get(DatabaseConfig.class);
        assertEquals("db.internal", databaseConfig.host);
        assertEquals(6543, databaseConfig.port);
        assertEquals(25, databaseConfig.pool.maximumSize);
    }

    @Test
    void injectsConfigurationIntoComponents(@TempDir final Path dataFolder) {
        this.injector.initialize(new TestApplication(dataFolder), List.of(Greeter.class));

        assertSame(this.injector.get(GeneralConfig.class), this.injector.get(Greeter.class).generalConfig());
    }

    @Test
    void reloadUpdatesTheSameInstance(@TempDir final Path dataFolder) throws IOException {
        this.injector.initialize(new TestApplication(dataFolder));

        final GeneralConfig generalConfig = this.injector.get(GeneralConfig.class);
        Files.writeString(dataFolder.resolve("general.json"), "{\"motd\": \"Reloaded\"}");

        this.injector.get(ConfigurationExtension.class).reloadConfiguration(GeneralConfig.class);

        assertSame(generalConfig, this.injector.get(GeneralConfig.class));
        assertEquals("Reloaded", generalConfig.motd);
    }

    @Test
    void saveWritesCurrentValues(@TempDir final Path dataFolder) throws IOException {
        this.injector.initialize(new TestApplication(dataFolder));

        this.injector.get(GeneralConfig.class).motd = "Saved";
        this.injector.get(ConfigurationExtension.class).saveConfiguration(GeneralConfig.class);

        assertTrue(Files.readString(dataFolder.resolve("general.json")).contains("\"motd\": \"Saved\""));
    }

    @Test
    void writesCommentsOnNestedJsonFieldsAndReadsThemBack(@TempDir final Path dataFolder) throws IOException {
        this.injector.initialize(new TestApplication(dataFolder));
        assertTrue(Files.readString(dataFolder.resolve("general.json")).contains("    // Homes each player can set"));

        Files.writeString(dataFolder.resolve("general.json"), Files.readString(dataFolder.resolve("general.json")).replace("\"homes\": 3", "\"homes\": 5"));
        this.injector.get(ConfigurationExtension.class).reloadConfiguration(GeneralConfig.class);

        assertEquals(5, this.injector.get(GeneralConfig.class).limits.homes);
    }

    @Test
    void yamlKeepsListsAcrossRestarts(@TempDir final Path dataFolder) {
        this.injector.initialize(new TestApplication(dataFolder));
        this.injector.shutdown(new TestApplication(dataFolder));

        final CoreInjector restarted = new CoreInjector();
        restarted.initialize(new TestApplication(dataFolder));

        assertEquals(List.of("db-1", "db-2"), restarted.get(DatabaseConfig.class).replicas);
        assertEquals(10, restarted.get(DatabaseConfig.class).pool.maximumSize);
    }

    @Test
    void reloadAllPicksUpEveryChangedFile(@TempDir final Path dataFolder) throws IOException {
        this.injector.initialize(new TestApplication(dataFolder));

        Files.writeString(dataFolder.resolve("general.json"), "{\"motd\": \"All\"}");
        Files.writeString(dataFolder.resolve("database.yml"), "host: reloaded.internal\n");
        this.injector.get(ConfigurationExtension.class).reloadAllConfigurations();

        assertEquals("All", this.injector.get(GeneralConfig.class).motd);
        assertEquals("reloaded.internal", this.injector.get(DatabaseConfig.class).host);
    }

    @Test
    void forgetsConfigurationsAfterShutdown(@TempDir final Path dataFolder) {
        final TestApplication application = new TestApplication(dataFolder);
        this.injector.initialize(application);

        final ConfigurationExtension configurationExtension = this.injector.get(ConfigurationExtension.class);
        this.injector.shutdown(application);

        assertThrows(ConfigurationException.class, () -> configurationExtension.reloadConfiguration(GeneralConfig.class));
    }

    @Test
    void rejectsUnknownConfiguration(@TempDir final Path dataFolder) {
        this.injector.initialize(new TestApplication(dataFolder));

        assertThrows(ConfigurationException.class, () -> this.injector.get(ConfigurationExtension.class).reloadConfiguration(String.class));
    }

    @Application
    private static final class TestApplication implements ApplicationCallback, ConfigurationCallback {

        private final Path dataFolder;

        private TestApplication(final Path dataFolder) {
            this.dataFolder = dataFolder;
        }

        @Override
        public File getDataFolder() {
            return this.dataFolder.toFile();
        }
    }

    @Configuration("general")
    static final class GeneralConfig {

        @Comment("Shown to players when they join")
        private String motd = "Welcome";
        private int maxPlayers = 100;
        private String website = "https://example.com";
        private Limits limits = new Limits();
    }

    static final class Limits {

        @Comment("Homes each player can set")
        private int homes = 3;
    }

    @Configuration(value = "database", type = ConfigType.YAML)
    static final class DatabaseConfig {

        @Comment("PostgreSQL host")
        private String host = "localhost";
        private int port = 5432;
        private Pool pool = new Pool();
        private List<String> replicas = new ArrayList<>(List.of("db-1", "db-2"));
    }

    static final class Pool {

        @Comment("Maximum open connections")
        private int maximumSize = 10;
    }

    private record Greeter(
            GeneralConfig generalConfig
    ) {
    }
}