<h1 align="center">Minecraft-Paper-Addon-Team</h1>

<p align="center">Team prefixes, suffixes, colors, collision rules, and name tag visibility via NMS packets.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper NMS" src="https://img.shields.io/badge/Paper-NMS-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The Team addon manages scoreboard teams through NMS packets, allowing per-viewer customization of player name tags, prefixes, suffixes, colors, collision rules, and friendly-fire settings. Teams are bidirectional: each player-viewer pair can have its own team configuration.

## Contents

<details>
<summary>On this page</summary>

- [How it works](#how-it-works)
- [Creating a team](#creating-a-team)
- [Team properties](#team-properties)
- [Priority](#priority)
- [Bidirectional teams](#bidirectional-teams)
- [Events](#events)
- [Installation](#installation)

</details>

## How it works

`TeamService` manages registered teams. `TeamListener` handles player join and quit events to create and remove teams, and responds to `TeamUpdateEvent` to refresh team state.

When a player joins:
1. Create this player's team for all online viewers.
2. Create all online players' teams for this new player.

When a player quits:
1. Remove this player's team from all viewers.
2. Remove all players' teams from this quitting player.

Teams are created, updated, and removed through NMS scoreboard team packets:

| Operation | Packets |
| --- | --- |
| Create | `ClientboundSetPlayerTeamPacket` (create with properties), then add player to team |
| Update | `ClientboundSetPlayerTeamPacket` (modify with new properties) |
| Remove | Remove player from team, then `ClientboundSetPlayerTeamPacket` (remove team) |

## Creating a team

```java
@Singleton
public class RankTeam extends Team {

    public RankTeam() {
        super("rank", 0);
    }

    @Override
    public Component getPrefix(final Player player, final Player viewer) {
        final String rank = getRank(player);
        return Component.text("[" + rank + "] ", NamedTextColor.GOLD);
    }

    @Override
    public ChatFormatting getColor(final Player player, final Player viewer) {
        return ChatFormatting.YELLOW;
    }

    @Override
    public boolean canDisplay(final Player player, final Player viewer) {
        return true;
    }
}
```

The constructor takes a unique identifier and an integer priority. Override methods to define per-viewer team properties.

## Team properties

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `canDisplay()` | `boolean` | `true` | Global eligibility check |
| `canDisplay(player, viewer)` | `boolean` | `true` | Per-viewer eligibility check |
| `getDisplayName(player, viewer)` | `Component` | `null` | Override the team display name |
| `getPrefix(player, viewer)` | `Component` | `null` | Text before the player name |
| `getSuffix(player, viewer)` | `Component` | `null` | Text after the player name |
| `getColor(player, viewer)` | `ChatFormatting` | `null` | Team color for name rendering |
| `allowFriendlyFire(player, viewer)` | `Boolean` | `null` (server default) | Whether team members can damage each other |
| `seeFriendlyInvisibles(player, viewer)` | `Boolean` | `null` (server default) | Whether team members see invisible teammates |
| `getNameTagVisibility(player, viewer)` | `Visibility` | `null` | ALWAYS, NEVER, HIDE_FOR_OTHER_TEAMS, or HIDE_FOR_OWN_TEAM |
| `getDeathMessageVisibility(player, viewer)` | `Visibility` | `null` | When death messages are shown |
| `getCollisionRule(player, viewer)` | `CollisionRule` | `null` | ALWAYS, NEVER, PUSH_OWN_TEAM, or PUSH_OTHER_TEAMS |

All methods receive both the target player and the viewer, enabling per-viewer customization.

## Priority

Teams use integer priority where `0` is the highest priority and `Integer.MAX_VALUE` is the lowest. When multiple teams have `canDisplay()` returning `true` for a player-viewer pair, the team with the lowest priority value is selected first. Use priority to layer teams: a spectator team at priority `0` overrides a rank team at priority `10`.

## Bidirectional teams

Each player-viewer pair has its own team, identified as `{playerUUID}:{viewerUUID}`. This means player A can appear differently to player B than to player C. When player A joins a server with 50 players, 100 team create operations occur: 50 teams for how A appears to each viewer, and 50 teams for how each existing player appears to A.

## Events

`TeamUpdateEvent` extends `CustomEvent` and carries the player and an optional team identifier. Listeners can fire this event to trigger a team refresh:

- With an identifier: refreshes only the specified team for the player.
- Without an identifier: refreshes all teams for the player.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Team</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Requires Paper NMS. Return to the [Addons README](../README.md).
