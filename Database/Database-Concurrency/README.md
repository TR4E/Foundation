<h1 align="center">Database-Concurrency</h1>

<p align="center">Per-key load coalescing and shared marker coordination.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database-Concurrency provides synchronous load coordination without depending on a storage implementation. [Lookup](../Database-Lookup/README.md) uses it for entity reads.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [In-process loads](#in-process-loads)
- [Shared loads](#shared-loads)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Responsibility |
| --- | --- |
| `InFlight<Key, Value>` | One active supplier per key per instance |
| `Stampede` | Shared claim, result publication and timeout fallback |
| `CoalescingException` | Failed or incomplete joined load |

## In-process loads

`InFlight.get(key, loader)` installs a CompletableFuture. The winning caller runs the supplier synchronously; other callers join it. Completed loads are removed in finally, including failures. Later calls load again, so this is not a cache.

Runtime failures propagate to waiters. Other incomplete exits complete waiters with CoalescingException. There is no join timeout or cancellation API.

## Shared loads

Construct `Stampede(marker, lease, timeout)` and call `load(key, converter, loader)`, with a string converter and an Optional-returning supplier.

A successful claim runs the supplier, publishes the serialized result and releases. An empty result publishes an empty string. Runtime failures release without a payload. A caller that cannot claim waits; timeout or absent payload falls back to its own supplier. This reduces duplicate reads but does not guarantee one global execution. Claims are not renewed during the supplier call; if a query outlives its lease, another instance can claim and run the same query concurrently.

[LocalMarker](../Database-Local/README.md#local-marker) always grants claims. [RedisMarker](../Database-Redis/README.md#expiry-and-load-markers) supplies shared leases. Keep marker keys unique across workloads. Core marker keys contain table and UUID, without tenant.

Published empty strings represent missing rows, so concurrent waiters can reuse a negative result without a second query. A later caller that successfully claims after release can query again; this is not a general negative cache. InFlight keys only by its supplied key. Lookup supplies a String combining UUID and the requested tier set representation, separating in-process loads for different tier selections. Core shared-marker keys still contain only table and UUID, without tier selection or tenant.

## Installation and build

Compile dependencies: Database-API. Parent dependencies also supply SLF4J and provided Lombok.

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-Concurrency</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

From the repository root, using JDK 25 and Maven:

```shell
mvn -pl Database/Database-Concurrency -am install
```

See the [Database guide](../README.md) for generic wiring and test commands. Return to [Foundation](../../README.md).
