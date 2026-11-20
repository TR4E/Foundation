<h1 align="center">Database-Lookup</h1>

<p align="center">Tiered ID lookups, concurrent load sharing, and cache backfill.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database-Lookup resolves UUIDs across configured caches and repositories. It uses [Concurrency](../Database-Concurrency/README.md) to share simultaneous work.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Lookup order](#lookup-order)
- [Shared loads](#shared-loads)
- [Backfill and retention](#backfill-and-retention)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Responsibility |
| --- | --- |
| `EntityLookup` | LOCAL → REDIS → POSTGRES resolution |
| `LoadGuard` | Optional shared marker and payload conversion |
| `Backfill` | Store repository results in requested caches |

## Lookup order

`find(id)` uses the holder's configured tiers. `find(id, tiers)` checks Local, then enters an `InFlight` load keyed by UUID and the requested tier set. That load checks Redis, then calls the repository if POSTGRES was requested. Missing stores are skipped.

The in-process key uses the requested set's string representation. Requests for different tier selections use separate keys.

## Shared loads

`LoadGuard` takes an optional `Stampede`, an entity payload converter and a UUID-to-key factory. Without a `Stampede`, it calls the loader directly. Core configures a `Stampede` when the holder's resolved tiers include REDIS and a marker is available. Holders without REDIS still share simultaneous loads through `InFlight`.

With a shared marker, the claim owner runs the repository lookup and publishes the result for waiters. Timeout or a missing payload permits a direct lookup. Core uses `load:<tenant>:<table>:<uuid>` keys for the shared marker. Those keys are shared across tier selections.

## Backfill and retention

Holder hold and reload can read a VersionedEntity snapshot directly from Redis. On a snapshot miss they use the tiered lookup fallback.

Successful repository ID reads backfill storage for requested cache tiers. Redis can be repopulated from a PostgreSQL result. Local is updated only when the ID is already held. Redis hits and decoded marker results return without the repository backfill step.

Runtime indexes and subscriptions are maintained by [Database-Holder](../Database-Holder/README.md).

## Installation and build

Compile dependencies: Database-Concurrency. Parent dependencies also supply SLF4J and provided Lombok.

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-Lookup</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

From the repository root, using JDK 25 and Maven:

```shell
mvn -pl Database/Database-Lookup -am install
```

See the [Database guide](../README.md) for generic wiring and test commands. Return to [Foundation](../../README.md).
