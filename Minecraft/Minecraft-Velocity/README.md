<h1 align="center">Minecraft-Velocity</h1>

<p align="center">Velocity proxy plugin framework integrated with Foundation's Injector.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Multi--module-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/software/velocity"><img alt="Velocity" src="https://img.shields.io/badge/Velocity-4.2.0-44cc11?logo=minecraft&logoColor=white"></a>
</p>

Minecraft-Velocity is a Maven aggregator that contains the Velocity plugin framework. The framework bridges Velocity's proxy lifecycle with Foundation's [Injector](../../Injector/README.md), providing constructor injection, scheduled tasks, configuration files, and hierarchical Brigadier commands.

The framework also provides [search engines](Minecraft-Velocity-Plugin-Framework/README.md#search-engines) for online players and Foundation plugins, with colored feedback to a `CommandSource`. Its guide includes usage and implementation examples based on the existing `search.types` classes.

## Modules

| Module | Purpose |
| --- | --- |
| [Minecraft-Velocity-Plugin-Framework](Minecraft-Velocity-Plugin-Framework/README.md) | Core plugin framework integrated with Injector |

This module is a Maven aggregator with `pom` packaging. Depend on the library artifact listed above. Return to the [Minecraft README](../README.md).
