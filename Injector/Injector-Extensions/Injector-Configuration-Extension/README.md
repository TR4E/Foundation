<h1 align="center">Injector-Configuration-Extension</h1>

<p align="center">Injectable JSON and YAML configuration with defaults, comments, saving, and reloading.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Injector extension" src="https://img.shields.io/badge/Injector-Extension-C71A36?logo=apachemaven&logoColor=white"></a>
  <img alt="JSON and YAML" src="https://img.shields.io/badge/Configuration-JSON%20%2F%20YAML-4B5563">
</p>

Configuration turns annotated classes into injectable configuration objects. Files are loaded during component construction, missing settings keep their field defaults, and reloading updates the registered object so components retain their configuration reference. Serialization uses Gson for JSON and SnakeYAML for YAML.

## Contents

<details>
<summary>On this page</summary>

- [Define a configuration](#define-a-configuration)
- [Choose the data folder](#choose-the-data-folder)
- [Inject and use settings](#inject-and-use-settings)
- [Save and reload](#save-and-reload)
- [File and field behavior](#file-and-field-behavior)
- [Errors and troubleshooting](#errors-and-troubleshooting)
- [Installation and build](#installation-and-build)

</details>

## Define a configuration

```java
@NoArgsConstructor
@Getter
@Setter
@Configuration(value = "general", type = ConfigType.YAML)
public final class GeneralConfig {

    @Comment("Greeting shown to new users")
    private String greeting = "Welcome";

    private int maxConnections = 100;

    private Limits limits = new Limits();

    @NoArgsConstructor
    @Getter
    @Setter
    public static final class Limits {

        @Comment("Maximum requests allowed per minute")
        private int requestsPerMinute = 60;
    }
}
```

Use `Configuration` and `Comment` from `me.trae.foundation.injector.extensions.configuration.annotation`, and `ConfigType` from its `enums` package. Examples use Lombok and omit imports. These are Injector configuration annotations; Spring's `@Configuration` has a different purpose.

| Annotation setting | Result |
| --- | --- |
| `value = "general", type = ConfigType.YAML` | `general.yml` |
| `value = "general", type = ConfigType.JSON` | `general.json` |
| `@Comment("Description")` on a field | A `#` YAML comment or `//` JSON comment |

`@Configuration` makes the class discoverable through this extension; it does not need `@Singleton`. Place it under the application's scan package or supply it in the explicit component list. Declare a no-argument constructor and initialize defaults on the fields.

## Choose the data folder

Implement both `ApplicationCallback` and `ConfigurationCallback` on the application class:

```java
@Application
public final class ShopApplication implements ApplicationCallback, ConfigurationCallback {

    @Override
    public File getDataFolder() {
        return new File("config/shop");
    }

    @Override
    public void onConfigurationReload(final Class<?> type) {
        System.out.println("Reloaded %s".formatted(type.getSimpleName()));
    }

    @Override
    public void onConfigurationSave(final Class<?> type) {
        System.out.println("Saved %s".formatted(type.getSimpleName()));
    }
}
```

The default folder is the process working directory. The resolver creates missing parent directories when writing. With this example, `GeneralConfig` lives at `config/shop/general.yml`.

## Inject and use settings

```java
@AllArgsConstructor
@Singleton
public final class GreetingService {

    private final GeneralConfig generalConfig;

    public String getGreeting() {
        return this.generalConfig.getGreeting();
    }
}
```

Read settings through the injected object when they are needed. A value copied into another field at startup will remain that copied value after a reload; rebuild derived state from `onConfigurationReload` when necessary.

## Save and reload

`ConfigurationExtension` is registered by Core and can be constructor-injected into application components:

```java
@AllArgsConstructor
@Singleton
public final class ConfigurationManager {

    private final ConfigurationExtension configurationExtension;
    private final GeneralConfig generalConfig;

    public void reload() {
        this.configurationExtension.reloadConfiguration(GeneralConfig.class);
    }

    public void changeGreeting(final String greeting) {
        this.generalConfig.setGreeting(greeting);
        this.configurationExtension.saveConfiguration(GeneralConfig.class);
    }
}
```

| Operation | Scope |
| --- | --- |
| `reloadConfiguration(type)` | One loaded configuration |
| `reloadConfigurations(applicationClass)` | Every configuration owned by that application |
| `reloadAllConfigurations()` | Every loaded configuration |
| `saveConfiguration(type)` | Serialize the current in-memory values of one configuration |

Reload copies loaded fields into the existing top-level instance, rewrites the file, and then invokes the reload callback. Nested objects and collections can be replaced during that copy; keep the top-level configuration reference and retrieve its current nested values. Saves invoke the save callback. Initial loading writes the file without invoking the explicit save callback.

There is no automatic file watcher. Call a reload method when changes should take effect. Resolver operations are synchronized, but ordinary component reads are not locked by the resolver; coordinate application reads and reloads if several fields must be observed consistently.

## File and field behavior

- A missing file is created from field defaults. Missing values in an existing file retain defaults from a newly constructed configuration.
- Every successful load or reload rewrites the file with the current schema and generated comments. Treat the file as serializer-managed output; arbitrary manual formatting and comments are not preserved as an editing contract.
- Non-static, non-transient fields are included, including inherited fields. Nested objects, enums, lists, maps, and arrays are supported by the serializers.
- JSON comments generated by `@Comment` use `//`, so commented files require a reader that accepts this JSON extension.
- Reloading a missing file keeps the current instance values and writes them back. It does not reset the registered object to defaults.
- Detaching a configuration or shutting down its application removes its resolver registration. Saving and reloading operate on configurations that are still loaded.

## Errors and troubleshooting

`ConfigurationException` reports unreadable or malformed files, write failures, missing no-argument constructors, and requests for configurations that are not loaded.

| Symptom | Check |
| --- | --- |
| No file is generated | The extension JAR is present and the class is included in component discovery |
| Settings appear in an unexpected folder | Implement `getDataFolder()` on the application object |
| A new field does not load | Confirm it is neither static nor transient and has a supported serialized type |
| A service still uses an old value | Read from the injected configuration instead of retaining a copied scalar or nested reference |

## Installation and build

Add this dependency alongside Injector-API and runtime Injector-Core:

```xml
<dependency>
    <groupId>me.trae.foundation.injector.extensions</groupId>
    <artifactId>Injector-Configuration-Extension</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Gson and SnakeYAML are transitive dependencies. From the repository root with JDK 25 and Maven:

```shell
mvn -pl Injector/Injector-Extensions/Injector-Configuration-Extension -am install
```

Tests need no external services. Return to the [extensions overview](../README.md) or [Injector guide](../../README.md).
