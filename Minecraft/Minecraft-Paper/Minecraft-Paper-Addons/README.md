<h1 align="center">Minecraft-Paper-Addons</h1>

<p align="center">Optional Paper plugin features: chat channels, damage processing, items, NPCs, recipes, holograms, sidebars, billboards, death messages, tab lists, and teams.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Multi--module-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper" src="https://img.shields.io/badge/Paper-1.21.11-44cc11?logo=minecraft&logoColor=white"></a>
</p>

Minecraft-Paper-Addons is a Maven aggregator for the eleven optional feature addons. Each addon is a standalone library that integrates with the [Plugin Framework](../Minecraft-Paper-Plugin-Framework/README.md) through the addon system. Add only the addons your plugin needs.

## Addons

| Addon | Purpose | NMS |
| --- | --- | --- |
| [Billboard](Minecraft-Paper-Addon-Billboard/README.md) | Image and video rendering on maps via item frames | Yes |
| [Chat](Minecraft-Paper-Addon-Chat/README.md) | Chat channels with static and per-recipient placeholders | No |
| [Damage](Minecraft-Paper-Addon-Damage/README.md) | Staged damage processing, hit delays, and combat events | Yes |
| [Death](Minecraft-Paper-Addon-Death/README.md) | Custom death events and per-player death messages | No |
| [Hologram](Minecraft-Paper-Addon-Hologram/README.md) | Floating text holograms with per-player content | Yes |
| [Item](Minecraft-Paper-Addon-Item/README.md) | Custom items with activation, cooldowns, and inventory management | No |
| [NPC](Minecraft-Paper-Addon-Npc/README.md) | NPCs with capabilities, state restoration, and delayed respawns | No |
| [Recipe](Minecraft-Paper-Addon-Recipe/README.md) | Custom shaped and shapeless crafting recipes | No |
| [Sidebar](Minecraft-Paper-Addon-Sidebar/README.md) | Scoreboard sidebars with per-player content | Yes |
| [Tablist](Minecraft-Paper-Addon-Tablist/README.md) | Player tab list header and footer | No |
| [Team](Minecraft-Paper-Addon-Team/README.md) | Team prefixes, suffixes, colors, and collision rules | Yes |

The NMS column indicates whether the addon requires Paper NMS for server internals or direct packet access. Addons without NMS use the Bukkit API only.

The [Item addon](Minecraft-Paper-Addon-Item/README.md#item-search), [NPC addon](Minecraft-Paper-Addon-Npc/README.md#npc-search), and [Recipe addon](Minecraft-Paper-Addon-Recipe/README.md#recipe-search) include search engines for their registered definitions. All three use the framework's [Paper search API](../Minecraft-Paper-Plugin-Framework/README.md#search-engines) with exact and partial matching and colored feedback.

All addons share the Plugin Framework and Paper API as `provided` dependencies. The Damage addon depends on the Death addon for custom death events and message integration. The Recipe addon has an optional dependency on the Item addon for restricting custom items to their own recipes. The remaining addons have no dependencies on other addons.

This module is a Maven aggregator with `pom` packaging. Return to the [Minecraft README](../../README.md).
