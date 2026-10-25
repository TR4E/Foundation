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
- [Search engines](#search-engines)
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

    @Override
    public String getPluginName() {
        return "MyProxyPlugin";
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

Implement `getPluginName()` to supply the name used by `VelocityInternalPluginSearchEngine` and `PluginRegistry.searchInternalPlugin(...)`. The registry's direct `getInternalPluginByName(...)` lookup uses the plugin class's simple name.

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

## Search engines

`AbstractVelocitySearchEngine<Type>` extends the [Minecraft search engine](../../Minecraft-Common/README.md#search-engines) with Velocity's `CommandSource` as the receiver. It supplies colored feedback for missing or ambiguous results and leaves candidate collection and matching rules to the implementation. The shared [search contract](../../../Utilities/README.md#search-engines) describes exact-match priority, filtering, and the display limit.

### Built-in engines

These implementations live in `me.trae.foundation.minecraft.velocity.plugin.framework.search.types`. Both have no-argument constructors and use case-insensitive equality for exact matches and case-insensitive containment for partial matches.

| Engine | Candidates | Match value and displayed name |
| --- | --- | --- |
| [VelocityOnlinePlayerSearchEngine](src/main/java/me/trae/foundation/minecraft/velocity/plugin/framework/search/types/VelocityOnlinePlayerSearchEngine.java) | Current online players on the proxy | Player username |
| [VelocityInternalPluginSearchEngine](src/main/java/me/trae/foundation/minecraft/velocity/plugin/framework/search/types/VelocityInternalPluginSearchEngine.java) | Foundation `VelocityPlugin` instances in `PluginRegistry` | Plugin name |

Internal plugin search covers Foundation plugins registered with the framework.

### Search helpers

Utilities expose reusable engines for the built-in search types. Each helper accepts `(commandSource, input, inform)` and an overload with a final `Predicate<Type>` argument. Omitting the predicate includes all candidates.

| Helper | Returns | Engine |
| --- | --- | --- |
| `UtilServer.searchOnlinePlayer(...)` | `Optional<Player>` | `VelocityOnlinePlayerSearchEngine` |
| `PluginRegistry.searchInternalPlugin(...)` | `Optional<VelocityPlugin>` | `VelocityInternalPluginSearchEngine` |

`UtilServer` belongs to the framework's `utility` package; `PluginRegistry` belongs to `utility.registry`.

```java
UtilServer.searchOnlinePlayer(commandSource, input, true)
        .ifPresent(player -> UtilMessage.message(commandSource, "Player", player.getUsername()));
```

Filter before both exact and partial matching, for example when a command accepts only staff:

```java
final Optional<Player> playerOptional = UtilServer.searchOnlinePlayer(
        commandSource,
        input,
        true,
        player -> player.hasPermission("myplugin.staff")
);
```

These helpers have the same result and feedback behaviour as calling an engine's `find(...)` directly. Direct UUID and name getters remain available for lookups that do not need partial matching.

### Using an engine

Inside a command or service, pass the source, input, whether to report an unresolved search, and an optional candidate predicate. These examples omit imports, as do the other framework examples:

```java
final VelocityOnlinePlayerSearchEngine velocityOnlinePlayerSearchEngine = new VelocityOnlinePlayerSearchEngine();

velocityOnlinePlayerSearchEngine.find(commandSource, input, true, null)
        .ifPresent(player -> UtilMessage.message(commandSource, "Player", player.getUsername()));
```

Set `inform` to `false` for a silent lookup. `Optional.empty()` can mean either no match or multiple partial matches. Reuse an engine as a field when searching repeatedly; its supplier obtains current candidates each time.

A predicate restricts both exact and partial matches. For example, search only players with a particular permission:

```java
final Optional<Player> playerOptional = velocityOnlinePlayerSearchEngine.find(
        commandSource,
        input,
        true,
        player -> player.hasPermission("myplugin.staff")
);
```

Here `Player` is `com.velocitypowered.api.proxy.Player` and `CommandSource` is `com.velocitypowered.api.command.CommandSource`.

### Creating an engine

The existing `VelocityOnlinePlayerSearchEngine` shows the implementation pattern. Extend `AbstractVelocitySearchEngine<Player>`, supply the current collection, and define the display name and two matching checks:

```java
public final class VelocityOnlinePlayerSearchEngine extends AbstractVelocitySearchEngine<Player> {

    public VelocityOnlinePlayerSearchEngine() {
        super("Online Player Search", UtilServer::getOnlinePlayers);
    }

    @Override
    protected String getMatchName(final Player player, final CommandSource commandSource) {
        return player.getUsername();
    }

    @Override
    protected boolean isExact(final Player player, final String result) {
        return player.getUsername().equalsIgnoreCase(result);
    }

    @Override
    protected boolean isMatching(final Player player, final String result) {
        return player.getUsername().toLowerCase(Locale.ROOT).contains(result.toLowerCase(Locale.ROOT));
    }
}
```

`UtilServer` is the Velocity framework utility. For a new search type, replace `Player`, the supplier, and the matching methods with your own candidate type and rules. Override `getTypeColor(type, commandSource)` to color individual matches, `getInputColor()` to change the input color, or `updateList(resultList, commandSource, input)` to adjust partial results. Exact matches return before that list hook runs.

The supplied engines are ordinary classes without component annotations. Construct them directly, or annotate your own engine with Injector's `@Singleton` when it should be a plugin component.

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
| `UtilServer` | Online player lookups by UUID or name through `ProxyServer`, plus exact and partial username searches |
| `UtilTask` | Task scheduling through Velocity's scheduler with `ChronoUnit` time units |
| `CommandRegistry` | Registers and unregisters root and child commands with Velocity's command manager |
| `PluginRegistry` | Tracks framework plugins by name, class, and class loader for cross-plugin discovery; searches their `getPluginName()` values |

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
