<h1 align="center">Minecraft</h1>

<p align="center">Shared Minecraft support and platform-specific plugin frameworks for Paper and Velocity.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Multi--module-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper" src="https://img.shields.io/badge/Paper-1.21.11-44cc11?logo=minecraft&logoColor=white"></a>
  <a href="https://papermc.io/software/velocity"><img alt="Velocity" src="https://img.shields.io/badge/Velocity-4.2.0-44cc11?logo=minecraft&logoColor=white"></a>
</p>

Minecraft provides a common library for Adventure text components, color handling, and Mojang API integration, plus plugin frameworks for Paper servers and Velocity proxies. Both frameworks bridge Foundation's [Injector](../Injector/README.md) with their platform's lifecycle, so plugins get constructor injection, scheduled tasks, configuration files, and hierarchical commands without manual wiring. Paper addons are optional and can be selected individually.

## Contents

<details>
<summary>On this page</summary>

- [Modules](#modules)
- [Installation](#installation)
- [Paper plugin quick start](#paper-plugin-quick-start)
- [Velocity plugin quick start](#velocity-plugin-quick-start)
- [Building and testing](#building-and-testing)
</details>

## Modules

| Module | Artifact | Purpose |
| --- | --- | --- |
| [Minecraft-Common](Minecraft-Common/README.md) | `me.trae.foundation.minecraft:Minecraft-Common` | Shared code: Adventure text, color translation, Mojang API, and message formatting |
| [Minecraft-Paper-Plugin-Framework](Minecraft-Paper/Minecraft-Paper-Plugin-Framework/README.md) | `me.trae.foundation.minecraft.paper:Minecraft-Paper-Plugin-Framework` | Paper plugin framework integrated with Injector |
| [Minecraft-Paper-Addons](Minecraft-Paper/Minecraft-Paper-Addons/README.md) | `me.trae.foundation.minecraft.paper.addons:Minecraft-Paper-Addons` | Optional Paper features |
| [Minecraft-Paper-Addon-Billboard](Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Billboard/README.md) | `me.trae.foundation.minecraft.paper.addons:Minecraft-Paper-Addon-Billboard` | Billboard displays and image/video support |
| [Minecraft-Paper-Addon-Chat](Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Chat/README.md) | `me.trae.foundation.minecraft.paper.addons:Minecraft-Paper-Addon-Chat` | Chat channels with static and per-recipient placeholders |
| [Minecraft-Paper-Addon-Death](Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Death/README.md) | `me.trae.foundation.minecraft.paper.addons:Minecraft-Paper-Addon-Death` | Death events and custom death messages |
| [Minecraft-Paper-Addon-Hologram](Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Hologram/README.md) | `me.trae.foundation.minecraft.paper.addons:Minecraft-Paper-Addon-Hologram` | Holograms and their lifecycle events |
| [Minecraft-Paper-Addon-Item](Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Item/README.md) | `me.trae.foundation.minecraft.paper.addons:Minecraft-Paper-Addon-Item` | Custom items, activation, and cooldowns |
| [Minecraft-Paper-Addon-Recipe](Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Recipe/README.md) | `me.trae.foundation.minecraft.paper.addons:Minecraft-Paper-Addon-Recipe` | Custom shaped and shapeless crafting recipes |
| [Minecraft-Paper-Addon-Sidebar](Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Sidebar/README.md) | `me.trae.foundation.minecraft.paper.addons:Minecraft-Paper-Addon-Sidebar` | Player sidebar displays |
| [Minecraft-Paper-Addon-Tablist](Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Tablist/README.md) | `me.trae.foundation.minecraft.paper.addons:Minecraft-Paper-Addon-Tablist` | Player tab list management |
| [Minecraft-Paper-Addon-Team](Minecraft-Paper/Minecraft-Paper-Addons/Minecraft-Paper-Addon-Team/README.md) | `me.trae.foundation.minecraft.paper.addons:Minecraft-Paper-Addon-Team` | Team management and related events |
| [Minecraft-Velocity-Plugin-Framework](Minecraft-Velocity/Minecraft-Velocity-Plugin-Framework/README.md) | `me.trae.foundation.minecraft.velocity:Minecraft-Velocity-Plugin-Framework` | Velocity plugin framework integrated with Injector |

`Minecraft`, `Minecraft-Paper`, `Minecraft-Paper-Addons`, and `Minecraft-Velocity` are Maven aggregators with `pom` packaging. Applications depend on the library artifacts listed above.

## Installation

Requires JDK 25 and Maven. Build and install from the repository root:

```shell
mvn -pl Minecraft/Minecraft-Paper/Minecraft-Paper-Plugin-Framework -am install
```

Or for a Velocity plugin:

```shell
mvn -pl Minecraft/Minecraft-Velocity/Minecraft-Velocity-Plugin-Framework -am install
```

Then depend on the framework for your platform:

```xml
<!-- Paper plugin -->
<dependency>
    <groupId>me.trae.foundation.minecraft.paper</groupId>
    <artifactId>Minecraft-Paper-Plugin-Framework</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- Velocity plugin -->
<dependency>
    <groupId>me.trae.foundation.minecraft.velocity</groupId>
    <artifactId>Minecraft-Velocity-Plugin-Framework</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Paper addons are separate dependencies. Add only the ones you need:

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Item</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Paper plugin quick start

Extend `PaperPlugin` and declare addons. The framework scans your package for `@Singleton` components, registers Bukkit listeners and commands, and manages startup and shutdown through Injector.

```java
@Addons({ItemAddon.class})
public class MyPlugin extends PaperPlugin {
}

@Singleton
public class WelcomeListener implements Listener {

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        UtilMessage.message(event.getPlayer(), "Server", "Welcome!");
    }
}

@Singleton
public class HelloCommand extends BaseCommand<MyPlugin, Player> {

    public HelloCommand() {
        super("hello", "Say hello", List.of("hi"), "myplugin.hello");
    }

    @Override
    public void execute(final Player sender, final String[] args) {
        UtilMessage.message(sender, "Server", "Hello!");
    }
}
```

Components that implement `Listener` are registered with Bukkit automatically. Commands extending `AbstractCommand` are registered with the command map. The `@Scheduler`, `@Configuration`, `@PostConstruct`, and other Injector extension annotations work the same way they do outside of Minecraft. See the [Plugin Framework guide](Minecraft-Paper/Minecraft-Paper-Plugin-Framework/README.md) for the addon system, command hierarchy, events, and utilities.

## Velocity plugin quick start

Extend `VelocityPlugin`. The same Injector lifecycle applies, adapted for Velocity's proxy model.

```java
@Plugin(id = "my-proxy-plugin")
public class MyProxyPlugin extends VelocityPlugin {

    @Inject
    public MyProxyPlugin(final ProxyServer proxyServer, @DataDirectory final Path dataDirectory) {
        super(proxyServer, dataDirectory);
    }
}

@Singleton
public class PingCommand extends BaseCommand<MyProxyPlugin, Player> {

    public PingCommand() {
        super("ping", "Check latency", List.of(), "myproxy.ping");
    }

    @Override
    public void execute(final Player sender, final String[] args) {
        UtilMessage.message(sender, "Proxy", "Pong!");
    }
}
```

Components that implement `Listener` are registered with Velocity's event manager. Commands are wrapped as Brigadier commands. See the [Velocity Framework guide](Minecraft-Velocity/Minecraft-Velocity-Plugin-Framework/README.md).

## Building and testing

Build the complete Minecraft module family from the repository root:

```shell
mvn -f Minecraft/pom.xml install
```

The Minecraft modules have no integration tests that require external services. Return to the [Foundation README](../README.md).
