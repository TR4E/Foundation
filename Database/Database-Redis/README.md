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

EntityCodec includes persistent and memory properties, omitting nulls on encode. Hash puts remove declared property fields omitted by the codec before HSET, so replacing a value with null clears its previous field. Scripted null SET uses UNSET and removes that field. Hash puts skip empty HSET calls. Clearing the last property drops a hash with no metadata, while a version field can keep the hash readable.

ValueCodec supports strings, UUIDs, boxed numeric/boolean/character values, BigDecimal, BigInteger, Instant and enum names. Other values require a compatible static valueOf(String) or ValueConverter. PostgreSQL has a different stored-type set. Raw byte arrays use Base64, including an empty array stored as an empty string.

## Entity writes

SAVE and UPDATE use the same Lua script. SAVE can create a missing hash and publish the resulting version. For other kinds, a missing hash returns version `0` without creating a key or publishing. An unversioned hash can be seeded from a positive expected version. A positive expected version must match the current version once it is present. A mismatch throws `VersionConflictException`.

SET writes the encoded property value. A null SET uses HDEL. INCREMENT uses HINCRBY, with a missing counter treated as zero. The script projects all guarded changes before applying them. It refuses a debit below the floor and rejects out-of-range guarded arithmetic. Guarded operands and the current value must have magnitude below `2^53`.

A successful write advances the version, refreshes a positive TTL and publishes the version, origin, kind and changes. A successful guarded decrement publishes a negative INCREMENT. An arithmetic write returns the resulting counter as well as the version.

DELETE removes the hash and publishes the supplied expected version, origin and DELETE kind with no changes. A remote holder evicts a held copy and marks that ID deleted before it considers duplicate versions.

When POSTGRES is enabled, Core submits a copy with expected version `0` for SQL persistence. Redis and PostgreSQL maintain their own versions.

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

RedisEntityWriter encodes property SET values once for both the script and the event. Null remains null on the wire. INCREMENT keeps the numeric delta. Holder counter operations require unconverted `Long` properties. Pub/Sub has no replay or delivery guarantee after disconnect. RedisScript caches a loaded SHA and reloads on NOSCRIPT, including after SCRIPT FLUSH. Other connection or command failures propagate; the wrapper does not retry them. Losing the command connection leaves the caller unable to infer whether Redis committed a write.

## Expiry and load markers

snapshot(id) decodes the entity and its version from one HGETALL. The codec caches its column list, and the writer caches the property for each column.

RedisDriver.subscribe accepts a channel-to-listener map and sends its channels in one SUBSCRIBE. Bulk expiry runs on the driver?s batch connection. Each nonempty refreshAll batch queues PEXPIRE commands and flushes them together once. Core refreshes entity hashes and unique-index storage in separate batches.

Positive TTLs apply to entity and generic puts. Zero TTL skips expiry there, but refresh unconditionally uses PEXPIRE, so zero TTL deletes a refreshed key. Held Local entities keep their Redis hashes and unique indexes alive through HoldRefresher.

A Redis miss can fall through to PostgreSQL and backfill the hash when POSTGRES is enabled.

RedisMarker uses SET NX PX with the driver's instanceId. Waiters poll claim/result keys every 5 ms; published results live two seconds. Claim release checks the stored instance ID. Core uses shared markers for holders with REDIS and an available marker. Load keys include tenant, table and UUID.

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
