<h1 align="center">Injector-Commons-Extension</h1>

<p align="center">Annotation-based lifecycle hooks for Injector components.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Injector extension" src="https://img.shields.io/badge/Injector-Extension-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Commons adds four lifecycle annotations to [Injector](../../README.md). Use them for work that belongs to a particular component phase, such as preparing local state after construction, warming a cache after startup, or releasing a resource during shutdown. The extension is discovered automatically when its JAR is present.

## Contents

<details>
<summary>On this page</summary>

- [Lifecycle annotations](#lifecycle-annotations)
- [Usage](#usage)
- [Method rules and inheritance](#method-rules-and-inheritance)
- [Attach, detach, and failure behavior](#attach-detach-and-failure-behavior)
- [Installation and build](#installation-and-build)

</details>

## Lifecycle annotations

| Annotation | When it runs | Typical use |
| --- | --- | --- |
| `@PostConstruct` | Immediately after the component is created | Prepare state that needs injected dependencies |
| `@ApplicationReady` | After the application's components have initialized | Work that requires the complete application |
| `@PreDestroy` | Before the component's unregister and shutdown callbacks | Stop accepting work or begin resource cleanup |
| `@PostDestroy` | After application component removal, in reverse creation order | Final cleanup after shutdown |

These annotations are in `me.trae.foundation.injector.extensions.commons.annotation`. Import these types explicitly if another library also defines annotations named `PostConstruct` or `PreDestroy`.

## Usage

```java
@Singleton
public final class CacheWarmer {

    @PostConstruct
    private void prepare() {
        // Prepare this component's local state.
    }

    @ApplicationReady
    private void warm() {
        // Read other initialized components and warm application caches.
    }

    @PreDestroy
    private void stop() {
        // Stop accepting new work.
    }

    @PostDestroy
    private void release() {
        // Release remaining resources.
    }
}
```

The class must still be a discovered or explicitly supplied component. Lifecycle annotations alone do not make a class discoverable. Examples use Lombok and omit imports.

For a component that also implements the API's `Lifecycle`, the broad sequence is:

1. Construction, then `@PostConstruct`.
2. `onComponentInitialize()`, then the application's registration callback.
3. `@ApplicationReady` during the Commons application-initialization hook.
4. On removal, `@PreDestroy`, the application's unregister callback, then `onComponentShutdown()`.
5. `@PostDestroy` during application shutdown or component detach.

Use `Lifecycle.onLiveDependencyUpdate()` when derived state must react to future container changes; `@ApplicationReady` is a readiness hook, not a notification for every later registration.

## Method rules and inheritance

Annotated methods must take no parameters. Private methods are supported through reflective access, and the resolver walks the concrete class and its superclasses.

For each annotation, methods are deduplicated by name while walking from the subclass upward. An annotated subclass hook with the same name takes precedence over an annotated superclass hook. Give hooks distinct names when both should run. Method order within one class follows reflection discovery and should not be used as a sequencing contract; combine ordered steps in one hook.

If reflective access or the hook itself fails, Commons throws `LifecycleException`, preserving the underlying invocation cause.

## Attach, detach, and failure behavior

Attached components receive `@PostConstruct` during creation and `@ApplicationReady` after initialization. Detached components receive `@PreDestroy` during removal and `@PostDestroy` through the detach hook. They are removed from Commons' tracking so a later application shutdown does not repeat their final hook.

A failing startup hook propagates to Core, which attempts startup rollback. A failing shutdown hook propagates to its caller. Keep cleanup hooks small and account for any resource that may have been only partly initialized.

## Installation and build

Add this dependency alongside Injector-API and runtime Injector-Core:

```xml
<dependency>
    <groupId>me.trae.foundation.injector.extensions</groupId>
    <artifactId>Injector-Commons-Extension</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

From the repository root with JDK 25 and Maven:

```shell
mvn -pl Injector/Injector-Extensions/Injector-Commons-Extension -am install
```

No external services are needed for its tests. See the [extensions overview](../README.md) for SPI details and the other optional modules.
