<h1 align="center">Database-Core</h1>

<p align="center">PostgreSQL persistence, schema synchronization, repositories, and cache-aware holders.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://www.postgresql.org/"><img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-Persistence-4169E1?logo=postgresql&logoColor=white"></a>
  <a href="https://redis.io/"><img alt="Optional Redis" src="https://img.shields.io/badge/Redis-Optional-DC382D?logo=redis&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven library" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database-Core assembles the [API](../Database-API/README.md), [storage](../Database-Storage/README.md), and [lookup](../Database-Lookup/README.md) layers into a persistence runtime. PostgreSQL access uses jOOQ, HikariCP, and JDBC. Redis adds shared caching and coordination when configured; application wiring can be manual, through Injector, or through Spring.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Connect and start](#connect-and-start)
- [Repositories and holders](#repositories-and-holders)
- [Holder modes and cache consistency](#holder-modes-and-cache-consistency)
- [Writes, durability, and failures](#writes-durability-and-failures)
- [Optimistic locking and atomic operations](#optimistic-locking-and-atomic-operations)
- [Schema and value mapping](#schema-and-value-mapping)
- [Database contexts and tenants](#database-contexts-and-tenants)
- [Installation and testing](#installation-and-testing)

</details>

## At a glance

| Type | Responsibility |
| --- | --- |
| `CoreDatabase` | Repository registration, schema startup, queue lifecycle, and failure reporting |
| `GlobalDatabase` / `LocalDatabase` | Distinct injectable database contexts with independent queues and drivers |
| `PostgresDriver` / `PostgresSettings` | Connection pool and jOOQ context |
| `AbstractEntityRepository<Entity>` | PostgreSQL reads, schema metadata, and queued writes |
| `AbstractEntityHolder<Entity, Repository>` | Cache-aware lookups, tracking, claims, and mutations |
| `BatchQueue` | Coalesce and flush writes in transactional chunks |
| `CacheInvalidationOutbox` | Persist invalidation events with committed changes and retry Redis delivery |
| `JsonValueConverter` / `EnumValueConverter` | Built-in value conversion |

## Connect and start

Define the entity and properties as shown in the [Database quick start](../README.md#quick-start). Connect drivers, create the database, construct repositories and holders, then start the runtime:

```java
final PostgresDriver postgresDriver = new PostgresDriver(new PostgresSettings(
        "localhost",
        5432,
        "app",
        "postgres",
        "password",
        10
));
postgresDriver.connect();

final CoreDatabase coreDatabase = new CoreDatabase(
        postgresDriver,
        null,
        new Tenant("api", 1),
        Duration.ofMillis(250),
        500,
        10_000
);
coreDatabase.setFailureHandler(databaseException -> databaseException.printStackTrace());

final AccountRepository accountRepository = new AccountRepository(coreDatabase);
final AccountHolder accountHolder = new AccountHolder(accountRepository);
coreDatabase.start();

try {
    final Account account = new Account(UUID.randomUUID());
    account.setName("alice");
    accountHolder.save(account);
} finally {
    coreDatabase.stop();
    postgresDriver.disconnect();
}
```

The password is an example connection value. Supply deployment settings from application configuration. This example disables Redis for the holder shown below.

All three database classes currently use the same six constructor arguments:

| Argument | Purpose | Example |
| --- | --- | --- |
| `postgresDriver` | Connected PostgreSQL driver | Application-owned driver |
| `redisDriver` | Optional connected Redis driver | `null` for PostgreSQL-only use |
| `tenant` | Tenant identity for scoped repositories | `new Tenant("api", 1)` |
| `flushInterval` | Delay between background flushes | `Duration.ofMillis(250)` |
| `chunkSize` | Maximum writes in a transaction chunk | `500` |
| `maximumPendingWrites` | Outstanding distinct write-key capacity | `10_000` |

The numeric values above are explicit application choices. The current database constructors require them; there are no shorter database constructor overloads. A tenant may be null only when repositories use `TenantScope.NONE`.

`start()` synchronizes registered schemas and starts the queue. `stop()` stops background work and attempts to drain pending writes. Drivers remain application-owned and must be disconnected afterwards. Repositories created after startup are synchronized as they register.

## Repositories and holders

```java
public final class AccountRepository extends AbstractEntityRepository<Account> {

    public AccountRepository(final CoreDatabase coreDatabase) {
        super(coreDatabase, Account.class, "accounts", TenantScope.NONE, false, AccountProperties.class);
    }

    @Override
    public Map<EntityProperty<? super Account, ?>, IndexType> getIndexes() {
        return Map.of(AccountProperties.NAME, IndexType.UNIQUE);
    }
}

public final class AccountHolder extends AbstractEntityHolder<Account, AccountRepository> {

    public AccountHolder(final AccountRepository accountRepository) {
        super(
                accountRepository,
                InstanceMode.SINGLE_INSTANCE,
                RedisMode.DISABLED,
                Duration.ofMinutes(30),
                Duration.ofHours(1)
        );
    }
}
```

The repository constructor takes the database, entity class, table name, tenant scope, optimistic-locking flag, and property-holder classes. Pass every property-holder class required to initialize the registry. A database rejects duplicate repository registrations for the same entity type.

Repository queries read PostgreSQL directly. Holder lookups use cache tiers and track loaded entities. Mutate entities through the holder when those entities can be cached: direct repository writes bypass holder cache coordination and can leave cached values stale.

| Holder method | Purpose |
| --- | --- |
| `getById`, `getByProperty`, `getByIndex` | Read through the selected tiers |
| `save` | Cache and track a new entity, then queue an insert |
| `update` | Queue changed persistent properties for an existing entity |
| `delete` | Remove the entity and queue deletion |
| `cache`, `evict` | Manage local retention; eviction first processes tracked changes |
| `pin`, `unpin`, `getPinned` | Retain single-instance entities until unpinned |

The holder registers a pre-flush task that detects dirty tracked values, including entries expiring from local storage. Explicit `update` makes the mutation intent clear and performs the holder's checks immediately.

## Holder modes and cache consistency

| Mode | Redis requirement | Local cache | Pinning |
| --- | --- | --- | --- |
| `SINGLE_INSTANCE` | Optional; supports `RedisMode.DISABLED` | Fixed expiry | Supported |
| `MULTI_INSTANCE` | `RedisMode.ENABLED` and a configured Redis driver | Sliding idle expiry, bounded by a five-minute maximum age | Unsupported |

`RedisMode.DISABLED` keeps a single-instance holder's cache, claim, and write-invalidation path off Redis even when its database has a Redis driver for other holders. Multi-instance configuration without Redis is rejected during holder construction.

Committed holder writes record cache invalidations in the same PostgreSQL transaction. An outbox dispatcher removes stale Redis entries and notifies peer multi-instance holders to evict local entries. Events survive Redis dispatch failures; reconnect handling and absolute cache age help recover from missed notifications.

Cache visibility is eventual. A successful mutation call may still be waiting in the write queue, and a committed change may briefly coexist with a stale peer cache. Choose the lookup tiers and concurrency strategy to fit the application's consistency requirements.

## Writes, durability, and failures

| Behavior | Result |
| --- | --- |
| Insert-only `save` | An existing UUID is rejected rather than overwritten |
| Update-only `update` | A missing row is not recreated |
| Coalescing | Pending changes to one entity merge by column where supported |
| Transactional chunks | Each chunk commits independently |
| Temporary PostgreSQL failure | Writes remain pending for a later flush |
| Permanently rejected write | Isolate the failing write, continue other work, and report failure |
| Queue capacity reached | A new distinct write throws `DatabaseException` synchronously |
| Shutdown | Up to ten drain attempts, then report remaining writes |

The queue is in memory. Enqueueing is not a durability acknowledgment, and process termination can lose uncommitted writes. Reads through the repository see committed PostgreSQL data. `getBatchQueue().flush()` requests an immediate flush but temporary failures can leave writes pending; observe failure reporting and queue state when a workflow needs to establish completion.

Set `setFailureHandler(...)` before starting work. The default handler performs no action. Background failures, including optimistic-lock conflicts, are reported there. Outstanding callback registrations are also bounded, with a current default of 100,000 across queued and in-flight work.

## Optimistic locking and atomic operations

Set the repository constructor's `optimisticLocking` argument to `true` to require the expected revision on changed writes. Foundation stores revisions internally and in a PostgreSQL `revision` column; entity classes need no revision field.

A stale snapshot produces `OptimisticLockException` through failure reporting. Reload or merge before retrying. Locking detects conflicts rather than automatically merging edits. Avoid reusing a deleted UUID while old instances of the entity may still exist, because revisions restart for recreated rows.

| Holder operation | Guarantee |
| --- | --- |
| `increment(entity, property, delta)` | PostgreSQL atomic numeric update and returned committed value |
| `transferUniqueProperty(claimantId, currentOwnerId, property, claimedValue, replacementValue)` | Transfer a persistent unique value between two existing entities in one transaction |

Both operations first flush pending writes on their database queue and reject the atomic operation if the queue cannot drain. Increment supports unconverted persistent `Long` or `Integer` values that do not participate in unique or composite indexes. A transfer verifies ownership, checks conflicts and revisions, and records both invalidations in the transaction.

## Schema and value mapping

Startup creates missing tables and columns. Removed properties leave their columns intact. Foundation-managed indexes are synchronized with repository declarations; separately created indexes are retained. `GIN_TRGM` declarations require PostgreSQL to enable `pg_trgm`.

| Java value | PostgreSQL type |
| --- | --- |
| `String`, enum | `varchar` |
| `UUID` | `uuid` |
| `Long`, `Integer`, `Short`, `Byte` | `bigint`, `integer`, `smallint`, `smallint` |
| `Double`, `Float` | `double precision`, `real` |
| `Boolean` | `boolean` |
| `BigDecimal`, `BigInteger` | `numeric` |
| `Instant` | `timestamp with time zone` |
| `byte[]` | `bytea` |
| `JsonValueConverter` values | `jsonb` |

Use `JsonValueConverter.of(type)`, `ofList(type)`, `ofSet(type)`, or `ofMap(keyType, valueType)` for structured values. Mapping support for PostgreSQL and Redis is separate; see the [storage codecs](../Database-Storage/README.md#codecs) when enabling Redis.

## Database contexts and tenants

`CoreDatabase`, `GlobalDatabase`, and `LocalDatabase` share the implementation. The latter two provide distinct injectable types so repository constructors can select an intended database when an application has both.

Each context has its own driver, queue, repository registry, and revision state. Configure the destination database through its `PostgresSettings`; choose `TenantScope` separately for row partitioning, and `InstanceMode` separately for holder coordination.

Redis keys include the table and tenant, not the PostgreSQL database name. Use separate Redis databases or distinct namespaces if independent PostgreSQL contexts contain the same table and tenant identifiers.

See [dependency injection](../README.md#dependency-injection) for Injector and Spring wiring.

## Installation and testing

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-Core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Core includes the other Database modules, jOOQ, HikariCP, PostgreSQL JDBC, Lettuce, and Gson transitively. From the repository root with JDK 25 and Maven:

```shell
mvn -pl Database/Database-Core -am install
```

Integration tests use real PostgreSQL and Redis. Follow the [isolated test setup](../README.md#building-and-testing) for Docker Compose, connection properties, multi-JVM checks, and opt-in recovery tests. Unreachable-service tests can be skipped, so inspect the skipped count when verifying persistence behavior.
