<h1 align="center">Database-Redis</h1>

<p align="center">Shared storage, atomic hash writes, origin-tagged events, and load markers.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database-Redis supplies Lettuce drivers, generic string storage, entity hash codecs, Lua writes and shared read coordination. Core selects Redis authority per holder when that holder resolves a REDIS tier and a driver is supplied.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Connections and settings](#connections-and-settings)
- [Storage and codecs](#storage-and-codecs)
- [Entity writes](#entity-writes)
- [Events and scripts](#events-and-scripts)
- [Expiry and load markers](#expiry-and-load-markers)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Area | Types |
| --- | --- |
| Connections | RedisDriver, RedisSettings, RedisSubscriptions |
| Storage | RedisStorage, RedisStorageProvider, RedisEntityStorage |
| Serialization | ValueCodec, EntityCodec, RedisEvents |
| Writes | RedisEntityWriter, RedisScript |
| Read coordination | RedisMarker |

## Connections and settings

RedisSettings(host, port, username, password, database, timeout) supports ACL authentication. A five-argument overload omits username. connect() opens command and Pub/Sub connections; disconnect() closes them and the client.

RedisDriver exposes sync/async commands and a random instanceId. Core uses that ID as the event origin. Use a distinct driver per independent instance.

RedisSubscriptions keeps one consumer per channel, so another subscribe replaces the earlier consumer. attach resubscribes registered channels. Callbacks execute on the Pub/Sub connection thread. Subscribe is synchronous; unsubscribe is asynchronous and logs completion failures without blocking that callback thread. Core gap reload now runs on a separate holder executor and does not unsubscribe.

## Storage and codecs

Generic RedisStorage stores converter-serialized strings. RedisEntityStorage stores one hash field per encoded property. Paths are namespace plus encoded key; entity channels append :events. Core prefixes namespaces with the instance tenant when set. Both stores reject values() enumeration. RedisEntityStorage.version(id) reads the infrastructure version hash field, returning 0 when absent. Core uses it to initialize holder versions on hold and reload.

EntityCodec includes persistent and memory properties, omitting nulls on encode. Hash puts remove declared property fields omitted by the codec before HSET, so replacing a value with null clears its previous field. Scripted null SET uses UNSET and removes that field.

ValueCodec supports strings, UUIDs, boxed numeric/boolean/character values, BigDecimal, BigInteger, Instant and enum names. Other values require a compatible static valueOf(String) or ValueConverter. PostgreSQL has a different stored-type set. Raw byte arrays have no built-in Redis string parser; supply a converter, such as a Base64 String representation.

## Entity writes

The Lua write script requires an existing hash. Missing hashes return zero without creating fields, updating TTL or publishing. SAVE and UPDATE share this script; save does not create a new Redis entity.

For an existing hash, a positive expected version must match. A mismatch becomes VersionConflictException before fields, version or TTL change. Zero skips the comparison. SET writes a value; null produces UNSET/HDEL; INCREMENT uses HINCRBY and a missing counter starts at zero.

A successful script increments version, refreshes a positive TTL and publishes version, origin, WriteKind and encoded changes. Concurrent scripts update counters atomically. This does not synchronize the held Java object or guarantee PostgreSQL replication.

DELETE removes the hash and publishes expected version, origin and the DELETE kind, with no changes. It does not compare expected version or increment it. EventApplier handles DELETE before its duplicate-version check, evicting the remote held copy, runtime indexes and tracked version even at the same version.

Core optionally persists a copy with expected version 0 to PostgreSQL, immediately or through the batch queue. Redis remains the authority; its version is not used as a SQL comparison predicate. The queue is in memory and is not durable.

## Events and scripts

The wire format has three header lines followed by zero or more changes:

```text
version
origin-instance-id
SAVE | UPDATE | DELETE
column<TAB>change-type<TAB>encoded-value
```

Additional changes occupy additional lines. Values use Base64, with ~ reserved for null. Empty strings encode as an empty value and remain distinct from null. RedisEvents.origin reads the origin; decode returns a ChangeEvent containing key, version, kind and changes. Older payloads without the kind header do not match this format.

Core's ContextFactory drops messages whose origin equals its RedisDriver instanceId before decoding/application. Other origins reach EventApplier. Raw RedisDriver subscribers do not automatically get this filter.

Event value encoding uses String.valueOf rather than universally using property converters. Pub/Sub has no replay or delivery guarantee after disconnect. RedisScript caches a loaded SHA and reloads on NOSCRIPT, including after SCRIPT FLUSH. Other connection or command failures propagate; the wrapper does not retry them. Losing the command connection leaves the caller unable to infer whether Redis committed a write.

## Expiry and load markers

Positive TTLs apply to entity and generic puts. Zero TTL skips expiry there, but refresh unconditionally uses PEXPIRE, so zero TTL deletes a refreshed key. Held Local entities keep their Redis hashes and unique indexes alive through HoldRefresher.

A REDIS/POSTGRES miss can fall through to PostgreSQL and repopulate Redis. A LOCAL hit returns immediately and does not repair an expired Redis hash.

RedisMarker uses SET NX PX with the driver's instanceId. Waiters poll claim/result keys every 5 ms; published results live two seconds. Claim release checks the stored instance ID. Core load keys use table and UUID without a tenant prefix. Markers coordinate reads, not writes.

## Installation and build

Compile dependencies: Database-API and Lettuce 7.8.0.RELEASE. The parent also supplies SLF4J and provided Lombok. Test-only dependencies do not become part of the runtime.

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-Redis</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

From the repository root, using JDK 25 and Maven:

```shell
mvn -pl Database/Database-Redis -am install
```

See the [Database guide](../README.md) for generic examples and test commands. Return to [Foundation](../../README.md).
