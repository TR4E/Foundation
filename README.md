<h1 align="center">Foundation</h1>

<p align="center">A modular Java toolkit for persistence, dependency injection, Minecraft platforms, Spring applications, and everyday utilities.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Multi--module-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://www.postgresql.org/"><img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-Supported-4169E1?logo=postgresql&logoColor=white"></a>
  <a href="https://redis.io/"><img alt="Redis" src="https://img.shields.io/badge/Redis-Optional-DC382D?logo=redis&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Modules-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://www.minecraft.net/"><img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-Integrations-62B47A?logo=minecraft&logoColor=white"></a>
</p>

Foundation is organized as a Maven reactor. Each module can be used independently, so applications can choose the pieces that fit their stack.

## Contents

- [Modules](#modules)
  - [Database](#database)
  - [Injector](#injector)
  - [Minecraft](#minecraft)
  - [Spring](#spring)
  - [Utilities](#utilities)
- [Build](#build)

## Modules

| Module | What it provides |
| --- | --- |
| [Database](#database) | PostgreSQL persistence with optional Redis storage and caching |
| [Injector](#injector) | Constructor-based dependency injection and component lifecycle management |
| [Minecraft](#minecraft) | Shared Minecraft support and Paper/Velocity plugin frameworks |
| [Spring](#spring) | Reusable Spring Boot web features |
| [Utilities](#utilities) | General-purpose Java helper classes |

### Database

An entity persistence stack for PostgreSQL, with optional local and Redis caching, tiered lookups, schema synchronization, and batched writes. See the [Database guide](Database/README.md) for usage details.

| Maven module | Purpose |
| --- | --- |
| [Database-API](Database/Database-API) | Entity, property, query, repository, holder, and tenant contracts |
| [Database-Storage](Database/Database-Storage) | Local cache, Redis storage, and entity codecs |
| [Database-Lookup](Database/Database-Lookup) | Lookups across local cache, Redis, and PostgreSQL |
| [Database-Core](Database/Database-Core) | PostgreSQL integration, schema management, repositories, and batched writes |

### Injector

A lightweight dependency injector with constructor injection, application lifecycles, generic-aware providers, live component collections, and optional extensions. See the [Injector guide](Injector/README.md).

| Maven module | Purpose |
| --- | --- |
| [Injector-API](Injector/Injector-API) | Annotations, contracts, callbacks, extension SPI, and exceptions |
| [Injector-Core](Injector/Injector-Core) | Dependency resolution, component registration, and lifecycle runtime |
| [Injector-Extensions](Injector/Injector-Extensions) | Optional additions to the Injector |
| [Injector-Commons-Extension](Injector/Injector-Extensions/Injector-Commons-Extension) | Annotation-based lifecycle hooks |
| [Injector-Scheduler-Extension](Injector/Injector-Extensions/Injector-Scheduler-Extension) | Scheduled component methods and executor integration |
| [Injector-Configuration-Extension](Injector/Injector-Extensions/Injector-Configuration-Extension) | JSON/YAML configuration loading, saving, and reloading |

### Minecraft

Shared Minecraft support plus platform-specific plugin frameworks. Paper addons are optional and can be selected individually.

| Maven module | Purpose |
| --- | --- |
| [Minecraft-Common](Minecraft/Minecraft-Common) | Shared code and Adventure text support |
| [Minecraft-Paper-Plugin-Framework](Minecraft/Minecraft-Paper/Minecraft-Paper-Plugin-Framework) | Paper plugin framework integrated with Utilities and Injector |
| [Minecraft-Paper-Addons](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons) | Optional Paper features |
| [Minecraft-Paper-Addon-Billboard](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Billboard) | Billboard displays and image/video support |
| [Minecraft-Paper-Addon-Death](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Death) | Death events and custom death messages |
| [Minecraft-Paper-Addon-Hologram](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Hologram) | Holograms and their lifecycle events |
| [Minecraft-Paper-Addon-Item](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Item) | Custom items, activation, recipes, and cooldowns |
| [Minecraft-Paper-Addon-Sidebar](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Sidebar) | Player sidebar displays |
| [Minecraft-Paper-Addon-Tablist](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Tablist) | Player tab list management |
| [Minecraft-Paper-Addon-Team](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Team) | Team management and related events |
| [Minecraft-Velocity-Plugin-Framework](Minecraft/Minecraft-Velocity/Minecraft-Velocity-Plugin-Framework) | Velocity plugin framework integrated with Utilities and Injector |

### Spring

Reusable Spring Boot web components for servlet-based applications. The modules share common request configuration and can otherwise be adopted independently.

| Maven module | Purpose |
| --- | --- |
| [Spring-Common](Spring/Spring-Common) | Shared properties, IP resolution, request path utilities, and filter ordering ([guide](Spring/Spring-Common/README.md)) |
| [Spring-Pages](Spring/Spring-Pages) | Page routes, access rules, assets, caching, navigation, and pagination ([guide](Spring/Spring-Pages/README.md)) |
| [Spring-Ratelimit](Spring/Spring-Ratelimit) | Annotation-driven controller rate limiting ([guide](Spring/Spring-Ratelimit/README.md)) |
| [Spring-Security](Spring/Spring-Security) | Security headers, Content Security Policy, and CSRF protection ([guide](Spring/Spring-Security/README.md)) |

### Utilities

A collection of Java helpers for reflection, strings, numbers, HTTP, files, hashing, Base64, Argon2, and functional interfaces. Its Maven module is [Utilities](Utilities).

## Build

Use JDK 25 and Maven to build and install the complete project from the repository root:

```shell
mvn clean install
```

The root POM aggregates all module families and configures Java 25 compilation and JUnit tests.
