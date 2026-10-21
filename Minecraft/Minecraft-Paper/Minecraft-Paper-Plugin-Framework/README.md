<h1 align="center">Minecraft-Paper-Plugin-Framework</h1>

<p align="center">Paper plugin framework with constructor injection, hierarchical commands, addon hosting, and automatic listener registration.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper" src="https://img.shields.io/badge/Paper-1.21.11-44cc11?logo=minecraft&logoColor=white"></a>
</p>

Minecraft-Paper-Plugin-Framework connects Paper's plugin lifecycle with Foundation's [Injector](../../../Injector/README.md). A plugin extends `PaperPlugin`, and the framework scans for components, registers Bukkit listeners and commands, wires dependencies through constructors, and manages startup and shutdown. Injector extensions for scheduling, configuration, and lifecycle annotations work without extra setup. An addon system lets multiple plugins share optional features through Paper's `ServicesManager`, with automatic election and failover.

## Contents

<details>
<summary>On this page</summary>

- [Quick start](#quick-start)
- [PaperPlugin base class](#paperplugin-base-class)
- [Addon system](#addon-system)
- [Command system](#command-system)
- [Event system](#event-system)
- [Display names](#display-names)
- [Stash classes](#stash-classes)
- [Utilities](#utilities)
- [Installation](#installation)

</details>

## Quick start

```java
@Addons({ItemAddon.class})
public class MyPlugin extends PaperPlugin {
}

@Singleton
public class JoinListener implements Listener {

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        UtilMessage.message(event.getPlayer(), "Server", "Welcome!");
    }
}

@Singleton
public class GreetCommand extends BaseCommand<MyPlugin, Player> {

    public GreetCommand() {
        super("greet", "Greet a player", List.of("hi"), "myplugin.greet");
    }

    @Override
    public void execute(final Player sender, final String[] args) {
        UtilMessage.message(sender, "Server", "Hello!");
    }

    @Override
    public List<String> getTabComplete(final Player sender, final String[] args) {
        return Suggestions.players(args[0]);
    }
}
```

Place public types in separate files under the plugin's package. Plugin components use the `@Singleton` annotation. `@AddonSingleton` is reserved for internal addon framework code only. Examples omit imports and may use Lombok.

## PaperPlugin base class

`PaperPlugin` extends `JavaPlugin` and implements `ApplicationCallback`, `SchedulerCallback`, and `ConfigurationCallback`. It manages the full plugin lifecycle:

**Startup (`onEnable`):**
1. Registers the plugin with `PluginRegistry`.
2. Initializes the addon registry, which scans `@Addons` for addon classes, discovers `@AddonSingleton` components in each addon's package, elects addons through Paper's `ServicesManager`, and returns the component classes of elected addons.
3. Initializes Injector with the addon components. During initialization, components implementing `Listener` are registered with Bukkit's plugin manager, and commands extending `AbstractCommand` are registered with the command map.
4. Starts the addon registry and dispatches `PluginInitializeEvent`.

**Shutdown (`onDisable`):**
1. Dispatches `PluginShutdownEvent`.
2. Shuts down the addon registry, detaching addon components.
3. Unregisters the plugin and shuts down Injector. Listeners are unregistered from `HandlerList` and commands are removed from the command map.

**Executors:** the synchronous executor runs tasks on Bukkit's primary thread using `BukkitScheduler.runTask`. The asynchronous executor uses `BukkitScheduler.runTaskAsynchronously`. Both are used by the Injector Scheduler extension for `@Scheduler` methods.

**Configuration callbacks:** `onConfigurationSave` and `onConfigurationReload` dispatch `ConfigSaveEvent` and `ConfigReloadEvent` so listeners can react to configuration changes.

**Failure handling:** if initialization fails, the error is logged and the plugin is disabled.

## Addon system

Addons let multiple plugins declare the same feature, with only one hosting it at a time. This is useful for shared features that should be active once regardless of how many plugins include them.

### Declaring addons

Annotate the plugin class with `@Addons` and list the addon classes:

```java
@Addons({ItemAddon.class, RecipeAddon.class})
public class MyPlugin extends PaperPlugin {
}
```

Each addon implements `Addon` and returns a unique key:

```java
public class ItemAddon implements Addon {

    @Override
    public String getKey() {
        return "item";
    }
}
```

### Addon components

Classes in the addon's package annotated with `@AddonSingleton` are discovered by reflection and managed as Injector components:

```java
@AddonSingleton
public class ItemService implements Lifecycle {

    @Override
    public void onComponentInitialize() {
    }
}
```

### Election

The addon registry registers each addon key as a service in Paper's `ServicesManager`. The first plugin to register a key hosts the addon. When a hosting plugin is disabled, the next plugin that declared the same addon automatically takes over: `AddonListener` listens for `ServiceUnregisterEvent` and calls `elect()` to attach the newly elected addon's components to Injector.

## Command system

### AbstractCommand

`AbstractCommand<Parent, Sender>` is the generic base for all commands. The `Parent` type parameter links a command to its parent (the plugin class for root commands, or another command for subcommands). The `Sender` type parameter restricts which `CommandSender` types can execute the command.

```java
public class AdminCommand extends BaseCommand<MyPlugin, Player> {

    public AdminCommand() {
        super("admin", "Admin commands", List.of(), "myplugin.admin");
    }

    @Override
    public void execute(final Player sender, final String[] args) {
        UtilMessage.message(sender, "Admin", "Use a subcommand.");
    }
}

public class AdminReloadCommand extends BaseCommand<AdminCommand, CommandSender> {

    public AdminReloadCommand() {
        super("reload", "Reload configuration", List.of("rl"));
    }

    @Override
    public void execute(final CommandSender sender, final String[] args) {
        UtilMessage.message(sender, "Admin", "Reloaded.");
    }
}
```

The framework sorts commands by hierarchy depth so parent commands are always registered before their children. Child commands are added to the parent's `childCommandMap` and resolved recursively at execution time.

### BaseCommand

`BaseCommand` extends `AbstractCommand` with a permission string. A child command that omits the permission inherits its parent's. Permission checking uses `UtilPermission`, which grants access when the permission is `null`, the sender is OP, or the sender has the `*` wildcard.

### Command events

`CommandExecuteEvent` fires before execution and is cancellable. `CommandTabCompleteEvent` fires during tab completion and is cancellable. Both carry the resolved command and the sender.

### Suggestions

`Suggestions` provides common tab completion helpers:

| Method | Suggests |
| --- | --- |
| `childCommands(command, sender, arg)` | Valid child commands for the sender |
| `players(arg)` | Online player names |
| `players(predicate, arg)` | Filtered online player names |
| `worlds(arg)` | Loaded world names |
| `materials(arg)` | Material names |
| `internalPlugins(arg)` | Registered framework plugins |
| `positionX/Y/Z(sender)` | The sender's current coordinates |

All suggestion methods filter case-insensitively by the current argument prefix.

### BukkitCommandWrapper

Root commands are wrapped in `BukkitCommandWrapper`, which extends `BukkitCommand` and handles recursive command resolution, sender validation, permission checks, event dispatch, and tab completion through Bukkit's command system.

## Event system

The framework provides base classes for custom events:

| Class | Purpose |
| --- | --- |
| `CustomEvent` | Base class for non-cancellable events |
| `CustomCancellableEvent` | Adds `Cancellable` with an optional cancellation reason |
| `CustomAsynchronousEvent` | Always-async variant |
| `CustomAsynchronousCancellableEvent` | Always-async cancellable variant |

`CustomCancellableEvent.setCancelledWithReason(reason)` sets both the cancelled flag and a reason string.

### Framework events

| Event | Dispatched when |
| --- | --- |
| `PluginInitializeEvent` | After the plugin has fully initialized |
| `PluginShutdownEvent` | Before the plugin begins shutdown |
| `ConfigSaveEvent` | After a configuration class is saved |
| `ConfigReloadEvent` | After a configuration class is reloaded |

### UtilEvent

`UtilEvent` dispatches events with sync/async control:

```java
UtilEvent.dispatch(event);
UtilEvent.dispatchSynchronously(event);
UtilEvent.dispatchAsynchronously(event);

final MyEvent result = UtilEvent.supply(new MyEvent());
if (result.isCancelled()) {
    return;
}

final CompletableFuture<MyEvent> future = UtilEvent.supplyAsynchronous(event);
```

## Display names

`DisplayName` is an immutable data class with an optional prefix, a required name, and an optional suffix. `getFullComponent()` joins all parts using `UtilAdventure.joinWithEmpty`.

`DisplayNameEvent` is dispatched with an entity and a recipient player. Listeners can modify the `DisplayName` to customize how entities appear per viewer:

```java
@EventHandler
public void onDisplayName(final DisplayNameEvent event) {
    if (event.getEntity() instanceof Player target) {
        event.setDisplayName(DisplayName.of(
                Component.text("[VIP] ", NamedTextColor.GOLD),
                Component.text(target.getName(), NamedTextColor.YELLOW),
                null
        ));
    }
}
```

## Stash classes

Immutable builder classes for Bukkit effects, created through static factory methods with validation:

| Class | Wraps | Key methods |
| --- | --- | --- |
| `EffectStash` | `Effect` with optional data | `play(location)`, `play(player)`, `broadcast()` |
| `ParticleStash` | `Particle` with offsets and data | `play(location)`, `play(player)`, `broadcast()` |
| `SoundStash` | `Sound` with category, volume, and pitch | `play(location)`, `play(player)`, `broadcast()` |
| `TitleStash` | Title and subtitle with fade timing | `send(player)`, `send(playerList)`, `broadcast()` |

```java
final SoundStash sound = SoundStash.of(Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYER, 1.0F, 1.0F);
sound.play(player);

final TitleStash title = TitleStash.of(
        Component.text("Welcome!", NamedTextColor.GOLD),
        Component.text("to the server", NamedTextColor.YELLOW),
        3000
);
title.send(player);
```

`ParticleStash` and `SoundStash` accept either the enum constant or a namespaced key string for forward compatibility.

## Utilities

| Class | Purpose |
| --- | --- |
| `UtilColor` | Extends `BaseUtilColor` with `toBukkitColor(Color)` for AWT-to-Bukkit conversion |
| `UtilEvent` | Event dispatching with sync/async and future variants |
| `UtilItemStack` | Persistent data helpers, custom item identifiers, inventory insert/remove/contains, item comparison, and display names |
| `UtilMaterial` | Classifies materials as interactable, interactable-by-hand, or usable |
| `UtilMessage` | Extends `BaseUtilMessage` with `broadcast` and `log` methods |
| `UtilNms` | Converts Adventure components to NMS components and sends raw packets |
| `UtilPermission` | Permission checking with OP, wildcard, and custom wildcard support |
| `UtilServer` | Online and offline player lookups by UUID or name |
| `UtilTask` | Task scheduling with `ChronoUnit` time conversion, delayed and repeating variants, and optional cancellation suppliers |
| `CommandRegistry` | Registers and unregisters root and child commands with Bukkit's command map |
| `PluginRegistry` | Tracks framework plugins by name and class for cross-plugin discovery |

### UtilTask

```java
UtilTask.executeSynchronously(() -> player.sendMessage("sync"));

UtilTask.executeLaterAsynchronously(3, ChronoUnit.SECONDS, () -> cleanup());

UtilTask.schedule(plugin, 0, 5, ChronoUnit.SECONDS, () -> tick(), () -> shouldCancel);
```

Time values are converted from `ChronoUnit` to Minecraft ticks (duration in milliseconds divided by 50).

### UtilItemStack

`UtilItemStack` manages persistent data on item stacks using a `NamespacedKey("custom", "item_identifier")`. `getIdentifierByItemStack` reads the identifier, and `isSimilar` compares items by identifier first when present, then by material, display name, lore, and enchantments.

`insert` adds an item to a player's inventory and drops overflow naturally. `contains` and `remove` check and remove items by count, using `isSimilar` for matching.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper</groupId>
    <artifactId>Minecraft-Paper-Plugin-Framework</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

The framework transitively includes Utilities, Injector-API, all three Injector extensions, and Minecraft-Common. Injector-Core is a `runtime` dependency. Paper API and Paper NMS are `provided`. Return to the [Minecraft README](../../README.md).
