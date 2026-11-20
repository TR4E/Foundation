<h1 align="center">Database-Batch</h1>

<p align="center">Write coalescing, merge precedence, and scheduled persistence.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database-Batch keeps pending writes in memory by EntityKey, combining changes before passing them to a writer callback. Core uses it for PostgreSQL persistence behind Redis authority.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Queue and merge rules](#queue-and-merge-rules)
- [Scheduling and failures](#scheduling-and-failures)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Responsibility |
| --- | --- |
| BatchQueue | Pending writes, merging and conditional drain |
| ChangeMerge | Per-column SET/INCREMENT merging |
| BatchScheduler | Periodic flush, requeue and final shutdown attempt |

## Queue and merge rules

Keys include table, tenant and UUID. size() counts pending keys, not individual changes. drain() walks pending keys and returns only entries it atomically removes. Concurrent drainers cannot return the same entry, though their writer callbacks can complete in a different order.

The table shows the resulting kind for an existing write followed by an incoming write:

| Existing / incoming | SAVE | UPDATE | DELETE |
| --- | --- | --- | --- |
| SAVE | SAVE | SAVE | DELETE |
| UPDATE | SAVE | UPDATE | DELETE |
| DELETE | SAVE | DELETE | DELETE |

An incoming SAVE or DELETE replaces the prior write. An UPDATE after DELETE keeps DELETE. Other incoming UPDATEs merge changes, retain the existing kind and use the incoming expected version.

For one column, SET replaces prior changes. INCREMENT after SET adds its Long delta and keeps SET, treating a null SET as zero. Repeated INCREMENTs sum. ADD/REMOVE append separately. Arithmetic merging expects Long values.

Preserving SAVE keeps a pending insert when UPDATE follows it. BatchQueue itself retains the incoming expected version when merging. Core converts Redis-authoritative writes to expected version 0 before queueing them for PostgreSQL, so Redis versions are not used as PostgreSQL comparison predicates. A custom writer using BatchQueue directly must choose its own version policy.

## Scheduling and failures

`BatchScheduler(queue, writer, interval)` runs fixed-delay flushes on the daemon Database-Batch thread. Each flush snapshots the pending keys and submits one write per key to an eight-worker pool. The scheduler joins the submitted tasks and keeps the persistence lock around the pass. Size the PostgreSQL pool for at least eight connections when using this flush path. A failed open-queue write merges back as the older entry, so a pending newer write keeps its ordering. The failed key waits for a later pass. Core routes writes by table.

`stop()` is idempotent. It shuts down the scheduled worker, waits up to five seconds and interrupts the worker if needed. It then closes the queue and makes a final flush attempt with a five-second deadline. The deadline bounds the wait for the persistence lock and the time for starting new writes in that pass. The warning counts keys still in the queue. Submitted tasks have already left the queue. A persistence call already in flight can outlast the deadline.

A closed queue sends new `add` calls to its synchronous writer. `requeue` returns false after close, so a failed write during final flush is logged as dropped. Pending writes live in memory. Core drains its write gate before calling the scheduler's shutdown. See [Database-Core](../Database-Core/README.md#lifecycle).

## Installation and build

Compile dependencies: Database-API. The parent also supplies SLF4J and provided Lombok. Test-only dependencies do not become part of the runtime.

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-Batch</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

From the repository root, using JDK 25 and Maven:

```shell
mvn -pl Database/Database-Batch -am install
```

See the [Database guide](../README.md) for generic examples and test commands. Return to [Foundation](../../README.md).
