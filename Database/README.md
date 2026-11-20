<h1 align="center">Database</h1>

<p align="center">Per-holder persistence, shared Redis authority, and explicit DI wiring.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Multi--module-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database is Foundation's Java 25 persistence stack. PostgreSQL supplies durable repositories and additive schema setup. See [installation](INSTALLATION.md) for complete repository and manager wiring. Holders are DI beans extending EntityHolder with entity and repository type parameters. Each holder chooses its tier set. A database instance supplies the available tiers, tenant, drivers and lifecycle.

## Contents

<details>
<summary>On this page</summary>

- [Modules](#modules)
- [Installation](#installation)
- [Per-holder tiers](#per-holder-tiers)
- [Generic examples](#generic-examples)
- [Database and DI wiring](#database-and-di-wiring)
- [Read and write paths](#read-and-write-paths)
- [Schema and coalescing](#schema-and-coalescing)
- [Building and testing](#building-and-testing)

</details>

## Modules

| Module | Maven coordinates | Purpose |
| --- | --- | --- |
| [Database-API](Database-API/README.md) | `me.trae.foundation.database:Database-API:1.0-SNAPSHOT` | Entity, property, index, query, storage and holder contracts |
| [Database-Batch](Database-Batch/README.md) | `me.trae.foundation.database:Database-Batch:1.0-SNAPSHOT` | Write merge matrix and scheduled persistence |
| [Database-Concurrency](Database-Concurrency/README.md) | `me.trae.foundation.database:Database-Concurrency:1.0-SNAPSHOT` | Per-key and shared-marker load coalescing |
| [Database-Core](Database-Core/README.md) | `me.trae.foundation.database:Database-Core:1.0-SNAPSHOT` | Database instances, per-holder contexts and lifecycle |
| [Database-Holder](Database-Holder/README.md) | `me.trae.foundation.database:Database-Holder:1.0-SNAPSHOT` | DI holder beans, local retention and event application |
| [Database-Index](Database-Index/README.md) | `me.trae.foundation.database:Database-Index:1.0-SNAPSHOT` | Unique and grouped runtime lookup stores |
| [Database-Local](Database-Local/README.md) | `me.trae.foundation.database:Database-Local:1.0-SNAPSHOT` | Concurrent in-process storage |
| [Database-Lookup](Database-Lookup/README.md) | `me.trae.foundation.database:Database-Lookup:1.0-SNAPSHOT` | Tiered reads and selective backfill |
| [Database-Postgres](Database-Postgres/README.md) | `me.trae.foundation.database:Database-Postgres:1.0-SNAPSHOT` | Repositories, schema and versioned SQL writes |
| [Database-Redis](Database-Redis/README.md) | `me.trae.foundation.database:Database-Redis:1.0-SNAPSHOT` | Hash/string storage, Lua authority and origin-tagged events |
| [Database-Tenant](Database-Tenant/README.md) | `me.trae.foundation.database:Database-Tenant:1.0-SNAPSHOT` | Instance tenant identities and scoped repository selection |

Database is a POM aggregator. Database-Core brings in the other ten libraries transitively. Injector is optional application wiring, not a Database runtime dependency.

## Installation

Use JDK 25 and Maven. PostgreSQL is required by Core startup; Redis is optional.

```shell
mvn -pl Database/Database-Core -am install
```

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-Core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

The generic examples omit packages/imports and use Lombok. Put public types in separate files and configure annotation processing in a consumer project.

## Per-holder tiers

Core's `ContextFactory` intersects each holder's tiers with `DatabaseSettings.tiers`. Database settings make the tiers available to holders.

| Holder tiers | Entity storage | Write authority | PostgreSQL persistence |
| --- | --- | --- | --- |
| LOCAL, POSTGRES | Explicitly held Local objects | Repository | Synchronous, once |
| REDIS, POSTGRES | Shared Redis hashes | Redis script | Queued |
| LOCAL, REDIS, POSTGRES | Local working set and Redis | Redis script | Queued |

For a Spring `MULTI_INSTANCE` application that uses shared storage, choose `Set.of(Tier.REDIS, Tier.POSTGRES)` for those holders and enable both tiers in the database settings. A holder with this set resolves IDs through Redis and PostgreSQL. `holds(id)` returns `false` and `getHeld()` is empty. See [Spring multi-instance wiring](INSTALLATION.md#spring-multi-instance).

A Redis driver supplies Redis storage. Unique index declarations inherit the holder's resolved tiers and exclude `POSTGRES` providers. Grouped indexes are process-local maps. Give grouped working-set indexes an explicit `LOCAL` tier when Redis is enabled.

## Generic examples

An entity implements Entity and has a UUID-only constructor. Persistent properties drive SQL schema and full saves. Index declarations initialize before holder context creation.

```java
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
@Getter
@Setter
public final class ExampleEntity implements Entity {

    @EqualsAndHashCode.Include
    private final UUID id;

    private Long counter = 0L;

    private String name, category;
}
```

```java
@UtilityClass
public final class ExampleProperty {

    public static final Property<ExampleEntity, String> NAME = Property.persistent(ExampleEntity.class, "name", String.class, ExampleEntity::getName, ExampleEntity::setName);
    public static final Property<ExampleEntity, String> CATEGORY = Property.persistent(ExampleEntity.class, "category", String.class, ExampleEntity::getCategory, ExampleEntity::setCategory);

    public static final Property<ExampleEntity, Long> COUNTER = Property.persistent(ExampleEntity.class, "counter", Long.class, ExampleEntity::getCounter, ExampleEntity::setCounter);
}
```

```java
@UtilityClass
public final class ExampleIndex {

    public static final UniqueIndex<ExampleEntity, String> NAME = UniqueIndex.of("example_name", ExampleProperty.NAME);

    public static final GroupedIndex<ExampleEntity, String> CATEGORY = GroupedIndex.of("example_category", ExampleProperty.CATEGORY, Set.of(Tier.LOCAL));
}
```

Runtime unique indexes map a value to an ID; they do not enforce database uniqueness. The repository declares SQL indexes separately:

```java
public final class ExampleRepository extends PostgresRepository<ExampleEntity> {

    public ExampleRepository(final PostgresDriver postgresDriver, final TenantScope tenantScope) {
        super(ExampleEntity.class, "examples", postgresDriver, tenantScope, ExampleProperty.class);
    }

    @Override
    public Map<Property<?, ?>, PostgresIndexType> getIndexes() {
        return Map.of(ExampleProperty.NAME, PostgresIndexType.UNIQUE);
    }
}
```

The holder constructor declares its tiers and registers the holder through the factory.

```java
public final class ExampleHolder extends EntityHolder<ExampleEntity, ExampleRepository> {

    public ExampleHolder(final ExampleRepository exampleRepository, final HolderContextFactory holderContextFactory) {
        super(ExampleEntity.class, exampleRepository, holderContextFactory, Set.of(Tier.LOCAL, Tier.POSTGRES), ExampleProperty.class, ExampleIndex.class);
    }
}
```

Null extracted index values remove any previous runtime mapping. Memory properties are excluded from PostgreSQL full saves and row mapping, but remain part of Redis entity serialization.

## Database and DI wiring

CoreDatabase takes settings and drivers. Its tenant comes from DatabaseSettings, not a separate constructor argument.

```java
final DatabaseSettings databaseSettings = new DatabaseSettings(Tier.ALL, null, Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(2), Duration.ZERO, 0);
final CoreDatabase coreDatabase = new CoreDatabase(databaseSettings, postgresDriver, redisDriver);
```

The settings arguments are enabled tiers, tenant, entity TTL, refresh interval, batch interval, marker lease, marker timeout, local TTL and local size limit. Null/blank tenant means no tenant. Pass null for redisDriver when Redis should not be used.

Expose PostgresDriver, coreDatabase.getTenantScope() and coreDatabase.getContextFactory() to your DI container. The container constructs ExampleRepository and ExampleHolder before Core starts. With [Foundation Injector](../Injector/README.md), explicitly supplied component classes work without additional annotations:

```java
final CoreInjector coreInjector = new CoreInjector();

coreInjector.provide(PostgresDriver.class, postgresDriver);
coreInjector.provide(TenantScope.class, coreDatabase.getTenantScope());
coreInjector.provide(HolderContextFactory.class, coreDatabase.getContextFactory());

try {
    coreInjector.initialize(application, List.of(ExampleRepository.class, ExampleHolder.class));
    coreDatabase.start();

    System.out.println(coreInjector.get(ExampleHolder.class).getHeld().size());
} finally {
    coreInjector.shutdown(application);
    coreDatabase.stop();
}
```

Here application is an application-owned object annotated with Injector's Application annotation. Add Injector's artifacts when choosing this integration. Another container can supply the same constructor dependencies. Register one holder per entity class per database instance; the registry replaces duplicate class/table entries.

Core start connects drivers, applies registered repository DDL, and starts batching and refreshers. Core stop drains admitted writes, closes registered holders and gives the batch scheduler a final flush window before disconnecting the drivers. See [holder lifecycle](Database-Holder/README.md#remote-events-and-refresh) and [batch shutdown](Database-Batch/README.md#scheduling-and-failures).

## Read and write paths

Capacity and TTL can bound each holder?s LOCAL entity store. Zero capacity and Duration.ZERO preserve unbounded retention. Index stores keep their own storage policy. Holder refresh groups Redis expirations by storage on a dedicated batch connection. holdAll subscribes to the loaded IDs in one request.

ID reads use the enabled tiers in LOCAL, REDIS, POSTGRES order. `hold` and `holdAll` populate the Local working set when enabled. `release` removes remembered index entries by UUID, removes any Local entry and unsubscribes. It clears live version tracking and preserves delete markers.

`getByProperty` tries a matching unique runtime index, or a held scan when no matching index exists, then PostgreSQL if enabled. `getByIndex` and `getAllByIndex` resolve IDs from runtime indexes.

The holder calls the write authority before applying local property changes. With Redis, the successful script advances the version, publishes the origin, kind and changes with that version, and Core queues PostgreSQL persistence when enabled. Core filters events from its own Redis driver instance. Without Redis, the repository is the synchronous authority. A Redis SAVE can create a missing hash. Core queues SQL copies with expected version `0`, keeping the two version counters independent.

`increment`, `decrement` and `decrementIfAtLeast` operate on unconverted `Long` properties. The guarded decrement returns `false` when the resulting value would fall below the floor. The authority checks the floor atomically. A refused debit leaves the held property's value unchanged and produces no persistence write or event.

Other-instance events for held entities are processed in order on the holder's event executor. Version gaps and SAVE events at or below the tracked version queue a reload. Reload reads Redis directly before falling back through the lookup, reads the entity and version together, and installs the snapshot only if the tracked version has stayed the same. Remote DELETE evicts the held object, removes its runtime index entries and marks the ID deleted. See [Database-Holder](Database-Holder/README.md#remote-events-and-refresh).

## Schema and coalescing

Schema synchronization creates tables, missing columns and declared BTREE, UNIQUE or BRIN indexes under an advisory lock. Use explicit migrations for renames, removals, type changes and changes to existing indexes.

`InFlight` shares one active load per UUID and requested tier set within a lookup instance. Holders without REDIS load from the repository without shared marker coordination. Holders with REDIS and a configured marker can also share repository results across instances, including empty results. Shared marker keys include tenant, table and UUID. See [Database-Lookup](Database-Lookup/README.md#shared-loads).

## Building and testing

From the repository root:

```shell
mvn -pl Database/Database-Core -am test
```

Verification suites live under `Database-Holder/src/test/java` and `Database-Core/src/test/java`. JUnit and Mockito cover contracts and isolated behavior. Testcontainers provides PostgreSQL and Redis for integration tests. The Core suite also includes benchmarks, convergence tests and a mixed-workload soak. Select a holder tier set that matches the deployment when comparing results.

Return to [Foundation](../README.md).

