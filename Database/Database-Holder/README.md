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
public final class ExampleHolder extends EntityHolder<ExampleEntity, ExampleRepository> {

    public ExampleHolder(final ExampleRepository exampleRepository, final HolderContextFactory holderContextFactory) {
        super(ExampleEntity.class, exampleRepository, holderContextFactory, Set.of(Tier.LOCAL, Tier.REDIS, Tier.POSTGRES), ExampleProperty.class, ExampleIndex.class);
    }
}
```

Declarations initialize before factory.create(repository, tiers). The constructor then registers the holder. getRepository() retains the concrete repository type. Core's factory intersects the requested tiers with database-level enabled tiers.

## Lookups and holding

get(id) checks resolved tiers; get(id, tiers) requests a specific set. getByProperty uses a matching unique index if one exists. On an index miss it skips the held scan and queries PostgreSQL when enabled, warming indexes from the result. Without a matching index it scans held entities before SQL.

getByIndex and getAllByIndex have no SQL fallback. Grouped results omit IDs whose entity lookup returns empty; this does not remove the stale index member. hold loads, inserts into Local when present, indexes and subscribes. holdAll queries every row and takes each. getHeld returns a list snapshot of Local values. Without LOCAL, holds is false and getHeld is empty, although take still creates index entries and a subscription. take initializes the writer version from the context version callback before indexing and subscribing.

`release` unindexes by UUID, removes any Local entry and unsubscribes. It clears a live tracked version and retains a delete marker. Holders without LOCAL still use UUID-based index and subscription cleanup.

## Writing entities

On a Redis hit, hold(id) reads the entity and version in one snapshot. holdAll reads the repository, obtains versions for the loaded IDs and submits their subscriptions as one collection. With bounded LOCAL storage, Core wires eviction to release the ID.

`save` produces SET changes for persistent properties. `set` writes one property. The writer uses 1,024 stripes and calls the authority before assigning a local property and uses the returned version to decide whether to apply the result. With Redis and POSTGRES, Core submits successful writes to the PostgreSQL queue. Holders update affected runtime indexes and request a reload when a write uses a different object from the held copy.

Use unconverted `Long` properties for counters. `increment` and `decrement` return the new value. `decrementIfAtLeast(entity, property, delta, floor)` returns whether the authority accepted the debit. It accepts a result equal to the floor. A refusal keeps the Java property unchanged and produces no persistence write. In the Redis path, an accepted debit publishes and persists as a negative increment.

Hold and reload read the Redis hash version when that storage is available. A plain `get` leaves tracking alone. An ordinary write with no tracked version can fall through to PostgreSQL when the hash is absent. A guarded decrement requires a Redis hash when Redis is the authority. A delete marker rejects later writes on the deleted entity. A successful SAVE can recreate it.

`delete` calls the authority and releases the ID. `reindex` updates runtime keys. Use direct setters to construct values before a full save.

## Remote events and refresh

Core filters own-origin messages. The holder dispatches events on a single FIFO executor. For held entities, the next version applies its SET and INCREMENT changes and refreshes runtime index keys. Duplicate UPDATE versions are ignored. DELETE evicts the held object, unindexes the ID and leaves a delete marker before version checks for other write kinds.

A version gap, a failed event application or a SAVE at or below the tracked version queues a reload on the holder's refetch executor. A reload checks that the ID is still held. It reads the Redis entity and version from one hash snapshot and falls back through the Redis/POSTGRES lookup on a miss. Under the state lock it installs the result only if the tracked version has stayed the same. This also lets a SAVE that recreated a hash at a lower version refresh the held copy.

`HoldRefresher` uses a daemon scheduler to refresh Redis entity and unique-index TTLs for held IDs. Each tick passes the held list to the refresher callback. The Core callback batches entity expiry and refreshes unique indexes by collection. `close()` stops the refresher and shuts down both event and refetch executors. Core closes its registered holders during shutdown.

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
