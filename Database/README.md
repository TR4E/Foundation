<h1 align="center">Database</h1>

<p align="center">Per-holder persistence, shared Redis authority, and explicit DI wiring.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Multi--module-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database is Foundation's Java 25 persistence stack. PostgreSQL supplies durable repositories and additive schema setup. Holders are DI beans extending EntityHolder with entity and repository type parameters. Each holder chooses its tier set; a database instance supplies the enabled tiers, tenant, drivers and lifecycle.

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

Core's ContextFactory intersects each holder's tiers with DatabaseSettings.tiers. Database-level tiers enable availability; the holder chooses its access mode.

| Holder tiers | Entity storage | Write authority | PostgreSQL persistence |
| --- | --- | --- | --- |
| LOCAL, POSTGRES | Explicitly held Local objects | Repository | Synchronous, once |
| REDIS, POSTGRES | Shared Redis hashes | Redis script | Queued |
| LOCAL, REDIS, POSTGRES | Local working set and Redis | Redis script | Queued |

A Redis driver must be supplied for Redis storage. Without LOCAL, holds is false and getHeld is empty. Unique index declarations inherit the holder's resolved tiers and omit POSTGRES providers. Grouped indexes are local maps and reject a resolved REDIS tier; explicitly place grouped working-set indexes in LOCAL.

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

    public static final Property<ExampleEntity, String> NAME = Property.persistent(
            ExampleEntity.class,
            "name",
            String.class,
            ExampleEntity::getName,
            ExampleEntity::setName
    );
    public static final Property<ExampleEntity, String> CATEGORY = Property.persistent(
            ExampleEntity.class,
            "category",
            String.class,
            ExampleEntity::getCategory,
            ExampleEntity::setCategory
    );

    public static final Property<ExampleEntity, Long> COUNTER = Property.persistent(
            ExampleEntity.class,
            "counter",
            Long.class,
            ExampleEntity::getCounter,
            ExampleEntity::setCounter
    );
}
```

```java
@UtilityClass
public final class ExampleIndex {

    public static final UniqueIndex<ExampleEntity, String> NAME = UniqueIndex.of(
            "example_name",
            ExampleProperty.NAME
    );

    public static final GroupedIndex<ExampleEntity, String> CATEGORY = GroupedIndex.of(
            "example_category",
            ExampleProperty.CATEGORY,
            Set.of(Tier.LOCAL)
    );
}
```

Runtime unique indexes map a value to an ID; they do not enforce database uniqueness. The repository declares SQL indexes separately:

```java
public final class ExampleRepository extends PostgresRepository<ExampleEntity> {

    public ExampleRepository(
            final PostgresDriver postgresDriver,
            final TenantScope tenantScope
    ) {
        super(
                ExampleEntity.class,
                "examples",
                postgresDriver,
                tenantScope,
                ExampleProperty.class
        );
    }

    @Override
    public Map<Property<?, ?>, PostgresIndexType> getIndexes() {
        return Map.of(ExampleProperty.NAME, PostgresIndexType.UNIQUE);
    }
}
```

The holder constructor declares its tiers and registers itself through the factory. A concrete holder must implement or explicitly reject the currently abstract floor-check operation.

```java
public final class ExampleHolder
        extends EntityHolder<ExampleEntity, ExampleRepository> {

    public ExampleHolder(
            final ExampleRepository exampleRepository,
            final HolderContextFactory holderContextFactory
    ) {
        super(
                ExampleEntity.class,
                exampleRepository,
                holderContextFactory,
                Set.of(Tier.LOCAL, Tier.POSTGRES),
                ExampleProperty.class,
                ExampleIndex.class
        );
    }

    @Override
    public boolean decrementIfAtLeast(
            final ExampleEntity exampleEntity,
            final Property<ExampleEntity, Long> property,
            final long delta,
            final long floor
    ) {
        throw new UnsupportedOperationException("Atomic floor checks require an implementation");
    }
}
```

Initialize non-null indexed values before saving/indexing. Memory properties are excluded from PostgreSQL full saves and row mapping, but remain part of Redis entity serialization.

## Database and DI wiring

CoreDatabase takes settings and drivers. Its tenant comes from DatabaseSettings, not a separate constructor argument.

```java
final DatabaseSettings databaseSettings = new DatabaseSettings(
        Tier.ALL,
        null,
        Duration.ofMinutes(5),
        Duration.ofMinutes(1),
        Duration.ofSeconds(1),
        Duration.ofSeconds(5),
        Duration.ofSeconds(2)
);
final CoreDatabase coreDatabase = new CoreDatabase(
        databaseSettings,
        postgresDriver,
        redisDriver
);
```

The settings arguments are enabled tiers, tenant, entity TTL, refresh interval, batch interval, marker lease and marker timeout. Null/blank tenant means no tenant. Pass null for redisDriver when Redis should not be used.

Expose PostgresDriver, coreDatabase.getTenantScope() and coreDatabase.getContextFactory() to your DI container. The container constructs ExampleRepository and ExampleHolder before Core starts. With [Foundation Injector](../Injector/README.md), explicitly supplied component classes work without additional annotations:

```java
final CoreInjector coreInjector = new CoreInjector();

