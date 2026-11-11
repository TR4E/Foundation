<h1 align="center">Minecraft-Paper-Addon-Tablist</h1>

<p align="center">Player tab list header and footer with per-player content and priority-based selection.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper" src="https://img.shields.io/badge/Paper-API-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The Tablist addon customizes the player list header and footer shown when a player presses the tab key. Multiple tablists can be registered with different priorities and eligibility conditions. Each player sees the eligible tablist with the lowest priority value, with per-player header and footer content. Updates are evaluated every second and sent through `Player.sendPlayerListHeaderAndFooter` only when the header or footer changes.

## Contents

<details>
<summary>On this page</summary>

- [How it works](#how-it-works)
- [Creating a tablist](#creating-a-tablist)
- [Priority and eligibility](#priority-and-eligibility)
- [Events](#events)
- [Installation](#installation)

</details>

## How it works

`TablistService` manages registered tablists and runs a one-second scheduler tick. On each tick, `TablistUpdateEvent` is dispatched asynchronously for each online player. An asynchronous update is also dispatched when a player joins. `TablistListener` handles the event:

1. If an identifier is supplied, ignore the event unless it matches the currently active tablist.
2. Clear the active tablist if the accepted event is cancelled.
3. Select the eligible tablist with the lowest priority value. Clear the current one if none is eligible, or if a scoped event would select a different identifier; scoped events never create a different fallback.
4. Store the selected tablist in `activeTablistMap`. Compare the header and footer with `cachedHeaderFooterMap`; send and cache both components only when either changed.

Clearing removes the cached components and active tablist, sending empty components when a tablist was active. Unchanged header and footer components skip sending while still updating the active definition.

## Creating a tablist

```java
@Singleton
public class ServerTablist extends Tablist {

    public ServerTablist() {
        super("server", 0);
    }

    @Override
    public Component getHeader(final Player player) {
        return Component.text("My Server", NamedTextColor.GOLD);
    }

    @Override
    public Component getFooter(final Player player) {
        return Component.text("Online: %d".formatted(UtilServer.getOnlinePlayers().size()), NamedTextColor.GRAY);
    }
}
```

The constructor takes a unique identifier and an integer priority. Override `getHeader` and `getFooter` to provide per-player content.

## Priority and eligibility

Priority `0` is the highest (checked first), and `Integer.MAX_VALUE` is the lowest (checked last). The first eligible tablist is displayed. Override `canDisplay()` for global eligibility and `canDisplay(player)` for per-player checks.

## Events

`TablistUpdateEvent` extends `CustomAsynchronousCancellableEvent` and carries the player and an optional tablist identifier. Use `new TablistUpdateEvent(player)` for normal selection or `new TablistUpdateEvent(identifier, player)` to process only the currently active tablist with that identifier. If none is active or the identifier differs, the event does nothing, including cancellation. A matching active definition that is no longer selected and eligible is removed without creating a different fallback. Unscoped updates may select any eligible tablist. Cancelling the event clears the player's active header and footer on this tick. A later uncancelled update can display them again.

Call `tablist.updatePlayer(player)` or `tablist.updateAllPlayers()` to request asynchronous updates carrying that tablist's identifier. The all-player helper captures the online-player list once and processes it in one asynchronous task.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Tablist</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Does not require Paper NMS. Return to the [Addons README](../README.md).
