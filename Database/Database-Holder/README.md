<h1 align="center">Database-Holder</h1>

<p align="center">DI holder beans, tracked writes, local retention, and remote changes.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database-Holder provides `EntityHolder<EntityType, RepositoryType>`. Each application holder owns a typed repository, a resolved tier set, runtime indexes, a version map and a local working set when LOCAL is enabled.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Creating a holder](#creating-a-holder)
- [Lookups and holding](#lookups-and-holding)
- [Writing entities](#writing-entities)
- [Remote events and refresh](#remote-events-and-refresh)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Responsibility |
| --- | --- |
| EntityHolder | Holding, reads, writes, index updates and asynchronous reload |
| HolderContext | Resolved storage, repository, callbacks and timing |
| HolderContextFactory | Per-holder context creation and registration |
| EntityWriter | Changes, authority/persistence dispatch and versions |
| EventApplier | Remote consecutive-version application and gap detection |
| HoldRefresher | Refresh expiry for held Redis entities/indexes |

## Creating a holder

A container constructs holders as ordinary beans. Pass the repository, factory, holder tier set and declaration classes to the superclass. Package declarations/imports are omitted.

```java
public abstract class ExampleHolder extends EntityHolder<ExampleEntity, ExampleRepository> {

    protected ExampleHolder(final ExampleRepository exampleRepository, final HolderContextFactory holderContextFactory) {
        super(ExampleEntity.class, exampleRepository, holderContextFactory, Set.of(Tier.LOCAL, Tier.REDIS, Tier.POSTGRES), ExampleProperty.class, ExampleIndex.class);
    }
}
```

This example is abstract because EntityHolder leaves Holder.decrementIfAtLeast unimplemented. A concrete bean must implement that operation or explicitly reject it. There is no supplied atomic floor check.

Declarations initialize before factory.create(repository, tiers). The constructor then registers the holder. getRepository() retains the concrete repository type. Core's factory intersects the requested tiers with database-level enabled tiers.

## Lookups and holding

get(id) checks resolved tiers; get(id, tiers) requests a specific set. getByProperty uses a matching unique index if one exists. On an index miss it skips the held scan and queries PostgreSQL when enabled, warming indexes from the result. Without a matching index it scans held entities before SQL.

getByIndex and getAllByIndex have no SQL fallback. hold loads, inserts into Local when present, indexes and subscribes. holdAll queries every row and takes each. getHeld returns a list snapshot of Local values. Without LOCAL, holds is false and getHeld is empty, although take still creates index entries and a subscription. take initializes the writer version from the context version callback before indexing and subscribing.

release unindexes by UUID, removes any Local entry, unsubscribes and clears the writer version. Cleanup does not require a Local entity, so it also removes remembered index entries for holders without LOCAL. release no longer performs a LOCAL-only lookup.

Local storage contains object references, not snapshots synchronized for application writes. A Local hit does not check or repair Redis expiry.

## Writing entities

save produces SET changes for persistent properties. set changes one property; increment/decrement operate on Long values. The writer first mutates the Java object, invokes authority, records a positive returned version and dispatches persistence. set/increment restore their previous Java value on runtime failure, without rolling back a backend write that already completed.

Holder operations update affected indexes after writing. delete writes deletion and calls release for UUID-based index, Local, subscription and version cleanup. reindex only maintains runtime keys. Direct setters bypass persistence and index maintenance.

Versions default to zero. hold/holdAll and asynchronous reload initialize the tracked version through HolderContext.version. Core reads that value from the Redis hash when Redis storage exists; without Redis its callback returns 0. A plain get does not itself initialize writer tracking. Zero skips positive-version comparisons in the current backends. Concurrent mutation of one held Java object is not made atomic by the Redis script.

## Remote events and refresh

Core filters own-origin messages before EventApplier. Without Local storage the applier does nothing. Versions at/below the tracked version are ignored; the next version applies SET/INCREMENT, records version and reindexes.

A gap submits a reload to the holder's daemon Database-Refetch executor. Reload reads Redis/PostgreSQL, replaces Local, reads the context version and reindexes, without unsubscribing.

ChangeEvent includes WriteKind. After the duplicate-version check, a DELETE event evicts Local, unindexes and clears the tracked version. That eviction does not unsubscribe. The duplicate check runs first: a delete carrying the same version as the tracked value is ignored. The Redis delete writer currently publishes the supplied expected version without incrementing it.

HoldRefresher uses a daemon scheduler to refresh Redis entity and unique-index TTLs for getHeld(). It does not poll PostgreSQL or replay events. EntityHolder.close() stops its refresher and shuts down its refetch executor. Core stop calls close() on its registered holders. Uncaught refresher exceptions can terminate subsequent scheduled runs.

## Installation and build

Compile dependencies: Database-Batch, Database-Index and Database-Lookup. The parent also supplies SLF4J and provided Lombok. Test-only dependencies do not become part of the runtime.

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-Holder</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

From the repository root, using JDK 25 and Maven:

```shell
mvn -pl Database/Database-Holder -am install
```

See the [Database guide](../README.md) for generic examples and test commands. Return to [Foundation](../../README.md).