coreInjector.provide(PostgresDriver.class, postgresDriver);
coreInjector.provide(TenantScope.class, coreDatabase.getTenantScope());
coreInjector.provide(HolderContextFactory.class, coreDatabase.getContextFactory());

try {
    coreInjector.initialize(
            application,
            List.of(ExampleRepository.class, ExampleHolder.class)
    );
    coreDatabase.start();

    System.out.println(coreInjector.get(ExampleHolder.class).getHeld().size());
} finally {
    coreInjector.shutdown(application);
    coreDatabase.stop();
}
```

Here application is an application-owned object annotated with Injector's Application annotation. Add Injector's artifacts when choosing this integration. Another container can supply the same constructor dependencies. Register one holder per entity class per database instance; the registry replaces duplicate class/table entries.

Core start connects drivers, applies registered repository DDL, and starts batching and refreshers. Stop consumers before Core shutdown. Core stop closes registered holders, stopping their refreshers and refetch executors; see [holder lifecycle](Database-Holder/README.md#remote-events-and-refresh).

## Read and write paths

ID reads check Local, Redis and PostgreSQL. Ordinary get does not create a new Local hold. hold/holdAll retain active entities; release removes remembered index entries by UUID, any Local retention, subscriptions and tracked version. It does not need a Local entity to perform cleanup.

getByProperty tries a matching unique runtime index, or a held scan when no matching index exists, then PostgreSQL if enabled. getByIndex/getAllByIndex have no SQL fallback.

Writes mutate the entity, call authority, record a positive returned version, dispatch persistence and update indexes. Redis authority publishes version, origin, WriteKind and changes. Core filters its own origin before event application, preventing a local write from applying its own published delta again.

Other-instance consecutive events update held objects. Gaps submit reloads to a separate executor without unsubscribing. Hold and reload initialize writer tracking through the context version callback, reading the Redis hash version when available. Without Redis, the callback returns 0. DELETE events have an eviction branch, but the duplicate-version check runs first and the delete writer currently publishes the supplied expected version without incrementing it.

Redis scripts require an existing hash; save does not bootstrap it. Successful repository ID reads can backfill Redis. Core copies Redis-authoritative writes with expected version 0 for immediate or batched PostgreSQL replication. Redis and SQL versions are not compared. The queue remains in memory, without durable replay.

## Schema and coalescing

Schema synchronization creates tables, missing columns and declared BTREE, UNIQUE or BRIN indexes under an advisory lock. It does not rename/drop columns, change stored types or migrate existing index methods.

InFlight shares one active load per UUID and requested tier set representation within one lookup instance. Stampede uses a shared marker to publish a result to concurrent waiters, including an empty result for a missing row. Timeout permits a fallback read; this is not global exactly-once loading.

In-process coalescing distinguishes requested tier selections, and release no longer performs a LOCAL-only lookup. Shared marker keys still omit tier selection and tenant. See [Lookup](Database-Lookup/README.md) for backfill and restriction details.

## Building and testing

From the repository root:

```shell
mvn -pl Database/Database-Core -am test
```

Verification suites live under Database-Holder/src/test/java and Database-Core/src/test/java. JUnit/Mockito cover contracts and isolated behavior; Testcontainers runs real PostgreSQL and Redis tests. Docker-dependent classes skip when Docker is unavailable. The Core suite also includes deterministic convergence tests and a sixty-second mixed-workload soak with allocation/GC measurements.

The suites cover regression contracts, concurrency, retention and convergence; they do not repair production code. Fixtures must use the current HolderContext and ChangeEvent constructor signatures and the current event headers. Stored reports describe the source at their run time, not subsequent edits.

Benchmark workloads are kept for comparison, but hand-built payloads must match the current protocol. The event header now requires version, origin and WriteKind. Older publish-benchmark payloads without those headers are incompatible.

Return to [Foundation](../README.md).
