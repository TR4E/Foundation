<h1 align="center">Minecraft-Paper-Addons</h1>

<p align="center">Optional Paper plugin features: items, recipes, holograms, sidebars, billboards, death messages, tab lists, and teams.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Multi--module-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper" src="https://img.shields.io/badge/Paper-1.21.11-44cc11?logo=minecraft&logoColor=white"></a>
</p>

Minecraft-Paper-Addons is a Maven aggregator for the eight optional feature addons. Each addon is a standalone library that integrates with the [Plugin Framework](../Minecraft-Paper-Plugin-Framework/README.md) through the addon system. Add only the addons your plugin needs.

## Addons

| Addon | Purpose | NMS |
| --- | --- | --- |
| [Billboard](Minecraft-Paper-Addon-Billboard/README.md) | Image and video rendering on maps via item frames | Yes |
| [Death](Minecraft-Paper-Addon-Death/README.md) | Custom death events and per-player death messages | No |
| [Hologram](Minecraft-Paper-Addon-Hologram/README.md) | Floating text holograms with per-player content | Yes |
| [Item](Minecraft-Paper-Addon-Item/README.md) | Custom items with activation, cooldowns, and inventory management | No |
| [Recipe](Minecraft-Paper-Addon-Recipe/README.md) | Custom shaped and shapeless crafting recipes | No |
| [Sidebar](Minecraft-Paper-Addon-Sidebar/README.md) | Scoreboard sidebars with per-player content | Yes |
| [Tablist](Minecraft-Paper-Addon-Tablist/README.md) | Player tab list header and footer | No |
| [Team](Minecraft-Paper-Addon-Team/README.md) | Team prefixes, suffixes, colors, and collision rules | Yes |

The NMS column indicates whether the addon requires Paper NMS for direct packet access. Addons without NMS use the Bukkit API only.

All addons share the Plugin Framework and Paper API as `provided` dependencies. The Recipe addon has an optional dependency on the Item addon for restricting custom items to their own recipes. All other addons are independent.

This module is a Maven aggregator with `pom` packaging. Return to the [Minecraft README](../../README.md).
