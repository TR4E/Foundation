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
- [Getting started](#getting-started)
- [Build](#build)
- [Testing](#testing)
- [Documentation](#documentation)

## Modules

| Module | What it provides |
| --- | --- |
| [Database](Database/README.md) | PostgreSQL persistence with optional Redis storage and caching |
| [Injector](Injector/README.md) | Constructor-based dependency injection and component lifecycle management |
| [Minecraft](Minecraft/README.md) | Shared Minecraft support and Paper/Velocity plugin frameworks |
| [Spring](Spring/README.md) | Reusable Spring Boot web features |
| [Utilities](Utilities/README.md) | General-purpose Java helper classes |

### Database

An entity persistence stack for PostgreSQL, with optional local and Redis caching, tiered lookups, schema synchronization, and batched writes. See the [Database guide](Database/README.md) for usage details.

| Maven module | Purpose |
| --- | --- |
| [Database-API](Database/Database-API/README.md) | Entity, property, query, repository, holder, and tenant contracts |
| [Database-Storage](Database/Database-Storage/README.md) | Local cache, Redis storage, and entity codecs |
| [Database-Lookup](Database/Database-Lookup/README.md) | Lookups across local cache, Redis, and PostgreSQL |
| [Database-Core](Database/Database-Core/README.md) | PostgreSQL integration, schema management, repositories, and batched writes |

### Injector

A lightweight dependency injector with constructor injection, application lifecycles, generic-aware providers, live component collections, and optional extensions. See the [Injector guide](Injector/README.md).

| Maven module | Purpose |
| --- | --- |
| [Injector-API](Injector/Injector-API/README.md) | Annotations, contracts, callbacks, extension SPI, and exceptions |
| [Injector-Core](Injector/Injector-Core/README.md) | Dependency resolution, component registration, and lifecycle runtime |
| [Injector-Extensions](Injector/Injector-Extensions/README.md) | Extension selection, application callbacks, and custom extension SPI |
| [Injector-Commons-Extension](Injector/Injector-Extensions/Injector-Commons-Extension/README.md) | Annotation-based lifecycle hooks |
| [Injector-Scheduler-Extension](Injector/Injector-Extensions/Injector-Scheduler-Extension/README.md) | Scheduled component methods and executor integration |
| [Injector-Configuration-Extension](Injector/Injector-Extensions/Injector-Configuration-Extension/README.md) | JSON/YAML configuration loading, saving, and reloading |

### Minecraft

Shared Minecraft support plus platform-specific plugin frameworks. Paper addons are optional and can be selected individually. See the [Minecraft guide](Minecraft/README.md).

| Maven module | Purpose |
| --- | --- |
| [Minecraft-Common](Minecraft/Minecraft-Common/README.md) | Shared code and Adventure text support |
| [Minecraft-Paper-Plugin-Framework](Minecraft/Minecraft-Paper/Minecraft-Paper-Plugin-Framework/README.md) | Paper plugin framework integrated with Utilities and Injector |
| [Minecraft-Paper-Addons](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/README.md) | Optional Paper features |
| [Minecraft-Paper-Addon-Billboard](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Billboard/README.md) | Billboard displays and image/video support |
| [Minecraft-Paper-Addon-Chat](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Chat/README.md) | Chat channels with static and per-recipient placeholders |
| [Minecraft-Paper-Addon-Damage](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Damage/README.md) | Staged damage processing, hit delays, and combat events |
| [Minecraft-Paper-Addon-Death](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Death/README.md) | Death events and custom death messages |
| [Minecraft-Paper-Addon-Hologram](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Hologram/README.md) | Holograms and their lifecycle events |
| [Minecraft-Paper-Addon-Item](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Item/README.md) | Custom items, activation, and cooldowns |
| [Minecraft-Paper-Addon-Recipe](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Recipe/README.md) | Custom shaped and shapeless crafting recipes |
| [Minecraft-Paper-Addon-Sidebar](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Sidebar/README.md) | Player sidebar displays |
| [Minecraft-Paper-Addon-Tablist](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Tablist/README.md) | Player tab list management |
| [Minecraft-Paper-Addon-Team](Minecraft/Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Team/README.md) | Team management and related events |
| [Minecraft-Velocity-Plugin-Framework](Minecraft/Minecraft-Velocity/Minecraft-Velocity-Plugin-Framework/README.md) | Velocity plugin framework integrated with Utilities and Injector |

### Spring

Reusable, auto-configured Spring Boot web components for servlet-based applications. Each module registers its beans through `@AutoConfiguration` when the JAR is on the classpath, with no manual wiring required. The modules share common request configuration and can otherwise be adopted independently. See the [Spring guide](Spring/README.md).

| Maven module | Purpose |
| --- | --- |
| [Spring-Common](Spring/Spring-Common/README.md) | Production flag, IP resolution, path canonicalization, and filter order constants |
| [Spring-Pages](Spring/Spring-Pages/README.md) | Page-as-a-class framework with role-based access, asset gating, cache control, navigation, and pagination |
| [Spring-Ratelimit](Spring/Spring-Ratelimit/README.md) | Annotation-driven rate limiting per IP or per account, with pluggable storage |
| [Spring-Security](Spring/Spring-Security/README.md) | Security response headers with per-directive CSP and double-submit CSRF protection |

### Utilities

A collection of Java helpers for reflection, strings, numbers, HTTP, files, hashing, Base64, Argon2, and functional interfaces. See the [Utilities guide](Utilities/README.md) for methods and examples.

## Getting started

Choose the module family that matches your application, install it from source, and add the corresponding Maven artifact. The examples use version `1.0-SNAPSHOT`; a local Maven installation makes those artifacts available to another project.

| Goal | Start with | Integration |
| --- | --- | --- |
| Persist Java entities | [Database quick start](Database/README.md#quick-start) | Compile against `Database-Core`, which includes the other Database modules |
| Wire components and control startup | [Injector quick start](Injector/README.md#quick-start) | Compile against `Injector-API`, with `Injector-Core` at runtime |
| Add configuration, scheduling, or lifecycle annotations | [Injector extensions](Injector/Injector-Extensions/README.md) | Add only the individual extensions used by the application |
| Add web features to a Spring application | The Spring module guides above | Choose the required feature modules |
| Use general Java helpers | [Utilities](Utilities/README.md) | Add the Utilities artifact |

For example, a persistence application can depend on:

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-Core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

The root and module-family POMs are aggregators. Depend on the library artifacts listed in their guides. Database and Injector work together through constructor injection and can also be used independently.

## Build

Use JDK 25 and Maven to build and install the complete project from the repository root:

```shell
mvn clean install
```

The root POM aggregates all module families and configures Java 25 compilation and JUnit tests.

To install only a selected family and its reactor dependencies, run one of these commands from the repository root:

```shell
mvn -pl Database/Database-Core -am clean install
mvn -pl Injector/Injector-Core,Injector/Injector-Extensions/Injector-Commons-Extension,Injector/Injector-Extensions/Injector-Scheduler-Extension,Injector/Injector-Extensions/Injector-Configuration-Extension -am clean install
```

Java examples in the guides omit imports unless shown and may use Lombok. Foundation configures Lombok annotation processing in its parent POM; consumer projects using those example annotations need their own Lombok setup.

## Testing

| Suite | Command from the repository root | External services |
| --- | --- | --- |
| Injector and all extensions | `mvn -f Injector/pom.xml test` | None |
| Database modules | `mvn -f Database/pom.xml test` | PostgreSQL and Redis for integration coverage |
| Complete reactor | `mvn test` | Depends on the selected modules and integration tests |

Database tests can skip cases when their services are unreachable. Use the [isolated Database test environment](Database/README.md#building-and-testing) to exercise those paths and inspect the skipped count. The guide also covers multi-JVM coordination and opt-in container recovery checks.

## Documentation

Each family guide introduces its architecture and common usage; individual module guides explain configuration, API behavior, examples, and focused build commands.

- [Database](Database/README.md): entity setup, cache modes, durability, tenant selection, dependency injection, and testing.
- [Injector](Injector/README.md): application startup, constructor injection, providers, lifecycle, and optional extensions.
- [Minecraft](Minecraft/README.md): Paper and Velocity plugin frameworks, addon system, commands, and optional features.
- [Utilities](Utilities/README.md): helper APIs and usage examples.
- [Spring](Spring/README.md): [Common](Spring/Spring-Common/README.md), [Pages](Spring/Spring-Pages/README.md), [Ratelimit](Spring/Spring-Ratelimit/README.md), and [Security](Spring/Spring-Security/README.md).
