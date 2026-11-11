<h1 align="center">Minecraft-Paper-Addon-Team</h1>

<p align="center">Team prefixes, suffixes, colors, collision rules, and name tag visibility via NMS packets.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper NMS" src="https://img.shields.io/badge/Paper-NMS-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The Team addon manages scoreboard teams through NMS packets, allowing per-viewer customization of player name tags, prefixes, suffixes, colors, collision rules, and friendly-fire settings. Teams are bidirectional: each player-viewer pair can have its own team configuration. Below-name displays support custom components and native scoreboard criteria.

## Contents

<details>
<summary>On this page</summary>

- [How it works](#how-it-works)
- [Creating a team](#creating-a-team)
- [Team properties](#team-properties)
  - [Below-name display](#below-name-display)
- [Priority](#priority)
- [Bidirectional teams](#bidirectional-teams)
- [Events](#events)
- [Installation](#installation)

</details>

## How it works

`TeamService` manages registered teams. `TeamListener` handles player join and quit events to create and remove teams, and responds to `TeamUpdateEvent` to refresh team state. Selection requires `viewer.canSee(player)`: hiding a player removes that player's team for the viewer, and showing the player refreshes the team using current eligibility and priority.

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
        return Component.text("[Member] ", NamedTextColor.GOLD);
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
| `getBelowName(player, viewer)` | `BelowName` | `null` | Custom text, native health, or another native criterion beneath the player name |
| `getColor(player, viewer)` | `ChatFormatting` | `null` | Team color for name rendering |
| `allowFriendlyFire(player, viewer)` | `Boolean` | `null` (server default) | Whether team members can damage each other |
| `seeFriendlyInvisibles(player, viewer)` | `Boolean` | `null` (server default) | Whether team members see invisible teammates |
| `getNameTagVisibility(player, viewer)` | `Visibility` | `null` | ALWAYS, NEVER, HIDE_FOR_OTHER_TEAMS, or HIDE_FOR_OWN_TEAM |
| `getDeathMessageVisibility(player, viewer)` | `Visibility` | `null` | When death messages are shown |
| `getCollisionRule(player, viewer)` | `CollisionRule` | `null` | ALWAYS, NEVER, PUSH_OWN_TEAM, or PUSH_OTHER_TEAMS |

The per-viewer methods receive both the target player and the viewer. `canDisplay()` is the separate global check. These settings are sent to each viewer through packets; the addon does not configure server-side scoreboard membership or enforce friendly-fire rules on the server.

### Below-name display

Override one method to choose the display for each player/viewer pair. Returning `null` removes the entry and is the default:

```java
@Override
public BelowName getBelowName(final Player player, final Player viewer) {
    return viewer.hasPermission("example.belowname.health")
            ? BelowName.asHealth()
            : BelowName.asCriteria(Criteria.LEVEL);
}
```

The factories are:

| Factory | Display |
| --- | --- |
| `BelowName.asComponent(component)` | Fixed custom text |
| `BelowName.asHealth()` | Native health without a label and with `INTEGER` render type |
| `BelowName.asHealth(component, renderType)` | Native health with an explicit component label and render type |
| `BelowName.asCriteria(criteria)` | A native criterion without a label and with `INTEGER` render type |

For example, use `BelowName.asComponent(Component.text("Level 2"))` for custom text, or `BelowName.asHealth(Component.text("HP", NamedTextColor.RED), RenderType.INTEGER)` for numeric health followed by `HP`. `asHealth()` and `asCriteria(criteria)` provide no label and use `INTEGER`. `asHealth(component, renderType)` uses the supplied label and render type without adding defaults. The service treats a null native component as an empty objective label. `asCriteria` accepts only the criterion. The single `component` field holds fixed text for component mode and the objective label for native mode.

Each viewer has a separate server-backed objective on their existing scoreboard. Joe can see the same player's level while John sees health. Minecraft updates supported native criteria automatically; the addon does not poll or write numeric scores. Criteria such as `DUMMY` require an external source to set their values. Values and initialization follow native scoreboard behavior. Component-only viewers use a `DUMMY` objective with fixed number formats.

The criterion, objective display name, and render type are shared across one viewer's numeric entries. Conflicting configurations raise an `IllegalStateException` before replacing the objective. Component entries can coexist with native entries, but an objective label also appears after their custom text because the client appends that shared label. A non-empty objective label can also appear on visible players whose hook returns `null`; use an empty objective display name when selective omission is required.

Fire a `TeamUpdateEvent` when the return value or eligibility conditions change. Criterion or scoreboard changes re-evaluate the viewer's other tracked entries and restore eligible entries on the replacement objective. Changes to only the label or render type update the existing objective without resetting its scores. Switching the last numeric entry off preserves remaining component entries using a `DUMMY` objective. Removing the last entry unregisters the objective. Hiding players and quitting use the existing removal lifecycle.

Native tracking sends objective data to players sharing the scoreboard; it does not provide packet-level privacy for hidden players. The addon selects the below-name display slot only for the intended viewer and does not replace their scoreboard. Another plugin using `BELOW_NAME` can replace this display or have its display replaced; the addon does not restore another plugin's previous objective.

## Priority

Teams use integer priority where `0` is the highest priority and `Integer.MAX_VALUE` is the lowest. When multiple teams have `canDisplay()` returning `true` for a player-viewer pair, the team with the lowest priority value is selected first. Use priority to layer teams: a spectator team at priority `0` overrides a rank team at priority `10`.

## Bidirectional teams

Each player-viewer pair has its own team, identified as `{playerUUID}:{viewerUUID}`. This means player A can appear differently to player B than to player C. With 50 players online including the joining player, the listener refreshes 99 pairs: 50 views of the joining player and 49 views of the other players for that player. Each pair creates a team only when a definition is eligible.

## Events

`TeamUpdateEvent` extends `CustomEvent`, is not cancellable, and carries the player and an optional team identifier. Listeners can fire this event to trigger a team refresh:

- With an identifier: ignore the event unless that identifier matches the currently active team for the player/viewer pair. Update that team only while it remains selected and eligible; otherwise remove it without creating a different fallback. A scoped event never activates an inactive definition.
- Without an identifier: selects the eligible team with the lowest priority value for each viewer, or removes the team when none is eligible.

Call `team.updatePlayer(player)` to request a synchronous update carrying that team's identifier. `team.updateAllPlayers()` captures the online-player list once and processes all updates in one synchronous task.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Team</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Requires Paper NMS. Return to the [Addons README](../README.md).
