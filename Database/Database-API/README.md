<h1 align="center">Database-API</h1>

<p align="center">Entity, property, index, query, holder, and storage contracts.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database-API defines the types shared by every Database module. Applications use these contracts for entity access, while [Core](../Database-Core/README.md) supplies explicit registration and runtime wiring.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Entities and declarations](#entities-and-declarations)
- [Indexes](#indexes)
- [Holders and repositories](#holders-and-repositories)
- [Queries and changes](#queries-and-changes)
- [Storage and exceptions](#storage-and-exceptions)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Area | Types |
| --- | --- |
| Runtime | `Database`: start, stop, readiness, holder and repository lookup |
| Entities | `Entity`, `EntityInstantiator` |
| Declarations | `Property`, `PropertyRegistry`, `IndexRegistry`, `DeclarationLoader` |
| Access | `Holder`, `Repository`, `Query`, `Condition`, `Order` |
| Storage | `Storage`, `StorageProvider`, `Namespace`, `Tier` |
| Changes | `Write`, `WriteKind`, `Change`, `ChangeType`, `ChangeEvent`, `EntityKey` |
| Conversion and coordination | `ValueConverter`, `SharedMarker` |

## Entities and declarations

An entity implements `Entity.getId()` and declares a constructor taking only `UUID`. `EntityInstantiator` finds that constructor, makes it accessible and uses it for row and cache decoding. Missing or failing constructors produce `SchemaException`.

`Property.persistent(...)` declares a PostgreSQL column; `Property.memory(...)` excludes it from full persistent saves and row mapping. Both take the entity class, column name, value class, getter and setter, with an optional `ValueConverter`. Use wrapper classes such as `Long.class`. An entity with only memory properties still has registered properties; PostgreSQL can store its id/version while those fields retain constructor defaults on reads. Memory properties still participate in Redis entity serialization; explicitly setting one through a holder still creates a write.

Properties and indexes register when their factory methods execute. `DeclarationLoader.load(ExampleProperty.class, ExampleIndex.class)` forces static initialization. Pass declaration classes to repository and holder construction before contexts capture their declarations. Registries are shared static maps keyed by entity class, without duplicate-declaration validation or reset.

See [generic examples](../README.md#generic-examples) for entity, property and holder declarations.

## Indexes

`UniqueIndex.of(name, property)` maps a value to one UUID; `GroupedIndex.of(name, property)` maps a value to several UUIDs. Both offer `derived(...)` factories with a value extractor and dependency properties for computed keys.

An empty index tier set inherits the holder's resolved tiers. Supply an explicit set to override it, particularly `Set.of(Tier.LOCAL)` for grouped indexes when Redis is enabled. Unique runtime stores exclude POSTGRES providers. Runtime indexes are lookup structures; PostgreSQL indexes are declared separately by the repository.

## Holders and repositories

| Operation | Contract |
| --- | --- |
| `Holder.get(id)` | Tiered lookup returning Optional |
| `getByProperty(property, value)` | Property lookup with implementation-specific fallback |
| `getByIndex`, `getAllByIndex` | Unique or grouped runtime lookup |
| `hold`, `holdAll`, `release`, `holds`, `getHeld` | Explicit local retention |
| `save`, `set`, `increment`, `decrement`, `delete` | Write through the configured authority |
| `reindex` | Rebuild lookup keys |
| `Repository.findById`, `find`, `count` | Direct persistent queries |
| `Repository.apply(write)` | Apply a low-level write and return its version |

`decrementIfAtLeast` is declared on Holder, but EntityHolder leaves it abstract. A concrete manager must implement it or explicitly reject it. There is no supplied atomic floor check.

## Queries and changes

`Query.where(property, value)` creates equality conditions; `Query.ordered(property, direction, limit)` creates ordering; `Query.all()` has no conditions or pagination. For multiple conditions and offsets, use the public constructor with `List<Condition>`, `List<Order>`, limit and offset. PostgreSQL combines conditions with AND. Operators are EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN, IN and CONTAINS; directions are ASCENDING and DESCENDING.

Query values are passed as supplied, without property conversion. Use stored representations for converted fields. Memory-only fields have no PostgreSQL column.

Write carries an EntityKey (table, tenant, UUID), kind, changes and expected version. Kinds are SAVE, UPDATE and DELETE; changes are SET, INCREMENT, ADD and REMOVE. PostgreSQL rejects ADD/REMOVE; contract declarations do not imply backend support.

ChangeEvent carries EntityKey, version, WriteKind and changes. Its constructor takes those four values in that order. It carries no origin field: Core reads origin from the Redis wire message before decoding and drops messages published by its own driver instance.

## Storage and exceptions

Namespace contains name, value class, key encoder, string value converter and TTL. Storage supports get, put, remove, contains, values and refresh. Redis rejects enumeration through values(); Local supplies it. SharedMarker exposes claim, await and release for load coordination. Tier defines LOCAL, REDIS, POSTGRES and the ALL set.

API exceptions extend DatabaseException: ConnectionException, SchemaException, QueryException, VersionConflictException and UniqueValueTakenException. UniqueValueTakenException is declared but is not thrown by current runtime index stores. Backend failures are not universally translated into API exceptions.

## Installation and build

Compile dependencies: Foundation Utilities. Parent dependencies also supply SLF4J and provided Lombok.

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-API</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

From the repository root, using JDK 25 and Maven:

```shell
mvn -pl Database/Database-API -am install
```

See the [Database guide](../README.md) for generic wiring and test commands. Return to [Foundation](../../README.md).
