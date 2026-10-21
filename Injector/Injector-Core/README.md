<h1 align="center">Injector-Core</h1>

<p align="center">Dependency resolution, component discovery, and application lifecycle management.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven runtime" src="https://img.shields.io/badge/Maven-Runtime-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Injector-Core implements [Injector-API](../Injector-API/README.md). It discovers components, resolves constructors and providers, tracks dependencies, and coordinates startup, shutdown, and dynamic component changes. Applications normally use `Injector.INSTANCE` and keep Core as a runtime dependency.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Runtime discovery](#runtime-discovery)
- [Application lifecycle](#application-lifecycle)
- [Dependency resolution](#dependency-resolution)
- [Live collections and concurrency](#live-collections-and-concurrency)
- [Troubleshooting](#troubleshooting)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Area | Responsibility |
| --- | --- |
| [`CoreInjector`](src/main/java/me/trae/foundation/injector/core/CoreInjector.java) | Public container operations and startup rollback |
| Application registry | Running applications, deferred startup, and application dependencies |
| Component container | Registrations, assignable types, and dependency-aware removal |
| Resolvers | Scanning, constructors, providers, generic types, and `@DependsOn` |
| Component lifecycle | Initialization, shutdown, application callbacks, and extension hooks |
| Extension registry | `ServiceLoader` discovery and extension dispatch |

## Runtime discovery

`Injector.INSTANCE` loads its implementation from `META-INF/services/me.trae.foundation.injector.api.Injector`. Core supplies this service descriptor. Optional extensions use a separate descriptor for `me.trae.foundation.injector.api.extension.Extension`.

The resulting Injector instance is shared by applications using the same loaded API class. Their components are visible across application boundaries. The Injector and discovered extension instances are registered before application startup.

If packaging several dependencies into one JAR, preserve and merge service descriptors so the Injector implementation and every chosen extension remain discoverable.

## Application lifecycle

Startup follows these phases:

1. Check `@Application`, reject duplicate initialization, and defer the application if its declared application dependencies are not ready.
2. Register the application instance, scan its package, and merge explicitly supplied component classes.
3. Sort component classes using `ApplicationCallback.getComponentSorter()`.
4. Construct components in dependency order, including constructor dependencies, providers, and `@DependsOn` targets. Notify extensions as each object is created.
5. Run component initialization and registration callbacks, then extension application-initialization hooks.
6. Start applications whose declared dependencies are now ready and notify live dependency listeners.

Failed startup rolls back the components created for that application. A failed attach similarly removes its new registrations; cleanup failures are attached as suppressed exceptions to the original failure.

Shutdown first stops applications that declare a dependency on the target application. Component removal also follows the registered dependency graph: dependents are removed before the objects they depend on. This applies to `detach` and can affect components belonging to another application.

Before removing a component, Core calls extension shutdown hooks, the application's unregister callback, and the component's `Lifecycle.onComponentShutdown()`. Extension application-shutdown or detach hooks follow removal. Shutting down a waiting application cancels its pending startup.

See the [API guide](../Injector-API/README.md#container-operations) for public operations and the [extensions overview](../Injector-Extensions/README.md#hook-ordering) for extension integration.

## Dependency resolution

| Rule | Effect |
| --- | --- |
| Exactly one declared constructor | Core can resolve its parameters without selecting between constructors |
| Exact registered `Type` first | Parameterized provider results retain their generic identity |
| Compatible interfaces and superclasses | Implementations can satisfy abstraction dependencies |
| Generic substitution and wildcard bounds | Type arguments constrain compatible candidates |
| Multiple compatible candidates | `AmbiguousDependencyException` |
| Missing candidate | `MissingDependencyException` |
| Cyclic creation path | `CircularDependencyException` with the dependency chain |
| Provider ownership | The owning component is created before its provider output is required |

`get(SomeClass.class)` retrieves the exact registration. Constructor resolution supports assignability; `getAll(SomeInterface.class)` retrieves assignable components explicitly.

## Live collections and concurrency

Constructor-injected `List<T>`, `Set<T>`, and `Map<Class<? extends T>, T>` track registrations as applications start and stop. Their iteration uses snapshots so container changes do not invalidate an active iteration. A component can implement `onLiveDependencyUpdate()` to rebuild its own derived lookup from those views.

`initialize`, `shutdown`, `attach`, and `detach` are synchronized on the runtime. Container lookups support concurrent access. This does not synchronize application component state or configuration reads: component methods remain responsible for their own threading requirements.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| `Injector.INSTANCE` fails during class initialization | Core is present at runtime and its service descriptor survived packaging |
| A class is never discovered | It is concrete, lies under the application's package, and has `@Singleton` or an extension-recognized annotation |
| A library component outside the scan root is missing | Pass it in the extra component class list |
| An interface injection is ambiguous | Register one implementation or request a supported live collection |
| A component constructor is rejected | Avoid generating an additional no-argument constructor alongside an injection constructor |
| An application does not start immediately | Its declared application dependencies must initialize first |
| An extension annotation has no effect | Include that extension JAR and its service descriptor |

## Installation and build

Use the API as a compile dependency and Core at runtime:

```xml
<dependency>
    <groupId>me.trae.foundation.injector</groupId>
    <artifactId>Injector-Core</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>runtime</scope>
</dependency>
```

From the repository root, using JDK 25 and Maven:

```shell
mvn -pl Injector/Injector-Core -am install
```

The tests need no external services. Start with the [Injector quick start](../README.md#quick-start) for application code.
