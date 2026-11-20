<h1 align="center">Database-Postgres</h1>

<p align="center">PostgreSQL connections, repositories, schema creation, and versioned writes.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database-Postgres supplies persistence through jOOQ, HikariCP and PostgreSQL JDBC. Extend PostgresRepository to select entity, table, declarations and SQL indexes.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Connections](#connections)
- [Repositories and queries](#repositories-and-queries)
- [Schema synchronization](#schema-synchronization)
- [Column types](#column-types)
- [Writes and tenants](#writes-and-tenants)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Responsibility |
| --- | --- |
| `PostgresDriver`, `PostgresSettings` | Pool and database configuration |
| `PostgresRepository` | Queries, count, writes and schema statements |
| `PostgresRow` | UUID construction and property mapping |
| `PostgresQuery`, `PostgresWrite` | Query translation and persistence |
| `SchemaDefinition`, `ColumnTypes` | DDL and stored types |
| `PostgresSynchronizer` | DDL under an advisory lock |
| `PostgresIndexType` | Repository index declarations |

## Connections

PostgresSettings(host, port, database, username, password, maximumPoolSize) builds jdbc:postgresql://<host>:<port>/<database>. connect() creates a Hikari pool and PostgreSQL DSL context. getDslContext() throws ConnectionException before connection; disconnect() closes the pool. isConnected() checks pool existence and closed state. Hikari can recover connections when the same PostgreSQL endpoint returns after an outage; failed batched writes remain in the queue until a successful flush. No driver reconnect call is required for that pool recovery.

On SQL state 3D000, the driver connects to the postgres maintenance database and attempts CREATE DATABASE. The user requires CREATEDB for that path. Concurrent database creation retries SQL states 55006 and 23505 up to ten attempts, with a 250 ms delay. Duplicate database state 42P04 is accepted. Pre-create the database when application credentials should not create databases.

## Repositories and queries

The protected constructor takes entity class, table, driver, tenant scope and declaration classes. It rejects an entity with no registered properties and requires its UUID-only constructor for decoding. Use the database instance's getTenantScope() when constructing the repository. See [generic examples](../README.md#generic-examples).

findById returns Optional. find(Query) applies AND conditions, ordering, positive limit and positive offset. count(Query) applies conditions and tenant filtering, ignoring pagination/order. IN expects a Collection. Query values bypass property converters.

PostgresRow precomputes typed fields in PostgresColumn for persistent properties. findById and find select the mapped columns, and row decoding reuses those fields. Memory fields keep constructor/default values. Direct repository reads do not hold entities or update runtime indexes.

## Schema synchronization

Core collects getStatements() before starting schedulers. Synchronization holds advisory lock 7041975 and unlocks in finally, without a single migration transaction.

New tables contain id, version bigint NOT NULL DEFAULT 0 and persistent columns. Non-tenanted tables use id uuid PRIMARY KEY; tenanted tables use PRIMARY KEY (tenant, id). Startup adds missing property/tenant columns and creates declared indexes. It does not rename/drop columns, change types, backfill data or repair missing version columns on old tables.

Override getIndexes() with property-to-PostgresIndexType mappings. UNIQUE creates a unique index; BTREE an ordinary index. BRIN emits USING brin. Existing index names are protected by IF NOT EXISTS; changing an existing index method requires a separate migration. Tenanted indexes place tenant before the property. Runtime API indexes do not generate SQL indexes.

DDL interpolates names; keep them application-owned, simple SQL-safe identifiers. Reserve id, version and tenant.

## Column types

| Stored Java type | PostgreSQL |
| --- | --- |
| UUID | uuid |
| String, enum | text |
| Boolean | boolean |
| Short, Integer, Long | smallint, integer, bigint |
| Float, Double | real, double precision |
| Instant | timestamptz |
| byte[] | bytea |
| BigDecimal, BigInteger | numeric |

Converters select schema type through getStoredType(). Unsupported types throw SchemaException. PostgresValues writes enums as names and reads them from text. Instant values are written as UTC OffsetDateTime and converted back on reads. Both mappings preserve null values. BigDecimal preserves scale through the repository. For `BigInteger`, `PostgresValues` reads `numeric` as `BigDecimal` and converts it to `BigInteger` before row assignment or converter deserialization. PostgreSQL rejects ADD/REMOVE collection changes; use child entities.

## Writes and tenants

SAVE upserts by UUID, or by tenant and UUID when tenanted. An insert starts at expected version plus one. On conflict, SAVE increments the existing row's version. UPDATE applies changes and increments the row version. Positive expected versions add a version predicate to UPDATE and DELETE. If no row matches, an ordinary UPDATE or a versioned DELETE throws `VersionConflictException`.

Counter arithmetic treats SQL NULL as zero. A guarded decrement adds `coalesce(column, 0) - delta >= floor` to the UPDATE predicate. The operation returns a refusal when the UPDATE matches no row.

Core derives the instance tenant scope from DatabaseSettings.tenant. Reads resolve the repository's supplied scope; writes use Write.key.tenant. New tenanted tables use a composite (tenant, id) primary key, and tenanted SAVE uses that same conflict target. The same UUID can identify independent rows in different tenants. Existing tables need an explicit primary-key migration; additive startup does not replace an old UUID-only key. A no-tenant scope does not filter out tenanted rows.

Null and empty String values remain distinct through PostgreSQL SET and row decoding. A DELETE with expected version 0 remains unchecked when no row matches. Core uses expected version 0 for PostgreSQL replication of Redis-authoritative writes, so Redis and SQL version counters are not compared.

There is no transaction spanning multiple holder operations. Use the DSL context for application-specific database transactions.

## Installation and build

Compile dependencies: Database-Tenant, jOOQ 3.21.3, HikariCP 7.1.0 and PostgreSQL JDBC 42.7.13. Parent dependencies also supply SLF4J and provided Lombok.

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-Postgres</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

From the repository root, using JDK 25 and Maven:

```shell
mvn -pl Database/Database-Postgres -am install
```

See the [Database guide](../README.md) for generic wiring and test commands. Return to [Foundation](../../README.md).
