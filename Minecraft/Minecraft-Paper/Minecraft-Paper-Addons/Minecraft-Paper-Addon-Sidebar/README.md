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

`SidebarService` checks active sidebars asynchronously every 250 milliseconds. It dispatches `SidebarUpdateEvent` when an active sidebar becomes ineligible; otherwise it refreshes only non-static titles. Players without an active sidebar are skipped. Joining players receive an asynchronous update event, and explicit updates can be requested through `sidebar.updatePlayer(player)` or `sidebar.updateAllPlayers()`. `SidebarListener` handles update events and determines which sidebar to display:

1. If an identifier is supplied, ignore the event unless it matches the currently active sidebar.
2. Clear the current sidebar if the accepted event is cancelled.
3. Select the eligible sidebar with the lowest priority value. Clear the current one if none is eligible, or if a scoped event would select a different identifier; scoped events never create a different fallback.
4. For an unscoped update selecting a different definition, clear the old and create the new.
5. Otherwise, update the active sidebar's title and lines if they changed.

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
                Component.text("Player: %s".formatted(player.getName()), NamedTextColor.WHITE),
                Component.text("Server statistics", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Online: %d".formatted(UtilServer.getOnlinePlayers().size()), NamedTextColor.YELLOW)
        );
    }
}
```

The constructor takes a unique identifier and a priority. Override `getTitle` and `getLines` to provide per-player content.

## Priority and eligibility

When multiple sidebars are registered, priority determines which one is shown. Priority `0` is the highest (checked first), and `Integer.MAX_VALUE` is the lowest (checked last). The first eligible sidebar for the player is displayed. Override `canDisplay()` for global eligibility and `canDisplay(player)` for per-player checks.

Override `isStaticTitle()` to return `false` if the title changes over time. Static titles skip scheduled refreshes, but explicit update events still refresh them. Lines refresh through update events; the scheduler does not poll line content.

## Caching

Titles and lines are cached per player in `cachedTitleMap` and `cachedLinesMap`. Cached lines are immutable snapshots, so modifying and reusing the source list still produces an update. Title refreshes send packets only when the title changes. Line updates resend changed lines and any lines whose scores shift when the line count changes, and remove excess lines. The scheduler uses the title cache during non-static title refreshes; update events use both caches.

## Events

`SidebarUpdateEvent` extends `CustomAsynchronousCancellableEvent` and carries the player and an optional sidebar identifier. Cancel the event to clear the sidebar for that player. An identifier scopes all actions, including cancellation and removal, to the currently active sidebar with that identifier. If none is active or the identifier differs, the event does nothing. If the matching active sidebar is no longer selected and eligible, it is cleared without creating another definition. An update without an identifier reevaluates normal eligibility and priority and may select any eligible sidebar.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Sidebar</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Requires Paper NMS. Return to the [Addons README](../README.md).
