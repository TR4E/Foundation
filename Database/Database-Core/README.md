<h1 align="center">Database-Core</h1>

<p align="center">Database instances, per-holder contexts, and persistence lifecycle.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database-Core assembles PostgreSQL repositories, entity holders, Local storage, Redis, indexes and batching. Holders are application beans; their constructors use Core's HolderContextFactory to create their context and register themselves. Core has no runtime dependency on a DI container.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Construction and registration](#construction-and-registration)
- [Per-holder tiers](#per-holder-tiers)
- [Settings](#settings)
- [Read and write paths](#read-and-write-paths)
- [Lifecycle](#lifecycle)
- [Tenants and packaging](#tenants-and-packaging)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Responsibility |
| --- | --- |
| `CoreDatabase` | Drivers, instance tenant, schema and scheduler lifecycle |
| `DatabaseSettings` | Enabled tiers, tenant and durations |
| `ContextFactory` | HolderContextFactory implementation and holder registration |
| `EntityRegistry` | Entity-class lookup and table-based write dispatch |
| `NamespaceFactory` | Entity/index namespaces and tenant prefixes |
| `EntityPayloadConverter`, `UuidConverter` | Shared-load payloads and index UUID conversion |

## Construction and registration

Construct `CoreDatabase(settings, postgresDriver, redisDriver)`. Redis may be null. Core creates its TenantScope from settings. Use coreDatabase.getTenantScope() for repositories and coreDatabase.getContextFactory() for holders.

A DI container constructs each repository and holder before coreDatabase.start(). The holder constructor loads declaration classes, creates its context with its own tier set and registers through the factory. There is no separate CoreDatabase.register(...) operation.

```java
final ExampleRepository exampleRepository = new ExampleRepository(postgresDriver, coreDatabase.getTenantScope());
final ExampleHolder exampleHolder = new ExampleHolder(exampleRepository, coreDatabase.getContextFactory());
```

The [holder guide](../Database-Holder/README.md#creating-a-holder) defines the holder shape. DI providers should expose these same repository, scope and factory objects, with one lifecycle owner starting Core after bean construction.

The registry stores one holder/repository per entity class and one repository per table. Registering the same class or table again replaces those entries. Independent database instances have independent registries.

## Per-holder tiers

ContextFactory intersects the holder's tier set with DatabaseSettings.tiers. Database settings enable available tiers; the holder chooses which of them to use.

| Holder tiers | Behavior |
| --- | --- |
| LOCAL, POSTGRES | Local retention and synchronous PostgreSQL writes |
| REDIS, POSTGRES | Redis-first reads, Redis authority and queued PostgreSQL persistence |
| LOCAL, REDIS, POSTGRES | Local working set, Redis authority/events and queued PostgreSQL persistence |

Database settings control the available storage providers. Redis storage requires a Redis driver. Core connects PostgreSQL at startup. For a Spring `MULTI_INSTANCE` holder with shared entity storage, use `Set.of(Tier.REDIS, Tier.POSTGRES)`. The holder resolves IDs through Redis and PostgreSQL and has no Local entity store.

## Settings

DatabaseSettings has a single constructor, without defaults:

| Argument | Use |
| --- | --- |
| tiers | Available tiers intersected with each holder's set |
| tenant | Instance tenant; null/blank means no tenant |
| entityTtl | Redis entity and unique-index expiry |
| refreshInterval | Held-entry expiry refresh |
| batchInterval | PostgreSQL queue flush |
| markerLease | Shared-load claim duration |
| markerTimeout | Shared-load wait before direct fallback |

Use positive scheduler intervals and a positive Redis TTL longer than refreshInterval. Zero TTL skips expiry on puts but refresh still calls PEXPIRE with zero. Settings do not validate these combinations.

## Read and write paths

ID lookup checks LOCAL, REDIS and POSTGRES in that order for the enabled tiers. Repository results can backfill Redis and an existing local hold. `InFlight` shares simultaneous reads for the same UUID and requested tiers in the holder. `ContextFactory` supplies shared `Stampede` coordination when the resolved holder has REDIS and a marker is available. With no `Stampede`, `LoadGuard` calls the repository loader directly.

Without Redis entity storage, `repository.apply` is the synchronous authority. With Redis, Lua handles entity writes and publishes the version, origin, kind and changes for successful writes. Core filters messages whose origin is its own driver instance ID. If POSTGRES is enabled alongside Redis, Core queues the write with expected version `0`. A context without a batch queue applies that SQL copy immediately.

A Redis SAVE can create a missing hash. A Redis UPDATE returns zero for a missing hash. The holder can send an ordinary untracked UPDATE to PostgreSQL in that case. A tracked UPDATE or guarded decrement rejects a missing hash. Core reads the hash version for holds and reloads when Redis is available.

Redis and SQL keep separate version counters. The PostgreSQL queue is in memory. See [Database-Holder](../Database-Holder/README.md#writing-entities) and [Database-Redis](../Database-Redis/README.md#entity-writes).

## Lifecycle

`start()` connects drivers, applies registered repository DDL and starts the batch scheduler and holder refreshers. Construct holders before startup to register their schemas and refreshers.

`stop()` is idempotent. It closes admission to the write gate and waits up to five seconds for admitted operations before closing holders. It then stops the batch scheduler and gives its final flush a deadline before disconnecting the drivers. See [batch shutdown](../Database-Batch/README.md#scheduling-and-failures) for pending writes and retry behavior. Construct a new runtime for a new lifecycle.

`isReady()` checks driver connection state. The application owns startup failure cleanup.

## Tenants and packaging

Core derives TenantScope from DatabaseSettings.tenant. Entity and index namespaces use that tenant at context creation; write keys capture it too. Repository reads resolve their supplied scope per call.

Use one fixed tenant per Core instance. Changing the scope around an existing holder does not rebuild its namespace or captured write tenant. Shared-load keys include tenant, table and UUID. They resolve the active tenant at lookup time, while entity namespaces are captured when the holder is constructed. New tenanted SQL tables use a composite (tenant, id) primary key; old schemas require an explicit migration.

Depend on Core at compile scope when wiring refers to implementation classes. Core contains no service descriptor or automatic bean scanning; use the application's DI container.

## Installation and build

Compile dependencies: Database-Holder, Database-Local, Database-Postgres and Database-Redis. The parent also supplies SLF4J and provided Lombok. Test-only dependencies do not become part of the runtime.

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-Core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

From the repository root, using JDK 25 and Maven:

```shell
mvn -pl Database/Database-Core -am install
```

See the [Database guide](../README.md) for generic examples and test commands. Return to [Foundation](../../README.md).
