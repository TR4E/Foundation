<h1 align="center">Minecraft-Paper-Addon-Sidebar</h1>

<p align="center">Scoreboard sidebars with per-player content and priority-based selection.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper NMS" src="https://img.shields.io/badge/Paper-NMS-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The Sidebar addon displays scoreboard sidebars using NMS packets. Multiple sidebars can be registered with different priorities and eligibility conditions. Each player sees the highest-priority eligible sidebar, with per-player title and line content. Titles and lines are cached per player and only resent when they change.

## Contents

<details>
<summary>On this page</summary>

- [How it works](#how-it-works)
- [Creating a sidebar](#creating-a-sidebar)
- [Priority and eligibility](#priority-and-eligibility)
- [Caching](#caching)
- [Events](#events)
- [Installation](#installation)

</details>

## How it works

`SidebarService` manages registered sidebars and dispatches `SidebarUpdateEvent` every 250 milliseconds for each online player (asynchronously). `SidebarListener` handles the event and determines which sidebar to display:

1. Check if the current sidebar is still eligible for the player.
2. If not, clear it.
3. Find the highest-priority eligible sidebar.
4. If it differs from the current one, switch by clearing the old and creating the new.
5. If it is the same, update the title and lines if they changed.

Sidebars are created, updated, and cleared through NMS scoreboard packets:

| Operation | Packets |
| --- | --- |
| Create | `ClientboundSetObjectivePacket` (add), `ClientboundSetDisplayObjectivePacket` (sidebar slot), `ClientboundSetScorePacket` per line |
| Update title | `ClientboundSetObjectivePacket` (change) |
| Update lines | `ClientboundSetScorePacket` for changed lines, `ClientboundResetScorePacket` for removed lines |
| Clear | `ClientboundSetObjectivePacket` (remove) |

Each line is a score entry with owner `{playerUUID}:{lineIndex}`. Score values determine display order, with higher scores appearing higher on screen.

## Creating a sidebar

```java
@Singleton
public class GameSidebar extends Sidebar {

    public GameSidebar() {
        super("game", 0);
    }

    @Override
    public Component getTitle(final Player player) {
        return Component.text("Game Stats", NamedTextColor.GOLD);
    }

    @Override
    public List<Component> getLines(final Player player) {
        return List.of(
                Component.text("Kills: " + getKills(player), NamedTextColor.WHITE),
                Component.text("Deaths: " + getDeaths(player), NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Score: " + getScore(player), NamedTextColor.YELLOW)
        );
    }
}
```

The constructor takes a unique identifier and a priority. Override `getTitle` and `getLines` to provide per-player content.

## Priority and eligibility

When multiple sidebars are registered, priority determines which one is shown. Priority `0` is the highest (checked first), and `Integer.MAX_VALUE` is the lowest (checked last). The first eligible sidebar for the player is displayed. Override `canDisplay()` for global eligibility and `canDisplay(player)` for per-player checks.

Override `isStaticTitle()` to return `false` if the title changes over time. Static titles are sent once and not rechecked, saving bandwidth.

## Caching

Titles and lines are cached per player in `cachedTitleMap` and `cachedLinesMap`. The service only sends packets when the cached value differs from the current value. Line count changes trigger removal of excess lines. This avoids sending redundant packets every 250 milliseconds for static or slowly changing sidebars.

## Events

`SidebarUpdateEvent` extends `CustomAsynchronousCancellableEvent` and carries the player and an optional sidebar identifier. Cancel the event to clear the sidebar for that player. Set the identifier to update a specific sidebar.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Sidebar</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Requires Paper NMS. Return to the [Addons README](../README.md).
