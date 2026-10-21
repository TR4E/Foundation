<h1 align="center">Injector-API</h1>

<p align="center">The public contracts for applications, components, dependency injection, and extensions.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven library" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Injector-API is the module application code compiles against. It defines the annotations, container interface, lifecycle callbacks, extension SPI, and exceptions. [Injector-Core](../Injector-Core/README.md) supplies the runtime implementation through Java's `ServiceLoader`.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Applications and components](#applications-and-components)
- [Providers and dependency types](#providers-and-dependency-types)
- [Container operations](#container-operations)
- [Lifecycle and callbacks](#lifecycle-and-callbacks)
- [Extension contract](#extension-contract)
- [Exceptions](#exceptions)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Contract | Purpose |
| --- | --- |
| [`Injector`](src/main/java/me/trae/foundation/injector/api/Injector.java) | Start and stop applications, retrieve components, and attach or detach components |
| `@Application` | Declare an application's scan root and dependencies on other applications |
| `@Singleton` | Mark a class for automatic component discovery |
| `@Provider` | Register a method's returned object as a component |
| `@DependsOn` | Declare creation dependencies without constructor parameters |
| `Lifecycle` | Receive initialization, shutdown, and live dependency updates |
| `ApplicationCallback` | Customize sorting and application integration |
| [`Extension`](src/main/java/me/trae/foundation/injector/api/extension/Extension.java) | Add discovery, construction, and lifecycle behavior |

## Applications and components

Place the application in a named package above its components. Core scans that package and its children for concrete `@Singleton` classes and classes recognized by extensions.

```java
@Application
public final class ShopApplication {

    public static void main(final String[] args) {
        final ShopApplication shopApplication = new ShopApplication();
        Injector.INSTANCE.initialize(shopApplication);

        try {
            Injector.INSTANCE.get(OrderService.class).placeOrder("alice");
        } finally {
            Injector.INSTANCE.shutdown(shopApplication);
        }
    }
}

@Singleton
public final class OrderRepository {

    public void save(final String customer) {
        // Persist the application's order here.
    }
}

@AllArgsConstructor
@Singleton
public final class OrderService {

    private final OrderRepository orderRepository;

    public void placeOrder(final String customer) {
        this.orderRepository.save(customer);
    }
}
```

Examples use Lombok and omit imports; put public types in their own files. A normally constructed component must declare exactly one constructor. `@AllArgsConstructor` supplies constructor injection for final dependencies.

`@Application(dependencies = BillingApplication.class)` defers startup until that application is ready. Components share one container across applications, so declare application dependencies when startup order matters. Classes passed to `initialize(application, componentClassList)` are included even without `@Singleton`.

## Providers and dependency types

A provider method belongs to a component. Its parameters are injected and its returned object is registered with its full generic return type.

```java
@Singleton
public final class ClockModule {

    @Provider
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

| Constructor parameter | Resolution |
| --- | --- |
| `OrderRepository` | A matching concrete component |
| A service interface | A compatible implementation; multiple matches are ambiguous |
| A parameterized type | Generic type arguments participate in matching |
| `List<Feature>` or `Set<Feature>` | A live view of matching components |
| `Map<Class<? extends Feature>, Feature>` | A live component map keyed by class |
| `Injector` | The current container |
| An application or extension type | Its registered instance |

Exact type matches take priority. `@DependsOn(ClockModule.class)` adds an ordering dependency, including when inherited through a superclass or interface. Provider methods must return a non-null value and cannot return `void`.

Live collections change when applications or components are added and removed. `getAll(type)` returns a list of the current matches; retain an injected live collection when future changes must be visible.

## Container operations

| Method | Behavior |
| --- | --- |
| `initialize(application)` | Scan, construct, and initialize the application's components |
| `initialize(application, componentClassList)` | Include additional component classes |
| `shutdown(application)` | Stop dependent applications, then remove components in dependency order |
| `get(type)` | Retrieve the exact registered type; throws when absent |
| `getAll(type)` | Retrieve components assignable to the requested class or interface |
| `getComponents(applicationClass)` | Return component classes in creation order |
| `attach(application, componentClassList)` | Add components to an initialized application |
| `detach(application, componentClassList)` | Remove selected components and affected dependents |

```java
final OrderService orderService = Injector.INSTANCE.get(OrderService.class);
final List<Feature> featureList = Injector.INSTANCE.getAll(Feature.class);

Injector.INSTANCE.attach(shopApplication, List.of(MetricsService.class));
Injector.INSTANCE.detach(shopApplication, List.of(MetricsService.class));
```

The attach/detach example assumes those application and component types exist. Detaching can remove components that depend on the selected types, including components owned by other applications; see the [runtime guide](../Injector-Core/README.md#application-lifecycle).

## Lifecycle and callbacks

| Interface | Method | When it runs |
| --- | --- | --- |
| `Lifecycle` | `onComponentInitialize()` | After application component construction, in creation order |
| `Lifecycle` | `onComponentShutdown()` | During removal, with dependents removed before their dependencies |
| `Lifecycle` | `onLiveDependencyUpdate()` | After container changes, to rebuild derived component indexes |
| `ApplicationCallback` | `getComponentSorter()` | Orders otherwise independent component classes; defaults to class name |
| `ApplicationCallback` | `onComponentRegister(component)` | After that component's initialization callback |
| `ApplicationCallback` | `onComponentUnregister(component)` | Before that component's shutdown callback |
| `ApplicationCallback` | `onApplicationFailure(throwable)` | Reports a failure in automatically started deferred applications |

All callback methods have defaults. Implement `ApplicationCallback` on the application class. Extension-specific application callbacks are documented in the [extensions overview](../Injector-Extensions/README.md).

## Extension contract

An extension can recognize component classes, supply instances, and observe component and application lifecycle events. Register the implementation in `META-INF/services/me.trae.foundation.injector.api.extension.Extension`. The file contains its fully qualified class name.

See [writing an extension](../Injector-Extensions/README.md#writing-an-extension) for a complete SPI example and hook ordering.

## Exceptions

All API exceptions extend unchecked `InjectorException`.

| Exception | Meaning |
| --- | --- |
| `ImplementationNotFoundException` | No Injector service implementation is available |
| `ApplicationNotAnnotatedException` | The application lacks `@Application` |
| `ApplicationAlreadyInitializedException` | The application class is running or waiting |
| `ApplicationNotInitializedException` | An operation requires a running application |
| `MissingDependencyException` | A requested dependency cannot be resolved |
| `AmbiguousDependencyException` | More than one candidate matches |
| `CircularDependencyException` | Creation dependencies form a cycle |
| `ConstructorException` | A normally constructed component does not have exactly one constructor |
| `UnsupportedDependencyTypeException` | An injection parameter uses an unsupported collection shape |
| `DuplicateComponentException` | A component registration conflicts with an existing one |
| `ComponentCreationException` | Component construction or a provider fails |
| `ScanException` | Component scanning fails |

## Installation and build

```xml
<dependency>
    <groupId>me.trae.foundation.injector</groupId>
    <artifactId>Injector-API</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<dependency>
    <groupId>me.trae.foundation.injector</groupId>
    <artifactId>Injector-Core</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>runtime</scope>
</dependency>
```

The API contains no runtime implementation. Accessing `Injector.INSTANCE` requires Core on the runtime classpath. Install the modules locally and run their tests from the repository root with JDK 25 and Maven:

```shell
mvn -pl Injector/Injector-API,Injector/Injector-Core -am install
```

Return to the [Injector guide](../README.md) or [Foundation](../../README.md).
