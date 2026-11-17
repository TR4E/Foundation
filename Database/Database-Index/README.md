<h1 align="center">Database-Index</h1>

<p align="center">Unique and grouped indexes with dependency-aware maintenance.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database-Index builds lookup stores from API declarations. Runtime indexes map extracted values to entity IDs; repositories separately declare SQL indexes and constraints.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Placement](#placement)
- [Unique and grouped behavior](#unique-and-grouped-behavior)
- [Dependencies and lookup coverage](#dependencies-and-lookup-coverage)
- [Removal and retained entries](#removal-and-retained-entries)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Responsibility |
| --- | --- |
| IndexStores | Resolve declarations and select providers |
| UniqueIndexStore | Value to one UUID using selected cache stores |
| GroupedIndexStore | In-process value to UUID set |
| IndexUpdater | Dependency-based indexing, removal and refresh |
| IndexException | Unsupported grouped Redis placement |

## Placement

An empty declaration tier set inherits the holder's resolved tier set. Unique stores select matching providers in Tier order and exclude POSTGRES. A POSTGRES-only declaration therefore has no runtime storage.

Explicit declaration tiers override inheritance. Providers still come from the database instance's enabled tiers. A grouped declaration whose resolved set contains REDIS throws IndexException; use explicit LOCAL placement for a grouped working-set index.

```java
public static final GroupedIndex<ExampleEntity, String> CATEGORY = GroupedIndex.of(
        "example_category",
        ExampleProperty.CATEGORY,
        Set.of(Tier.LOCAL)
);
```

Grouped stores use local concurrent maps even when the holder has no entity Local store. They do not enumerate all persisted entities.

## Unique and grouped behavior

UniqueIndexStore looks up the first matching UUID across its stores. It overwrites an existing value mapping instead of atomically claiming it or throwing UniqueValueTakenException. Use a PostgreSQL UNIQUE index for persistent enforcement.

GroupedIndexStore returns the UUIDs currently indexed under a value. Both kinds keep local UUID-to-old-key reverse maps so a rename can remove the previous key. Non-null extracted keys are required by the current concurrent maps.

Redis unique values are UUID strings, with the entity TTL. Reverse maps remain process-local; cross-instance rename and removal do not have transactional ownership guarantees.

## Dependencies and lookup coverage

IndexUpdater.index(entity, property) updates only indexes declaring that dependency, plus indexes with empty dependency sets. Derived extractors can compute keys from several properties; declare every property affecting their result.

index(entity) rebuilds all stores; refresh(entity) refreshes unique stores only. Holder save, hold, property-query results, reindex and remote event application populate indexes. Direct repository reads do not.

getByIndex/getAllByIndex have no SQL fallback. getByProperty can query PostgreSQL after an index miss and warm its indexes. Runtime lookup absence does not prove persistent absence.

## Removal and retained entries

IndexUpdater.unindex(id) removes a UUID from all unique and grouped stores. Each store removes its remembered reverse mapping, then removes the corresponding key/member entry. A UUID with no remembered key is a no-op; removal no longer needs an entity or its current property values.

Holder release uses this UUID-based cleanup whether or not LOCAL is enabled. Unique cleanup also removes shared Redis mappings. Reverse maps are still process-local, so cleanup only knows keys indexed by that store instance. Indexes are lookup hints, not a distributed uniqueness or lifetime protocol.

## Installation and build

Compile dependencies: Database-API. The parent also supplies SLF4J and provided Lombok. Test-only dependencies do not become part of the runtime.

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-Index</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

From the repository root, using JDK 25 and Maven:

```shell
mvn -pl Database/Database-Index -am install
```

See the [Database guide](../README.md) for generic examples and test commands. Return to [Foundation](../../README.md).
