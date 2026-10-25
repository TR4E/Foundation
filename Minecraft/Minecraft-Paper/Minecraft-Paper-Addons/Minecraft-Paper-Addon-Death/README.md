<h1 align="center">Minecraft-Paper-Addon-Death</h1>

<p align="center">Custom death events and per-player death messages.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper" src="https://img.shields.io/badge/Paper-API-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The Death addon replaces Minecraft's default death messages with a customizable, per-player death message system. It suppresses the vanilla death message, resolves entity display names through the framework's `DisplayNameEvent`, and fires a `CustomDeathMessageEvent` for each online player so listeners can modify or cancel individual messages.

## Contents

<details>
<summary>On this page</summary>

- [How it works](#how-it-works)
- [Death events](#death-events)
- [Death message formatting](#death-message-formatting)
- [Per-player display names](#per-player-display-names)
- [Custom death creators](#custom-death-creators)
- [Damage addon integration](#damage-addon-integration)
- [Installation](#installation)

</details>

## How it works

`DeathListener` handles Bukkit's `EntityDeathEvent` and resolves a `DeathEvent` through the first registered `DeathCreator`, or `VanillaDeathEvent` when no creators are registered. It dispatches the resolved event, applies its drops, experience, and sound settings, and fires a `CustomDeathMessageEvent` for each online player. The message listener formats the per-player message; listeners can customize the names or cancel delivery for that player.

Display names for the victim and killer are resolved by dispatching `DisplayNameEvent` from the plugin framework. This means rank prefixes, team colors, and other per-viewer customizations are reflected in death messages.

## Death events

`DeathEvent` is the interface contract for a death event, carrying the victim entity, optional killer, cause, reason, drops, experience, and a sound stash.

`CustomDeathMessageEvent` extends `CustomCancellableEvent` and carries:

| Field | Type | Purpose |
| --- | --- | --- |
| `deathEvent` | `DeathEvent` | The underlying death event |
| `recipient` | `Player` | The player receiving this death message |
| `entityName` | `DisplayName` | The victim's display name for this recipient |
| `killerName` | `DisplayName` | The killer's display name for this recipient (if present) |

Each field on `CustomDeathMessageEvent` is settable. Modify `entityName` or `killerName` to change how names appear in the death message for that specific recipient. Cancel the event to suppress the death message entirely for that player.

## Death message formatting

The message listener formats death messages based on available information:

| Scenario | Format |
| --- | --- |
| Suicide or `/kill` | "X was killed" |
| Has killer, no reason | "X was killed by Y" |
| Has killer and reason | "X was killed by Y with Z" |
| No killer, has cause | "X was killed by [cause]" |

Non-player entities are prefixed with the appropriate indefinite article ("a zombie", "an enderman") using `UtilString.getIndefiniteArticlePrefix`.

## Per-player display names

Death messages use the framework's `DisplayNameEvent` to resolve names. This event is fired once per recipient, so different players can see different names for the same victim or killer. This is how team colors, rank prefixes, and other per-viewer customizations appear in death messages.

To customize display names in death messages, listen to `DisplayNameEvent`:

```java
@Singleton
public class TeamDisplayNameListener implements Listener {

    @EventHandler
    public void onDisplayName(final DisplayNameEvent event) {
        if (!(event.getEntity() instanceof final Player player)) {
            return;
        }

        final NamedTextColor teamColor = getTeamColor(player); // BLUE, RED, YELLOW, GREEN, etc.

        event.setDisplayName(DisplayName.of(Component.text(player.getName(), teamColor)));
    }
}
```

With this listener, death messages automatically reflect team colors per recipient. If player A is on the blue team and kills player B on the red team, every recipient sees:

> **Death** <span style="color:red">PlayerB</span> was killed by <span style="color:blue">PlayerA</span>.

The `DisplayName` class supports prefix, name, and suffix components via `DisplayName.of(prefix, name, suffix)`, allowing rank tags or other decorations alongside team colors.

## Custom death creators

Plugins can implement `DeathCreator` to produce custom `DeathEvent` implementations for specific scenarios. The death listener uses creators to build the death event before dispatching messages.

```java
@Singleton
public class BossDeathCreator implements DeathCreator {

    @Override
    public DeathEvent createDeathEvent(final EntityDeathEvent entityDeathEvent) {
        // Return a custom DeathEvent with boss-specific fields
    }
}
```

## Damage addon integration

The [Damage addon](../Minecraft-Paper-Addon-Damage/README.md) supplies `CustomDeathCreator`, which resolves the victim's most recent applied `CustomDamageEvent` into a `CustomDeathEvent`. The death event carries the captured attacker, cause, and weapon into this addon's death-message system, along with copies of the Bukkit drops, experience, and sound settings.

Declare both `DamageAddon.class` and `DeathAddon.class` in your plugin's `@Addons` annotation to enable this integration. The Damage addon includes the Death addon as a Maven dependency; the annotation enables its components at runtime.

If no matching damage history exists, `CustomDeathCreator` logs the missing history and returns `null`. `DeathListener` stops processing when its selected creator returns `null`; it does not try another creator or fall back to `VanillaDeathEvent` for that death.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Death</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Does not require Paper NMS. Return to the [Addons README](../README.md).
