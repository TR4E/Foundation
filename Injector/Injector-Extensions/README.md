<h1 align="center">Injector-Extensions</h1>

<p align="center">Optional lifecycle, scheduling, and configuration features for Injector applications.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven aggregator" src="https://img.shields.io/badge/Maven-Aggregator-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

This module groups the optional Injector extensions. It is a Maven parent and aggregator with `pom` packaging; add the individual extension artifacts that your application uses. Each extension is discovered automatically by [Injector-Core](../Injector-Core/README.md).

## Contents

<details>
<summary>On this page</summary>

- [Choose an extension](#choose-an-extension)
- [Installation](#installation)
- [Application callbacks](#application-callbacks)
- [Hook ordering](#hook-ordering)
- [Writing an extension](#writing-an-extension)
- [Build and test](#build-and-test)

</details>

## Choose an extension

| Module | Adds | Application callback |
| --- | --- | --- |
| [Commons](Injector-Commons-Extension/README.md) | `@PostConstruct`, `@ApplicationReady`, `@PreDestroy`, `@PostDestroy` | Uses the standard application lifecycle |
| [Scheduler](Injector-Scheduler-Extension/README.md) | Repeating `@Scheduler` methods | `SchedulerCallback` selects executors |
| [Configuration](Injector-Configuration-Extension/README.md) | JSON/YAML files, defaults, comments, save, and reload | `ConfigurationCallback` selects the data folder and receives change notifications |

The extensions are independent. Configuration and Scheduler can be used without Commons, and all three can be combined.

## Installation

In addition to Injector-API and runtime Injector-Core, add the chosen extension. For example:

```xml
<dependency>
    <groupId>me.trae.foundation.injector.extensions</groupId>
    <artifactId>Injector-Commons-Extension</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Use the same group and version for `Injector-Scheduler-Extension` or `Injector-Configuration-Extension`. Keep an extension at compile scope when application code imports its annotations or types.

Core loads extension implementations from `META-INF/services/me.trae.foundation.injector.api.extension.Extension`. Preserve all entries when building a combined JAR. Discovered extensions are injectable components; for example, inject `ConfigurationExtension` to save or reload configurations.

## Application callbacks

Implement `ApplicationCallback` and the chosen extension callback interfaces on the `@Application` class. Core passes that application object to extensions only when it implements `ApplicationCallback`; otherwise extensions receive the default callback object.

| Callback method | Default |
| --- | --- |
| `SchedulerCallback.getSynchronousExecutor()` | Execute on the scheduler thread |
| `SchedulerCallback.getAsynchronousExecutor()` | Start a virtual thread for each run |
| `ConfigurationCallback.getDataFolder()` | Current working directory |
| `ConfigurationCallback.onConfigurationReload(type)` | No action |
| `ConfigurationCallback.onConfigurationSave(type)` | No action |

An application may implement both callback interfaces alongside `ApplicationCallback`. See the individual guides for complete examples.

## Hook ordering

| SPI hook | Runtime phase |
| --- | --- |
| `isComponent(type)` | Decide whether scanning includes a class |
| `instantiate(applicationClass, applicationCallback, type)` | Supply a component instance before ordinary constructor creation |
| `onComponentCreate(...)` | After construction and registration of a component |
| `onApplicationInitialize(...)` | After all component initialization and application registration callbacks |
| `onComponentShutdown(...)` | Before unregister and component shutdown callbacks |
| `onApplicationShutdown(...)` | After application component removal |
| `onComponentsAttach(...)` | After newly attached components initialize |
| `onComponentsDetach(...)` | After detached components and affected dependents are removed |

Hooks are dispatched to the discovered extensions. Avoid coupling correctness to the relative discovery order of separate extension JARs; express component dependencies through constructor parameters or `@DependsOn`.

## Writing an extension

Implement the API's `Extension` interface. Every hook has a default implementation, so override only the behavior needed:

```java
@NoArgsConstructor
public final class LoggingExtension implements Extension {

    @Override
    public void onComponentCreate(
            final Class<?> applicationClass,
            final ApplicationCallback applicationCallback,
            final Object component
    ) {
        System.out.println("Created %s for %s".formatted(
                component.getClass().getName(),
                applicationClass.getName()
        ));
    }
}
```

Place the fully qualified implementation name in your JAR's `src/main/resources/META-INF/services/me.trae.foundation.injector.api.extension.Extension`:

```text
com.example.injector.LoggingExtension
```

The example assumes `LoggingExtension` is declared in `com.example.injector`. A service-loaded implementation needs an accessible no-argument constructor. Return `Optional.empty()` from `instantiate` for types the extension does not create. Extensions themselves are created by `ServiceLoader`, so obtain application-specific state from the hook arguments.

## Build and test

From the repository root with JDK 25 and Maven:

```shell
mvn -pl Injector/Injector-Extensions/Injector-Commons-Extension,Injector/Injector-Extensions/Injector-Scheduler-Extension,Injector/Injector-Extensions/Injector-Configuration-Extension -am install
```

The extension tests require no PostgreSQL, Redis, or other external service. Return to the [Injector guide](../README.md).
