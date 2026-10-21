<h1 align="center">Database-Lookup</h1>

<p align="center">Ordered cache lookups, backfilling, and indexed entity access.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://redis.io/"><img alt="Optional Redis tier" src="https://img.shields.io/badge/Redis-Optional%20tier-DC382D?logo=redis&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven library" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database-Lookup combines storage tiers into a single lookup path. It depends on [Database-Storage](../Database-Storage/README.md) and the repository contracts in [Database-API](../Database-API/README.md), while [Database-Core](../Database-Core/README.md) supplies the PostgreSQL implementation and wires lookups into holders.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Lookup flow](#lookup-flow)
- [Using holder lookups](#using-holder-lookups)
- [Building a lookup pipeline](#building-a-lookup-pipeline)
- [Concurrent and bulk lookups](#concurrent-and-bulk-lookups)
- [Property and composite indexes](#property-and-composite-indexes)
- [Consistency and fallback](#consistency-and-fallback)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Purpose |
| --- | --- |
| `TieredLookup<Key, Value>` | Visit ordered steps, stop on a hit, and backfill misses |
| `LookupStep<Key, Value>` | Associate a lookup tier with readers and an optional cache writer |
| `EntityLookups` | Build entity ID, property, and composite index pipelines |
| `InFlightLookup` | Share concurrent single-key work in one JVM |
| `PropertyIndex` / `RedisPropertyIndex` | Resolve a property value to an entity UUID |
| `RedisEntityIndex` | Resolve composite property values to an entity UUID |

## Lookup flow

Core holders normally read tiers in this order:

```text
Request -> Local cache -> Redis cache -> PostgreSQL
               ^              ^              |
               +--------------+--------------+
                    Backfill earlier misses
```

An available value ends the search. Earlier enabled steps that missed receive the value through their writers. If every enabled step misses, the result is empty. `TieredLookup` follows the supplied step-list order; it does not reorder custom steps by enum value.

## Using holder lookups

Most application code uses the holder wired by Core:

```java
final Optional<Account> byId = accountHolder.getById(accountId);
final Optional<Account> byName = accountHolder.getByProperty(AccountProperties.NAME, "alice");

final Set<LookupTier> localTierSet = EnumSet.of(LookupTier.LOCAL);
final Optional<Account> localAccount = accountHolder.getById(accountId, localTierSet);
```

The examples assume the model and holder from the [Database quick start](../README.md#quick-start). The default overload searches all available tiers. Restricting the tier set changes both where the lookup reads and which earlier steps can be backfilled.

| Tier set | Behavior |
| --- | --- |
| `EnumSet.of(LOCAL)` | Return an existing local entry or empty |
| `EnumSet.of(REDIS, DATABASE)` | Skip the local tier |
| `EnumSet.of(DATABASE)` | Read PostgreSQL without populating skipped cache tiers |
| All tiers | Use the normal local, Redis, database path |

A Redis tier is available only when configured for that holder. Property and composite index lookups also accept tier sets in Core's implementation.

## Building a lookup pipeline

`LookupStep.of(tier, storage)` adapts a `Storage` instance. Reader-based overloads accept a single-key reader, an optional bulk reader, and an optional writer.

```java
final LocalStorage<UUID, Account> localStorage = new LocalStorage<>(Duration.ofMinutes(10));
final TieredLookup<UUID, Account> tieredLookup = new TieredLookup<>(List.of(
        LookupStep.of(LookupTier.LOCAL, localStorage),
        LookupStep.of(LookupTier.DATABASE, accountRepository::findById)
));

final Optional<Account> account = tieredLookup.lookup(accountId);
```

This custom example backfills local storage after a repository hit. It assumes registered Account properties and a connected repository. It supplies lookup behavior only; Core's standard holder also maintains change tracking, local indexes, invalidation, and write coordination.

`EntityLookups.byId(localStorage, redisStorage, repository)` builds the standard entity pipeline. Variants accept a local writer so callers can maintain related local indexes while filling the cache.

## Concurrent and bulk lookups

Single-key lookups through the same `TieredLookup` share in-flight work for the same key and selected tier set. This reduces duplicate loads within one JVM without combining requests that selected different tiers.

`lookupAll(keys, tiers)` visits each enabled step with the unresolved keys and backfills values found later. It returns a map of found values in input-key order; missing keys are omitted and duplicate keys have one map entry. A step with no bulk reader falls back to individual reads.

Entity pipelines integrate Redis fill coordination across JVMs. A missing ID is loaded by a fill-lock owner while peers wait and recheck Redis. Batch fills coordinate ownership per ID and load the owned IDs together. Short-lived missing and failed-fill markers reduce repeated database requests. See [cache fill coordination](../Database-Storage/README.md#cache-fill-coordination) for current timings.

## Property and composite indexes

| Lookup | Local behavior | Redis behavior | PostgreSQL behavior |
| --- | --- | --- | --- |
| UUID | Cache key lookup | Entity hash lookup | Primary key lookup |
| Declared unique property | Value-to-ID index, then entity lookup | Value-to-ID pointer, then entity lookup | Property condition |
| Declared composite unique index | Ordered value tuple to ID | Composite pointer to ID | Conditions on the indexed properties |
| Unindexed property | Scan cached entities | No declared unique value pointer | Property query |

`BTREE`, `GIN_TRGM`, and `BRIN` improve PostgreSQL queries but do not automatically create the holder's unique value-to-ID cache index.

Declare composite indexes on the repository:

```java
@Override
public List<EntityIndex<Account>> getCompositeIndexes() {
    return List.of(AccountIndexes.NAME_AND_REGION);
}
```

Here `AccountIndexes.NAME_AND_REGION` would be a constant created with `EntityIndex.of("name_and_region", AccountProperties.NAME, AccountProperties.REGION)`, where both properties belong to the entity and are persistent. Lookup values must be non-null, match each property's type, and follow the declaration order:

```java
final Optional<Account> account = accountHolder.getByIndex(
        AccountIndexes.NAME_AND_REGION,
        "alice",
        "au"
);
```

Case-insensitive string properties normalize index keys while preserving the stored display value. A stale local pointer to an expired entity is discarded. Redis pointers are checked against the loaded entity and removed when they no longer match.

## Consistency and fallback

Lookups read cached state when a selected cache tier hits. Committed Core holder mutations invalidate shared caches through the PostgreSQL outbox, so multi-instance cache visibility is eventual. Selecting only PostgreSQL bypasses those cached results but still cannot expose a write that has not committed.

Entity lookup builders handle Redis failures through their database fallback paths. A custom `TieredLookup` does not automatically catch arbitrary exceptions from user-supplied steps; each step's reader and writer must provide the desired failure behavior.

## Installation and build

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-Lookup</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Core users receive this dependency transitively. From the repository root with JDK 25 and Maven:

```shell
mvn -pl Database/Database-Lookup -am install
```

Generic lookup tests require no external service. Tests inherited from the storage dependency may use Redis. For full entity integration tests, follow [Database testing](../README.md#building-and-testing).
