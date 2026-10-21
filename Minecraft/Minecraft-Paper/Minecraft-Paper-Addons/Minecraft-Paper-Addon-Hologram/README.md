<h1 align="center">Minecraft-Paper-Addon-Hologram</h1>

<p align="center">Floating text holograms with per-player content and distance-based visibility.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper NMS" src="https://img.shields.io/badge/Paper-NMS-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The Hologram addon displays floating text in the world using NMS `TextDisplay` entities. Each hologram supports per-player text content, distance-based visibility, and lifecycle events for spawn, despawn, and update.

## Contents

<details>
<summary>On this page</summary>

- [How it works](#how-it-works)
- [Creating a hologram](#creating-a-hologram)
- [Events](#events)
- [Display properties](#display-properties)
- [Installation](#installation)

</details>

## How it works

`HologramService` manages all registered holograms and runs a 500-millisecond scheduler tick. On each tick, for every hologram and every online player, the service evaluates visibility and takes action:

| Current state | Visible | Action |
| --- | --- | --- |
| Not in viewers | Yes | Spawn: send `ClientboundAddEntityPacket` and entity data |
| In viewers | No | Despawn: send `ClientboundRemoveEntitiesPacket` |
| In viewers | Yes (dynamic) | Update: send `ClientboundSetEntityDataPacket` with new text |

Visibility is determined by the hologram's `isVisible(player)` and `canSee(player)` methods combined with distance checking. The default view distance is 48 blocks.

`HologramListener` cleans up viewer tracking when a player disconnects.

## Creating a hologram

Extend `Hologram` and register it as a component:

```java
@Singleton
public class WelcomeHologram extends Hologram {

    public WelcomeHologram() {
        super("welcome", new Location(world, 0, 65, 0));
    }

    @Override
    public List<String> getLines(final Player player) {
        return List.of(
                "<gold>Welcome, " + player.getName() + "!",
                "<gray>Online: " + Bukkit.getOnlinePlayers().size()
        );
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}
```

Override `isDynamic()` to return `true` when the hologram's text changes over time or differs per player. Static holograms are only sent once when the player enters range.

## Events

| Event | Type | Purpose |
| --- | --- | --- |
| `HologramSpawnEvent` | Cancellable | Fired before a hologram is shown to a player |
| `HologramDespawnEvent` | Informational | Fired after a hologram is hidden from a player |
| `HologramUpdateEvent` | Informational | Fired after a dynamic hologram's text is updated for a player |

Cancel `HologramSpawnEvent` to prevent the hologram from appearing for that player.

## Display properties

The hologram's NMS `TextDisplay` entity supports configurable properties:

| Property | Default | Purpose |
| --- | --- | --- |
| Billboard mode | `CENTER` | Rotation behavior: CENTER, FIXED, VERTICAL, HORIZONTAL |
| Text alignment | `CENTER` | LEFT, CENTER, or RIGHT alignment |
| Background color | Transparent | ARGB background behind text |
| Scale | 1.0 | Entity scale |
| Text opacity | Full | Byte value for text transparency |
| Line width | 200 | Maximum line width before wrapping |
| See-through | `false` | Whether text renders through blocks |
| Shadow | `false` | Whether text has shadow rendering |

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Hologram</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Requires Paper NMS. Return to the [Addons README](../README.md).
