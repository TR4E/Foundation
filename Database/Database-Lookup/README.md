<h1 align="center">Database-Lookup</h1>

<p align="center">Tiered ID lookups, concurrent load sharing, and cache backfill.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database-Lookup resolves UUIDs across configured caches and repositories. It uses [Concurrency](../Database-Concurrency/README.md) to share simultaneous work; writing and holding belong to other modules.

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
| `LoadGuard` | Shared marker and payload conversion |
| `Backfill` | Store repository results in requested caches |

## Lookup order

find(id) uses configured tiers. find(id, tiers) checks Local, then enters an InFlight load keyed by UUID plus the requested tier set representation. That load checks Redis, then a guarded repository lookup if POSTGRES is requested. Missing stores are skipped. Results are Optional; misses are not retained as persistent cache entries.

In-process loads for different requested tier selections use different keys. The key uses the Set's String representation rather than a canonical sorted encoding, so differently ordered representations can also split otherwise equivalent requests. Holder release now cleans up directly by UUID and does not start a LOCAL-only lookup.

## Shared loads

LoadGuard takes a Stampede, entity/string converter and UUID-to-key factory. The claim owner loads and publishes a payload; waiters decode it. Timeout or absent result allows a direct fallback read. Core uses `load:<table>:<uuid>` keys without tenant identity or tier selection. Shared-marker coordination therefore has broader identity than the in-process key. Marker leases are not extended while the repository loads; an expired lease allows a second instance to query before the first finishes.

## Backfill and retention

Successful repository ID reads backfill requested cache tiers. Local is written only when the held predicate is already true; Core tests whether Local already contains the UUID. Ordinary get therefore does not create a new hold.

Redis hits and decoded shared-marker results return directly without that repository backfill step. This module does not subscribe to events or populate runtime indexes.

With REDIS/POSTGRES and no Local hit, an expired Redis hash falls through to the repository and is repopulated. With LOCAL enabled, a held hit does not inspect or repair Redis. There is no automatic lifetime coupling between an unrefreshed Local reference and a Redis TTL.

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
