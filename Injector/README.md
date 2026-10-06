# Injector

A lightweight dependency injection framework for Java 25, built from scratch with no Spring or Guice underneath. Components are plain classes wired through their constructor, grouped into applications that start and stop as a unit, and extended through a small SPI that the Configuration, Scheduler and Commons modules are built on.

Any Java application, service or tool can use it.

## Modules

| Module | Artifact | Purpose |
|---|---|---|
| [Injector-API](#injector-api) | `me.trae.foundation.injector:Injector-API` | Annotations, contracts, callbacks, the extension SPI and every exception. This is what your code compiles against. |
| [Injector-Core](#injector-core) | `me.trae.foundation.injector:Injector-Core` | The implementation, found at runtime through `ServiceLoader`. |
| [Injector-Commons-Extension](#injector-commons-extension) | `me.trae.foundation.injector.extensions:Injector-Commons-Extension` | `@PostConstruct`, `@ApplicationReady`, `@PreDestroy` and `@PostDestroy` hooks. |
| [Injector-Scheduler-Extension](#injector-scheduler-extension) | `me.trae.foundation.injector.extensions:Injector-Scheduler-Extension` | `@Scheduler` repeating tasks with pluggable executors. |
| [Injector-Configuration-Extension](#injector-configuration-extension) | `me.trae.foundation.injector.extensions:Injector-Configuration-Extension` | `@Configuration` classes loaded from JSON or YAML files, with comments, reloading and saving. |

Extensions are optional. Add the ones you want to the classpath and Core picks them up on its own.

## Installation

Requires JDK 25 and Maven. Build and install Foundation from the repository root:

```
mvn clean install
```

Then depend on the API, with Core at `runtime` scope so your code only ever touches the API:

```xml
<!-- Injector API -->
<dependency>
    <groupId>me.trae.foundation.injector</groupId>
    <artifactId>Injector-API</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- Injector Core -->
<dependency>
    <groupId>me.trae.foundation.injector</groupId>
    <artifactId>Injector-Core</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>runtime</scope>
</dependency>

<!-- Any extensions you need, for example -->
<dependency>
    <groupId>me.trae.foundation.injector.extensions</groupId>
    <artifactId>Injector-Configuration-Extension</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Quick start

```java
@Application
public final class ShopApplication {

    public static void main(final String[] args) {
        final ShopApplication application = new ShopApplication();

        Injector.INSTANCE.initialize(application);

        Injector.INSTANCE.get(OrderService.class).placeOrder("alice");

        Injector.INSTANCE.shutdown(application);
    }
}

@Singleton
public final class OrderRepository {
}

@AllArgsConstructor
@Singleton
public final class OrderService {

    private final OrderRepository orderRepository;

    public void placeOrder(final String customer) {
    }
}
```

`initialize` scans the application's package, creates every `@Singleton` it finds in dependency order, and runs their lifecycle. `shutdown` tears them down in reverse.

## Injector-API

### Applications

An application is any object whose class is annotated with `@Application`. It owns the components created for it, and it is registered as a component itself, so components can take it in their constructor.

```java
Injector.INSTANCE.initialize(application);
Injector.INSTANCE.initialize(application, List.of(ExtraComponent.class));
Injector.INSTANCE.shutdown(application);
```

- **Scanning:** the application's package and every package below it are scanned for non abstract classes marked `@Singleton`, or claimed by an extension (such as `@Configuration`). Scanning works from a directory or from inside a jar.
- **Extra components:** classes passed to `initialize` are created as components even without an annotation. This is how libraries hand their own classes to an application without being scanned.
- **Dependencies between applications:** `@Application(dependencies = BillingApplication.class)` defers the application until every listed application has started. It starts on its own the moment the last one does. Shutting down an application first shuts down every application that depends on it, and shutting down a deferred application simply cancels it.
- **One at a time:** initializing an application class that is already running or waiting throws `ApplicationAlreadyInitializedException`, and a class without `@Application` throws `ApplicationNotAnnotatedException`.
- **Rollback:** if anything fails while an application starts, everything created so far is shut down again before the exception is rethrown.

### Components

```java
@Singleton
public final class InvoiceService {

    public InvoiceService(final InvoiceRepository invoiceRepository, final Injector injector) {
    }
}
```

- **Constructor injection only.** Every component declares exactly one constructor, otherwise `ConstructorException` is thrown. There is no field injection.
- **One instance.** Every component is a singleton, created once and shared by everyone that depends on it.
- **Resolving a parameter:** an exact class match wins. Otherwise the parameter may be an interface or superclass, and it resolves to the single component assignable to it. No match throws `MissingDependencyException`, more than one throws `AmbiguousDependencyException`.
- **Across applications:** resolution is global, so a component in `ReportingApplication` can inject a component owned by `BillingApplication`.
- **Built in:** the `Injector` itself, every running application and every loaded extension are components too.
- **Ordering:** dependencies are always created first. Components with no dependency on each other are created in the order chosen by the application's sorter, which defaults to class name order (see [ApplicationCallback](#applicationcallback)).
- **Cycles** throw `CircularDependencyException` with the full chain, for example `A -> B -> A`.

### Live collections

A constructor can ask for every component of a type:

```java
@Singleton
public final class FeatureRegistry {

    public FeatureRegistry(final List<Feature> featureList, final Set<Feature> featureSet, final Map<Class<? extends Feature>, Feature> featureMap) {
    }
}
```

`List<T>`, `Set<T>` and `Map<Class<? extends T>, T>` are live views of the container, not copies. When another application starts later and registers more `Feature` components, they appear in the collection straight away, and they disappear again when that application shuts down. Iterating while an application starts or stops is safe. Any other collection type throws `UnsupportedDependencyTypeException`.

### Providers

A method annotated with `@Provider` on a component registers its return value as a component. Its parameters are injected like a constructor's.

```java
@Singleton
public final class SerializationModule {

    @Provider
    public Gson gson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }
}
```

The provided value is registered under the method's return type. A `void` return or a `null` result throws `ComponentCreationException`. Anything that depends on the provided type causes its owning component to be created first.

### @DependsOn

```java
@Singleton
@DependsOn(SerializationModule.class)
public final class AccountManager {
}
```

Forces the listed components to be created first, even when the constructor does not need them. It is read through superclasses and interfaces, so `@DependsOn` on a shared base class or interface applies to every implementation. A listed component that cannot be found throws `MissingDependencyException`.

### Lifecycle

```java
@Singleton
public final class ConnectionPool implements Lifecycle {

    @Override
    public void onComponentInitialize() {
    }

    @Override
    public void onComponentShutdown() {
    }
}
```

`onComponentInitialize` runs once every component of the application has been created, in creation order. `onComponentShutdown` runs in reverse order. Both are optional defaults.

### ApplicationCallback

An application class can implement `ApplicationCallback` to plug into the container:

| Method | Default | Used for |
|---|---|---|
| `getComponentSorter()` | Sort by class name | The order of components that do not depend on each other |
| `onComponentRegister(component)` | Nothing | Called for every component as it initializes, for example to register event listeners |
| `onComponentUnregister(component)` | Nothing | Called for every component as it shuts down |
| `onApplicationFailure(throwable)` | Uncaught exception handler | Failures of a deferred application that started on its own |

Extensions add their own callbacks the same way: `SchedulerCallback` and `ConfigurationCallback` below are both implemented by the application class.

### Looking up components

```java
final OrderService orderService = Injector.INSTANCE.get(OrderService.class);
final List<Feature> featureList = Injector.INSTANCE.getAll(Feature.class);
final List<Class<?>> componentList = Injector.INSTANCE.getComponents(ShopApplication.class);
```

- `get` returns the component registered under that exact class.
- `getAll` returns every component assignable to a class or interface.
- `getComponents` returns an application's component classes in creation order.

### Attaching and detaching

```java
Injector.INSTANCE.attach(application, List.of(MetricsService.class, MetricsExporter.class));
Injector.INSTANCE.detach(application, List.of(MetricsService.class, MetricsExporter.class));
```

`attach` builds extra components inside an application that is already running, with full injection, lifecycle, callbacks and extension hooks. `detach` shuts down only those components, in reverse order, and removes them. If an attach fails part way, whatever it created is detached again before the exception is rethrown.

### Extensions

An extension implements `Extension` and is registered in `META-INF/services/me.trae.foundation.injector.api.extension.Extension`. Every method is optional:

| Hook | Called when |
|---|---|
| `isComponent(type)` | Scanning, to claim classes that are not `@Singleton` |
| `instantiate(...)` | Creating a component, to build it instead of calling its constructor |
| `onComponentCreate(...)` | Right after each component is created |
| `onApplicationInitialize(...)` | After every component of an application has initialized |
| `onComponentShutdown(...)` | Before each component shuts down |
| `onApplicationShutdown(...)` | After an application has shut down |
| `onComponentsAttach(...)` / `onComponentsDetach(...)` | Components are attached to or detached from a running application |

Extensions are components themselves, so a component can inject an extension directly, for example `ConfigurationExtension` to reload configurations.

### Exceptions

Every exception extends `InjectorException`, an unchecked exception.

| Exception | Thrown when |
|---|---|
| `ImplementationNotFoundException` | No Injector implementation is on the classpath |
| `ApplicationNotAnnotatedException` | The application class has no `@Application` |
| `ApplicationAlreadyInitializedException` | The application is already running or waiting |
| `ApplicationNotInitializedException` | The application is not running |
| `ConstructorException` | A component does not have exactly one constructor |
| `MissingDependencyException` | A dependency has no matching component |
| `AmbiguousDependencyException` | A dependency matches more than one component |
| `CircularDependencyException` | Components depend on each other in a loop |
| `UnsupportedDependencyTypeException` | A constructor asks for an unsupported collection |
| `DuplicateComponentException` | A class is registered twice |
| `ComponentCreationException` | A constructor or `@Provider` method throws or returns nothing |
| `ScanException` | The application's package cannot be scanned |

## Injector-Core

The implementation behind `Injector.INSTANCE`, loaded through `ServiceLoader` from `META-INF/services/me.trae.foundation.injector.api.Injector`. Keep it at `runtime` scope.

When an application starts, Core:

1. Registers the application as a component.
2. Scans its package and merges the result with any extra component classes.
3. Sorts them with the application's sorter.
4. Creates each one, creating `@DependsOn` targets, constructor dependencies and providers first, and detecting cycles along the way.
5. Calls `onComponentInitialize` and `onComponentRegister` on every component, then lets extensions run their application hooks.
6. Starts any deferred applications that were waiting on this one.

Shutdown runs the same steps backwards, after first shutting down dependent applications. `initialize`, `shutdown`, `attach` and `detach` are synchronized, and lookups are safe from any thread.

There is one Injector per classloader. Applications that share it share one container, which is what lets them inject each other's components.

## Injector-Commons-Extension

Annotation based lifecycle hooks, for when implementing `Lifecycle` is not enough:

```java
@Singleton
public final class CacheWarmer {

    @PostConstruct
    private void onCreate() {
    }

    @ApplicationReady
    private void onReady() {
    }

    @PreDestroy
    private void onStop() {
    }

    @PostDestroy
    private void onGone() {
    }
}
```

| Annotation | Runs |
|---|---|
| `@PostConstruct` | Right after the component is created |
| `@ApplicationReady` | Once the whole application has initialized, or right after the component is attached |
| `@PreDestroy` | Before the component shuts down |
| `@PostDestroy` | After the whole application has shut down, in reverse creation order, or when the component is detached |

Annotated methods may be private and may be declared on a superclass. A method overridden in a subclass only runs once. Methods must take no parameters, and a hook that fails throws `LifecycleException` with the original cause.

## Injector-Scheduler-Extension

Runs component methods on a fixed schedule:

```java
@Singleton
public final class ReportService {

    @Scheduler(period = 30, unit = TimeUnit.SECONDS, asynchronous = true)
    private void refresh() {
    }

    @Scheduler(period = 1, unit = TimeUnit.HOURS, clock = true)
    private void summarize() {
    }
}
```

| Attribute | Default | Meaning |
|---|---|---|
| `period` | Required | Time between runs, must be positive |
| `unit` | Required | `TimeUnit` for `period` and `initialDelay` |
| `initialDelay` | One full period | Delay before the first run |
| `clock` | `false` | Align runs to the wall clock, so an hourly task runs on the hour |
| `asynchronous` | `false` | Run on the asynchronous executor instead of the synchronous one |

Tasks start once their application has initialized, or when their component is attached, and are cancelled when the component shuts down. One daemon thread keeps time and hands each run to an executor from `SchedulerCallback`, which the application class can implement:

| Method | Default |
|---|---|
| `getSynchronousExecutor()` | Runs inline on the scheduler thread |
| `getAsynchronousExecutor()` | A new virtual thread per run |

A desktop or game application, for example, can point the synchronous executor at its main thread. A run that throws is reported to the thread's uncaught exception handler and the task keeps running. Scheduled methods must take no parameters, otherwise `SchedulerException` is thrown at startup.

## Injector-Configuration-Extension

Turns plain classes into configuration files:

```java
@NoArgsConstructor
@Getter
@Setter
@Configuration(value = "general", type = ConfigType.JSON)
public final class GeneralConfig {

    @Comment("Greeting shown on the home page")
    private String greeting = "Welcome";

    private int maxConnections = 100;

    private Limits limits = new Limits();

    @NoArgsConstructor
    @Getter
    @Setter
    public static final class Limits {

        @Comment("Requests allowed per minute")
        private int requestsPerMinute = 60;
    }
}

@NoArgsConstructor
@Getter
@Setter
@Configuration(value = "database", type = ConfigType.YAML)
public final class DatabaseConfig {

    @Comment("PostgreSQL host")
    private String host = "localhost";

    private int port = 5432;
}
```

Configuration classes are found by scanning just like `@Singleton`s and can be injected anywhere:

```java
@Singleton
public final class GreetingService {

    public GreetingService(final GeneralConfig generalConfig) {
    }
}
```

- **Files:** `general.json` and `database.yml` are created in the data folder, named by `value()`, with `@Comment` lines written as `//` in JSON and `#` in YAML, including on nested sections.
- **Defaults:** field initializers are the defaults. A missing file is created from them, and values missing from an existing file keep their defaults. The file is rewritten on every load, so new fields appear in old files.
- **Shape:** a class needs a no args constructor. Every non static, non transient field is a value, including fields from superclasses, nested classes, enums, lists, maps and arrays.
- **Reloading:** a reload reads the file into the existing instance, so every component holding the configuration sees the new values without being recreated.

`ConfigurationExtension` is injectable and controls the files at runtime:

```java
configurationExtension.reloadAllConfigurations();

configurationExtension.reloadConfigurations(ReportingApplication.class);

configurationExtension.reloadConfiguration(GeneralConfig.class);

configurationExtension.saveConfiguration(GeneralConfig.class);
```

The application class can implement `ConfigurationCallback` to choose where files live and to hear about changes:

| Method | Default |
|---|---|
| `getDataFolder()` | The working directory |
| `onConfigurationReload(type)` | Nothing, called after each configuration is reloaded |
| `onConfigurationSave(type)` | Nothing, called after each configuration is saved |

Problems reading or writing a file throw `ConfigurationException`.

## Building and testing

From the repository root:

```
mvn -f Injector/pom.xml test
```

The tests need no external services.
