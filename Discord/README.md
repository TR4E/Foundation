<h1 align="center">Discord</h1>

<p align="center">Discord webhook messages and embeds for Java applications.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Multi--module-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://discord.com/developers/docs/intro"><img alt="Discord" src="https://img.shields.io/badge/Discord-Webhooks-5865F2?logo=discord&logoColor=white"></a>
</p>

Foundation's Discord modules send messages through webhook URLs using Java's `HttpClient` and the shared [Utilities](../Utilities/README.md) helpers. The current module supports plain text, embeds, authors, footers, and fields, with synchronous and asynchronous dispatch.

## Contents

<details>
<summary>On this page</summary>

- [Modules](#modules)
- [Installation](#installation)
- [Building](#building)

</details>

## Modules

| Module | Artifact | Purpose |
| --- | --- | --- |
| [Discord-Webhook](Discord-Webhook/README.md) | `me.trae.foundation.discord:Discord-Webhook` | Compose webhook messages and embeds, serialize their payloads, and dispatch HTTP requests with optional response and error callbacks |

`Discord` itself is a Maven parent and aggregator with `pom` packaging. Applications depend on `Discord-Webhook` directly. The library needs no bot connection, gateway session, or application framework; the webhook URL identifies the destination.

See the [Webhook guide](Discord-Webhook/README.md) for usage examples, constructor options, embeds, and response handling.

## Installation

Requires JDK 25 and Maven. Build and install from the repository root:

```shell
mvn -pl Discord/Discord-Webhook -am install
```

Then add the library to your application:

```xml
<dependency>
    <groupId>me.trae.foundation.discord</groupId>
    <artifactId>Discord-Webhook</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Utilities is included transitively. No Injector or Minecraft dependency is required.

## Building

Compile Discord-Webhook and its reactor dependencies from the repository root:

```shell
mvn -pl Discord/Discord-Webhook -am compile
```

Compilation does not send webhook requests. Return to the [Foundation README](../README.md).
