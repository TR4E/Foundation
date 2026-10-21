<h1 align="center">Minecraft-Velocity-Plugin-Framework</h1>

<p align="center">Velocity proxy plugin framework with constructor injection, Brigadier commands, and automatic listener registration.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/software/velocity"><img alt="Velocity" src="https://img.shields.io/badge/Velocity-4.2.0-44cc11?logo=minecraft&logoColor=white"></a>
</p>

Minecraft-Velocity-Plugin-Framework connects Velocity's proxy lifecycle with Foundation's [Injector](../../../Injector/README.md). A plugin extends `VelocityPlugin`, and the framework scans for components, registers event listeners with Velocity's event manager, wraps commands as Brigadier commands, wires dependencies through constructors, and manages startup and shutdown. Injector extensions for scheduling, configuration, and lifecycle annotations work without extra setup.

## Contents

<details>
<summary>On this page</summary>

- [Quick start](#quick-start)
- [VelocityPlugin base class](#velocityplugin-base-class)
- [Command system](#command-system)
- [Event system](#event-system)
- [Utilities](#utilities)
- [Installation](#installation)

</details>

## Quick start

```java
@Plugin(id = "my-proxy-plugin")
public class MyProxyPlugin extends VelocityPlugin {

    @Inject
    public MyProxyPlugin(final ProxyServer proxyServer, @DataDirectory final Path dataDirectory) {
        super(proxyServer, dataDirectory);
    }
}

@Singleton
public class PingCommand extends BaseCommand<MyProxyPlugin, Player> {

    public PingCommand() {
        super("ping", "Check latency", List.of(), "myproxy.ping");
    }

    @Override
    public void execute(final Player sender, final String[] args) {
        UtilMessage.message(sender, "Proxy", "Pong!");
    }

    @Override
    public List<String> getTabComplete(final Player sender, final String[] args) {
        return Suggestions.players(args[0]);
    }
}

@Singleton
public class JoinListener implements Listener {

    @Subscribe
    public void onConnect(final PostLoginEvent event) {
        UtilMessage.message(event.getPlayer(), "Proxy", "Welcome to the network!");
    }
}
```

Place public types in separate files under the plugin's package. Components use the standard `@Singleton` annotation and are discovered by Injector's package scan. Examples omit imports and may use Lombok.

## VelocityPlugin base class

`VelocityPlugin` implements `ApplicationCallback`, `SchedulerCallback`, and `ConfigurationCallback`. It manages the full plugin lifecycle:

**Startup (`onProxyInitialize`):**
1. Registers the plugin with `PluginRegistry`.
2. Initializes Injector with `ProxyServerProvider`, which makes `ProxyServer` injectable by any component.
3. During initialization, components implementing `Listener` are registered with Velocity's event manager, and commands extending `AbstractCommand` are registered with the command manager.
4. Dispatches `PluginInitializeEvent`.

**Shutdown (`onProxyShutdown`):**
1. Dispatches `PluginShutdownEvent`.
2. Unregisters the plugin and shuts down Injector. Listeners and commands are cleaned up.

**Executors:** the synchronous executor runs tasks directly on the calling thread. The asynchronous executor uses Velocity's scheduler via `ProxyServer.getScheduler()`. Both are used by the Injector Scheduler extension for `@Scheduler` methods.

**Configuration callbacks:** `onConfigurationSave` and `onConfigurationReload` dispatch `ConfigSaveEvent` and `ConfigReloadEvent`.

**Plugin discovery:** `PluginRegistry` tracks framework plugins using a `ClassLoader`-based lookup. `getSelfPlugin()` uses `StackWalker` to identify the calling plugin by walking the call stack and matching the first non-framework class's class loader to a registered plugin.

## Command system

### AbstractCommand

The command system mirrors the Paper framework's design. `AbstractCommand<Parent, Sender>` is the generic base. The `Parent` type links commands into a hierarchy, and the `Sender` type restricts which `CommandSource` types can execute the command.

```java
public class AdminCommand extends BaseCommand<MyProxyPlugin, CommandSource> {

    public AdminCommand() {
        super("admin", "Admin commands", List.of(), "myproxy.admin");
    }

    @Override
    public void execute(final CommandSource sender, final String[] args) {
        UtilMessage.message(sender, "Admin", "Use a subcommand.");
    }
}

public class AdminReloadCommand extends BaseCommand<AdminCommand, CommandSource> {

    public AdminReloadCommand() {
        super("reload", "Reload configuration", List.of("rl"));
    }

    @Override
    public void execute(final CommandSource sender, final String[] args) {
        UtilMessage.message(sender, "Admin", "Reloaded.");
    }
}
```

Parent commands are sorted before children using `AbstractCommand.COMPARATOR_ORDER`. Child commands without a permission string inherit from their parent `BaseCommand`.

### BaseCommand

`BaseCommand` adds permission checking through `UtilPermission`. Access is granted when the permission is `null`, the sender has `*`, or the sender has the specific permission node.

### Command events

`CommandExecuteEvent` fires before execution and can be cancelled via `ResultedEvent<GenericResult>`. `CommandTabCompleteEvent` fires during tab completion and is also cancellable.

### BrigadierCommandWrapper

Root commands are wrapped as `BrigadierCommand` instances. The wrapper handles recursive child command resolution, sender type validation, permission checking, event dispatch, and tab completion with proper `StringRange` calculation for Brigadier's suggestion system.

### Suggestions

`Suggestions` provides the same helpers as the Paper framework:

| Method | Suggests |
| --- | --- |
| `childCommands(command, sender, arg)` | Valid child commands for the sender |
| `players(arg)` | Online player names |
| `players(predicate, arg)` | Filtered online player names |
| `internalPlugins(arg)` | Registered framework plugins |

## Event system

### Marker interfaces

`Event` marks Velocity event types (annotated with `@AwaitingEvent`). `Listener` marks event listener components for automatic registration.

### Event priority

`EventPriority` defines constants matching Velocity's short-based priority system:

| Constant | Value | Order |
| --- | --- | --- |
| `MONITOR` | `Short.MIN_VALUE` | First (observe only) |
| `HIGHEST` | `-32767` | |
| `HIGH` | `-32766` | |
| `NORMAL` | `0` | Default |
| `LOW` | `32765` | |
| `LOWEST` | `32766` | |
| `BASELINE` | `Short.MAX_VALUE` | Last |

### Custom events

| Class | Purpose |
| --- | --- |
| `CustomEvent` | Base class implementing `Event` |
| `CustomCancellableEvent` | Implements `ResultedEvent<GenericResult>` for cancellation |

### Framework events

| Event | Dispatched when |
| --- | --- |
| `PluginInitializeEvent` | After the plugin has fully initialized |
| `PluginShutdownEvent` | Before the plugin begins shutdown |
| `ConfigSaveEvent` | After a configuration class is saved |
| `ConfigReloadEvent` | After a configuration class is reloaded |

### UtilEvent

```java
UtilEvent.dispatch(event);

final CompletableFuture<MyEvent> future = UtilEvent.supply(event);
```

`dispatch` fires and forgets through `EventManager.fireAndForget`. `supply` returns a `CompletableFuture` through `EventManager.fire`. Both have overloads that accept a specific plugin or use `PluginRegistry.getSelfPlugin()`.

## Utilities

| Class | Purpose |
| --- | --- |
| `UtilColor` | Extends `BaseUtilColor` from Minecraft-Common |
| `UtilEvent` | Event dispatching with fire-and-forget and future variants |
| `UtilMessage` | Extends `BaseUtilMessage` with `broadcast` and `log` methods using `ProxyServer` |
| `UtilPermission` | Permission checking with wildcard and custom wildcard support |
| `UtilPlayer` | Extracts IP address from a player's remote address |
| `UtilServer` | Online player lookups by UUID or name through `ProxyServer` |
| `UtilTask` | Task scheduling through Velocity's scheduler with `ChronoUnit` time units |
| `CommandRegistry` | Registers and unregisters root and child commands with Velocity's command manager |
| `PluginRegistry` | Tracks framework plugins by name, class, and class loader for cross-plugin discovery |

### UtilTask

```java
UtilTask.executeAsynchronous(() -> cleanup());

UtilTask.executeLaterAsynchronous(3, ChronoUnit.SECONDS, () -> delayed());

UtilTask.scheduleAsynchronous(plugin, 0, 5, ChronoUnit.SECONDS, () -> tick(), () -> shouldCancel);
```

Velocity does not have a primary thread, so all scheduled tasks are asynchronous. The `schedule` variants accept an optional `Supplier<Boolean>` that cancels the task when it returns `true`.

### UtilMessage

```java
UtilMessage.broadcast("Proxy", "Server restarting in 10 seconds");

UtilMessage.broadcast(prefix, message, ignoredUuids);

UtilMessage.log("Proxy", "Player connected");
```

Broadcast sends to all connected players via `ProxyServer.getAllPlayers()`. Log sends to `ProxyServer.getConsoleCommandSource()`.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.velocity</groupId>
    <artifactId>Minecraft-Velocity-Plugin-Framework</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

The framework transitively includes Utilities, Injector-API, all three Injector extensions, and Minecraft-Common. Injector-Core is a `runtime` dependency. Velocity API is `provided`. Return to the [Minecraft README](../../README.md).
