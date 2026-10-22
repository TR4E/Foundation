<h1 align="center">Minecraft-Paper</h1>

<p align="center">Paper plugin framework and optional addons for Minecraft servers.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Multi--module-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper" src="https://img.shields.io/badge/Paper-1.21.11-44cc11?logo=minecraft&logoColor=white"></a>
</p>

Minecraft-Paper is a Maven aggregator that contains the Paper plugin framework and its optional addons. The plugin framework bridges Paper's lifecycle with Foundation's [Injector](../../Injector/README.md), providing constructor injection, scheduled tasks, configuration files, hierarchical commands, and automatic listener registration. Addons extend the framework with features such as chat channels, custom items, holograms, sidebars, and more.

## Modules

| Module | Purpose |
| --- | --- |
| [Minecraft-Paper-Plugin-Framework](Minecraft-Paper-Plugin-Framework/README.md) | Core plugin framework integrated with Injector |
| [Minecraft-Paper-Addons](Minecraft-Paper-Addons/README.md) | Optional feature addons |

This module is a Maven aggregator with `pom` packaging. Depend on the library artifacts listed in their guides. Return to the [Minecraft README](../README.md).
