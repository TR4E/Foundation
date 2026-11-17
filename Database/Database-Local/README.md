<h1 align="center">Database-Local</h1>

<p align="center">Concurrent in-process storage and a local marker implementation.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database-Local implements generic Storage using ConcurrentHashMap. Core uses it for held entities and local unique index entries.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Storage behavior](#storage-behavior)
- [Local marker](#local-marker)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Responsibility |
| --- | --- |
| `LocalStorage<Key, Value>` | Concurrent encoded-key storage |
| `LocalStorageProvider` | Create storage for Tier.LOCAL |
| `LocalMarker` | No-op shared load marker |

## Storage behavior

Construct LocalStorage with a Namespace. Keys use its encoder; values remain object references. The value converter and TTL are unused. There is no expiry, size limit, serialization or copying. holdAll keeps the entire repository result and its runtime index mappings in memory; the caller chooses a working set that fits the process.

get returns Optional; put replaces; remove deletes; contains checks presence. values() exposes the concurrent map's live values collection; clear() empties it. refresh does nothing. Separate LocalStorage instances have separate maps even with identical namespace names.

Entity retention is explicit: hold/holdAll populate Local, release removes entries. Ordinary reads do not create new local holds. Application code remains responsible for mutable value threading. Local storage makes map operations concurrent; it does not make multi-step entity mutation or hold/release atomic. A Local hit returns immediately without checking Redis expiry.

## Local marker

LocalMarker.claim always returns true; await always returns empty; release methods do nothing. It provides no leasing or shared coordination. Local simultaneous-load sharing is handled by InFlight in Lookup/Concurrency.

## Installation and build

Compile dependencies: Database-API. Parent dependencies also supply SLF4J and provided Lombok.

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-Local</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

From the repository root, using JDK 25 and Maven:

```shell
mvn -pl Database/Database-Local -am install
```

See the [Database guide](../README.md) for generic wiring and test commands. Return to [Foundation](../../README.md).
