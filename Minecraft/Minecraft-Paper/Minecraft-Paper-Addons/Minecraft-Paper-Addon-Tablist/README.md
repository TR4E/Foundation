<h1 align="center">Minecraft-Paper-Addon-Tablist</h1>

<p align="center">Player tab list header and footer with per-player content and priority-based selection.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper" src="https://img.shields.io/badge/Paper-API-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The Tablist addon customizes the player list header and footer shown when a player presses the tab key. Multiple tablists can be registered with different priorities and eligibility conditions. Each player sees the first eligible tablist, with per-player header and footer content. Updates are sent every second using Bukkit's native `Player.sendPlayerListHeaderAndFooter`.

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

`TablistService` manages registered tablists and runs a one-second scheduler tick. On each tick, `TablistUpdateEvent` is dispatched asynchronously for each online player. `TablistListener` handles the event:

1. Find the first eligible tablist (sorted by priority, lowest first).
2. If found, send the header and footer to the player and track it in `activeTablistSet`.
3. If none is eligible and the player had an active tablist, clear it by sending empty components.

Clearing sends empty components to remove the header and footer. The addon tracks active tablists to avoid redundant clear operations.

## Creating a tablist

```java
@Singleton
public class ServerTablist extends Tablist {

    public ServerTablist() {
        super(0);
    }

    @Override
    public Component getHeader(final Player player) {
        return Component.text("My Server", NamedTextColor.GOLD);
    }

    @Override
    public Component getFooter(final Player player) {
        return Component.text("Online: " + Bukkit.getOnlinePlayers().size(), NamedTextColor.GRAY);
    }
}
```

The constructor takes an integer priority. Override `getHeader` and `getFooter` to provide per-player content.

## Priority and eligibility

Priority `0` is the highest (checked first), and `Integer.MAX_VALUE` is the lowest (checked last). The first eligible tablist is displayed. Override `canDisplay()` for global eligibility and `canDisplay(player)` for per-player checks.

## Events

`TablistUpdateEvent` extends `CustomAsynchronousCancellableEvent` and carries the player. Cancel the event to prevent the tablist from being updated for that player on this tick.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Tablist</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Does not require Paper NMS. Return to the [Addons README](../README.md).
