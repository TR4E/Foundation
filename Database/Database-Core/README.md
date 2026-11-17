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
| REDIS, POSTGRES | Shared Redis authority and queued PostgreSQL persistence; no local hold |
| LOCAL, REDIS, POSTGRES | Local working set, Redis authority/events and queued PostgreSQL persistence |

Providers are created from database-level enabled tiers. A Redis driver is required for Redis storage. PostgreSQL connects during Core startup regardless of the resolved holder tier set. A supplied Redis driver also selects the shared Redis load marker, including for holders without a REDIS storage tier.

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

ID lookup checks LOCAL, REDIS and POSTGRES in that order. Local hits return immediately; successful repository reads can backfill Redis and an existing local hold. Holder contexts also expose a UUID-to-version callback: Redis contexts read the hash version; contexts without Redis return 0. Hold and reload use that callback to initialize tracked versions.

Without Redis entity storage, repository.apply is the authority and the persistence callback does nothing. There is one PostgreSQL write. With Redis entity storage, Lua is the authority; when POSTGRES is enabled, Core copies the write with expected version 0 and queues that copy for the repository. The same conversion applies if ContextFactory has no queue and persists immediately.

EntityWriter mutates the property, invokes authority, records a positive returned version, then calls persistence. Lua atomically changes the hash, increments version, refreshes TTL and publishes version, origin, WriteKind and changes. ContextFactory rejects events with its own RedisDriver instance ID before decoding or applying them.

Redis writes still require an existing hash and return zero when absent. The queue is process-local. PostgreSQL replication uses expected version 0, keeping Redis authority versions separate from SQL versions. There is no durable replay or transactional replication across both stores.

## Lifecycle

start() connects drivers, synchronizes registered PostgreSQL schemas, starts the batch scheduler and starts registered holder refreshers. Construct holders before start so schema and refresher registration are complete.

stop() calls close() on registered holders, stopping their refreshers and shutting down their refetch executors. It stops the batch scheduler, performs its stop-time flush and an additional explicit flush, then disconnects drivers. Failed writes can remain queued. stop does not wait for application writes between authority and persistence; a write can enqueue after the final drain. Stop and join application writers before stopping Core. Scheduler instances cannot restart after stop.

isReady() checks driver connection state only. It does not verify queue drainage, schema completion or executor health. Startup has no automatic rollback; the application owns cleanup.

## Tenants and packaging

Core derives TenantScope from DatabaseSettings.tenant. Entity and index namespaces use that tenant at context creation; write keys capture it too. Repository reads resolve their supplied scope per call.

Use one fixed tenant per Core instance. Changing the scope around an existing holder does not rebuild its namespace or captured write tenant. Shared-load keys currently contain table and UUID without tenant. New tenanted SQL tables use a composite (tenant, id) primary key; old schemas require an explicit migration.

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
