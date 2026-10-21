<h1 align="center">Minecraft-Common</h1>

<p align="center">Shared Adventure text, color translation, Mojang API, and message formatting for Minecraft modules.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://docs.advntr.dev/"><img alt="Adventure" src="https://img.shields.io/badge/Adventure-5.2.0-4B5563?logo=minecraft&logoColor=white"></a>
</p>

Minecraft-Common is the shared library used by both the Paper and Velocity plugin frameworks. It provides Adventure component utilities, legacy-to-MiniMessage color translation, configurable message formatting with prefix and color defaults, and a Mojang API client for UUID and username lookups. All classes are static utility classes or enums with no mutable instance state.

## Contents

<details>
<summary>On this page</summary>

- [Adventure component joining](#adventure-component-joining)
- [Color translation](#color-translation)
- [Message formatting](#message-formatting)
- [Mojang API](#mojang-api)
- [Installation](#installation)

</details>

## Adventure component joining

`UtilAdventure` joins Adventure `Component` arrays with configurable separators and optional filtering.

| Method | Separator | Filters empty |
| --- | --- | --- |
| `join(separator, predicate, components)` | Caller-supplied | Caller-supplied predicate |
| `join(separator, components)` | Caller-supplied | No |
| `joinWithSpace(components)` | Space | Yes |
| `joinWithEmpty(components)` | None | No |
| `joinWithNewLine(components)` | Newline | Yes |

Null components are always excluded. The methods return a single `Component`.

```java
final Component line = UtilAdventure.joinWithSpace(
        Component.text("Hello"),
        Component.text("World")
);
```

## Color translation

### ChatColor

`ChatColor` defines the 16 standard Minecraft colors and 6 text formatting codes as an enum. Each color constant carries its legacy code character and an AWT `Color`.

`ChatColor.translate(text)` converts legacy `&` and `\u00A7` format codes to MiniMessage tags:

```java
final String modern = ChatColor.translate("&cHello &lWorld");
// "<red>Hello <bold>World"
```

The nested `ChatFormat` enum covers `MAGIC` (`k`), `BOLD` (`l`), `STRIKETHROUGH` (`m`), `UNDERLINE` (`n`), `ITALIC` (`o`), and `RESET` (`r`).

### BaseUtilColor

`BaseUtilColor` converts between AWT `Color` and Minecraft text formats.

| Method | Purpose |
| --- | --- |
| `serialize(color, string)` | Wraps the string in a MiniMessage color tag, using the legacy name when the RGB matches a standard color and hex format otherwise |
| `toTextColor(color)` | Converts an AWT `Color` to an Adventure `TextColor` |

```java
final String tagged = BaseUtilColor.serialize(ChatColor.RED.getColor(), "Error");
// "<red>Error</red>"

final String hex = BaseUtilColor.serialize(new Color(0x123456), "Custom");
// "<#123456>Custom</#123456>"
```

Both methods return `null` when given a `null` color.

## Message formatting

### Message

`Message` is the central formatting engine. It holds process-wide defaults for prefix color, message color, and prefix format. These are configurable so each plugin can set its own theme once at startup.

| Setting | Default | Setter |
| --- | --- | --- |
| Prefix color | `ChatColor.BLUE` (#5555FF) | `Message.setPrefixColor(Color)` |
| Message color | `ChatColor.GRAY` (#AAAAAA) | `Message.setMessageColor(Color)` |
| Prefix format | `"[%s] "` | `Message.setPrefixFormat(String)` |

`Message.render(prefix, message)` combines a prefix and message component, applying the default colors only where the component does not already carry a color. `Message.prefix(string)` creates a formatted prefix component using the configured format and color.

```java
final Component output = Message.render("Server", "<gold>Restarting in 10 seconds");
// "[Server] " in blue, then "Restarting in 10 seconds" in gold
```

### BaseUtilMessage

`BaseUtilMessage` sends messages through the `Message` renderer. It provides overloads for single audiences and audience collections, accepting `Component` or `String` inputs for both prefix and message. Collection variants accept an optional `Collection<UUID>` of audiences to exclude.

```java
BaseUtilMessage.message(player, "Server", "Welcome!");

BaseUtilMessage.message(onlinePlayers, prefix, message, ignoredUuids);
```

The `filter` helper creates a composite `Audience` that excludes the ignored UUIDs using Adventure's `filterAudience`. Audiences without a UUID identity are always included.

`serialize(component)` and `deserialize(string)` convert between Adventure components and MiniMessage strings.

## Mojang API

`MojangApi` looks up Minecraft player UUIDs and usernames through Mojang's public REST endpoints.

| Method | Returns | Endpoint |
| --- | --- | --- |
| `getIdByUsername(username)` | `Optional<UUID>` | `api.mojang.com/users/profiles/minecraft/{username}` |
| `getUsernameById(uuid)` | `Optional<String>` | `sessionserver.mojang.com/session/minecraft/profile/{uuid}` |

Both methods return an empty `Optional` when the player does not exist or the input is invalid. HTTP calls use Foundation's `UtilHttp`. Non-success status codes other than 204 and 404 throw `HttpException`.

```java
final Optional<UUID> uuid = MojangApi.getIdByUsername("Notch");

final Optional<String> name = MojangApi.getUsernameById(uuid);
```

Mojang rate-limits these endpoints. The methods do not cache results; add application-level caching when calling them frequently.

## Installation

Minecraft-Common is pulled in transitively by both plugin frameworks. To depend on it directly:

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft</groupId>
    <artifactId>Minecraft-Common</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Adventure API and MiniMessage are `provided` dependencies, expected to be supplied by the server or proxy at runtime. Return to the [Minecraft README](../README.md).
