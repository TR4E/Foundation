<h1 align="center">Database</h1>

An entity persistence library for PostgreSQL, with an optional Redis layer for caching and coordination between servers. You describe an entity's properties once, and Database creates and migrates the table, writes changes behind the scenes in batches, serves reads from a local cache, Redis or PostgreSQL in that order, keeps unique values unique across every server, and separates data per tenant.

It is plain Java with no framework of its own, so it can be wired by hand, through [Injector](https://github.com/TR4E/Foundation/tree/master/Injector), or through Spring and Spring Boot. See [Dependency injection](#dependency-injection).

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://www.postgresql.org/"><img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-Persistence-4169E1?logo=postgresql&logoColor=white"></a>
  <a href="https://redis.io/"><img alt="Redis" src="https://img.shields.io/badge/Redis-Optional%20cache-DC382D?logo=redis&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Multi--module-C71A36?logo=apachemaven&logoColor=white"></a>
</p>



<details>
<summary>On this page</summary>

- [Modules](#modules)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Database API](#database-api)
- [Storage, lookup, and core](#database-storage)
- [Dependency injection](#dependency-injection)
- [Building and testing](#building-and-testing)
</details>



## Modules

| Module | Artifact | Purpose |
|---|---|---|
| [Database-API](#database-api) | `me.trae.foundation.database:Database-API` | Entities, properties, queries, repositories, holders, tenants and exceptions. No dependencies. |
| [Database-Storage](#database-storage) | `me.trae.foundation.database:Database-Storage` | The local cache, Redis storage, the Redis driver and the codecs that turn entities into Redis hashes. |
| [Database-Lookup](#database-lookup) | `me.trae.foundation.database:Database-Lookup` | Tiered lookups that walk local, Redis and PostgreSQL and fill the tiers they missed. |
| [Database-Core](#database-core) | `me.trae.foundation.database:Database-Core` | The PostgreSQL driver, schema synchronization, the write behind batch queue, base repository and holder classes. |

Each module builds on the one above it, so depending on Database-Core brings in everything.

## Installation

Requires JDK 25 and Maven. Build and install Foundation from the repository root:

```
mvn clean install
```

Then depend on Core:

```xml
<!-- Database Core -->
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-Core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

It brings in jOOQ, HikariCP, the PostgreSQL JDBC driver, Lettuce and Gson.

## Quick start

**1. The entity.** Any class implementing `Entity`, with a constructor that takes only its `UUID` id:

```java
@RequiredArgsConstructor
@Getter
@Setter
public final class Account implements Entity {

    private final UUID id;
    
    private String name;
    private Long coins;
    private Rank rank;
    private List<String> tags;
    private Instant createdAt;
}

public enum Rank {

    MEMBER, MODERATOR, ADMIN
}
```

**2. The properties.** Each persisted value is registered once, usually as constants in a holder class:

```java
public final class AccountProperties {

    public static final EntityProperty<Account, String> NAME = EntityProperty.register(
            Account.class,
            "name",
            Account::getName,
            Account::setName,
            String.class,
            true
    );

    public static final EntityProperty<Account, Long> COINS = EntityProperty.register(
            Account.class,
            "coins",
            Account::getCoins,
            Account::setCoins,
            Long.class,
            true
    );

    public static final EntityProperty<Account, Rank> RANK = EntityProperty.register(
            Account.class,
            "rank",
            Account::getRank,
            Account::setRank,
            Rank.class,
            true
    );

    public static final EntityProperty<Account, List<String>> TAGS = EntityProperty.registerWithConverter(
            Account.class,
            "tags",
            Account::getTags,
            Account::setTags,
            JsonValueConverter.ofList(String.class),
            true
    );

    public static final EntityProperty<Account, Instant> CREATED_AT = EntityProperty.register(
            Account.class,
            "createdAt",
            Account::getCreatedAt,
            Account::setCreatedAt,
            Instant.class,
            true
    );
}
```

**3. The repository.** Talks to PostgreSQL and declares the table, tenant scope and indexes:

```java
public final class AccountRepository extends AbstractEntityRepository<Account> {

    public AccountRepository(final CoreDatabase coreDatabase) {
        super(coreDatabase, Account.class, "accounts", TenantScope.NONE, AccountProperties.class);
    }

    @Override
    public Map<EntityProperty<? super Account, ?>, IndexType> getIndexes() {
        return Map.of(
                AccountProperties.NAME, IndexType.UNIQUE,
                AccountProperties.COINS, IndexType.BTREE
        );
    }
}
```

**4. The holder.** Adds caching, change tracking and unique claims on top of the repository:

```java
public final class AccountHolder extends AbstractEntityHolder<Account> {

    public AccountHolder(final AccountRepository accountRepository) {
        super(accountRepository, InstanceMode.SINGLETON);
    }
}
```

**5. Start it up:**

```java
final PostgresDriver postgresDriver = new PostgresDriver(new PostgresSettings("localhost", 5432, "app", "postgres", "password", 10));
final RedisDriver redisDriver = new RedisDriver(new RedisSettings("localhost", 6379, null, 0, Duration.ofSeconds(2)));

postgresDriver.connect();
redisDriver.connect();

final CoreDatabase coreDatabase = new CoreDatabase(postgresDriver, redisDriver, new Tenant("api", 1));
final AccountRepository accountRepository = new AccountRepository(coreDatabase);
final AccountHolder accountHolder = new AccountHolder(accountRepository);

coreDatabase.start();

final Account account = new Account(UUID.randomUUID());
account.setName("trae");
account.setCoins(100L);
accountHolder.save(account);

accountHolder.getByProperty(AccountProperties.NAME, "trae").ifPresent(found -> found.setCoins(250L));

coreDatabase.stop();
redisDriver.disconnect();
postgresDriver.disconnect();
```

The `accounts` table is created on `start()`. The save is written on the next flush, and the coin change is picked up automatically on a later flush with no second `save` call.

## Database-API

The contracts everything else implements. It has no dependencies, so it can be shared with code that only needs to describe entities or build queries.

### Entities

An entity implements `Entity`, which only requires `getId()`. It must declare a constructor taking only its `UUID`, which is how entities are rebuilt from PostgreSQL and Redis. The constructor may be private.

### Properties

`EntityProperty` describes one value on an entity: its name, getter, setter and type.

```java
EntityProperty.register(Account.class, "name", Account::getName, Account::setName, String.class, true);

EntityProperty.registerWithConverter(Account.class, "tags", Account::getTags, Account::setTags, JsonValueConverter.ofList(String.class), true);
```

- **Name:** becomes the column name and the Redis hash field. `id` and `tenant_id` are reserved.
- **Type:** must be a wrapper type such as `Long`, never a primitive.
- **Persistent:** the last argument. `true` stores the value in PostgreSQL. `false` keeps it in the local cache and Redis only, which suits session data that should be shared between servers but never saved.
- **Inheritance:** properties registered on a superclass apply to every subclass.
- **Registration:** properties are registered when their class is initialized. Pass the class that holds them to the repository constructor, which loads it for you.

### Value converters

A `ValueConverter<Value, Stored>` stores a type the database does not understand as one it does. Database-Core ships two:

| Converter | Stored as |
|---|---|
| `JsonValueConverter.of(type)`, `ofList(type)`, `ofSet(type)`, `ofMap(keyType, valueType)` | `JSONB`, through Gson |
| `EnumValueConverter` | `VARCHAR` holding the constant name |

Enums are stored by name even without a converter.

### Queries

```java
final List<Account> richest = accountRepository.findMany(Query.where(AccountProperties.COINS, Operator.GREATER_THAN, 1000L)
        .and(AccountProperties.RANK, Operator.EQUALS, Rank.ADMIN)
        .orderBy(AccountProperties.COINS, Direction.DESCENDING)
        .limit(10)
        .offset(20));

final long staff = accountRepository.count(Query.whereIn(AccountProperties.RANK, List.of(Rank.ADMIN, Rank.MODERATOR)));

final List<Account> everyone = accountRepository.findMany(Query.of(Account.class));
```

| Operator | Notes |
|---|---|
| `EQUALS`, `NOT_EQUALS` | |
| `GREATER_THAN`, `GREATER_THAN_OR_EQUALS`, `LESS_THAN`, `LESS_THAN_OR_EQUALS` | |
| `LIKE` | Standard SQL pattern, for example `"Tra%"` |
| `IS_NULL`, `IS_NOT_NULL` | The value is ignored |
| `IN` | Only through `whereIn` and `andIn`, which take a collection |

Queries only see the repository's own tenant by default. `tenant("api-2")` targets another tenant, and `allTenants()` removes the filter.

### Repositories

`EntityRepository` reads straight from PostgreSQL:

| Method | Purpose |
|---|---|
| `findById(id)`, `findManyById(ids)` | Load by id |
| `findOne(query)`, `findMany(query)` | Load by query |
| `count(query)`, `exists(query)` | Count or check without loading |
| `save(entity)`, `delete(entity)` | Queue a write, see [the batch queue](#the-batch-queue) |
| `getIndexes()` | The indexes to keep on the table |

| Index type | Creates |
|---|---|
| `UNIQUE` | A unique index, per tenant on tenant scoped tables, also enforced across servers through Redis claims |
| `BTREE` | A regular index |
| `GIN_TRGM` | A trigram index for fast `LIKE` searches, enabling `pg_trgm` when needed |
| `BRIN` | A block range index, for large tables ordered by time |

### Holders

`EntityHolder` is what most code should use. It sits in front of a repository:

| Method | Purpose |
|---|---|
| `getById(id)`, `getById(id, lookupTiers)` | Load through the cache tiers, optionally only some of them |
| `getByProperty(property, value)` | Load by any property |
| `save(entity)`, `delete(entity)` | Write through every tier |
| `cache(entity)`, `evict(entity)` | Add to or remove from the local cache, with `evict` writing any changes first |
| `pin(entity)`, `unpin(entity)`, `getPinned()` | Keep an entity cached until unpinned, for example while a user is logged in |
| `increment(entity, property, delta)` | Atomically add to a `Long` or `Integer` property |

`InstanceMode.SINGLETON` keeps a local copy of each entity and tracks changes to it. Its existing pinning behavior is unchanged. `InstanceMode.MULTI_INSTANCE` uses a short-lived local cache when Redis is configured; reads refresh that entry's idle expiry, but cannot extend its absolute age. The default maximum age is five minutes and can be changed through the `HolderComponents.create` overload. A Redis Pub/Sub invalidation evicts changed entries on other instances; the absolute age bounds stale data if a notification is missed. Pinning is not available in that mode. Without Redis, MULTI_INSTANCE reads directly from PostgreSQL.

`LookupTier` is `LOCAL`, `REDIS` or `DATABASE`.

### Tenants

A `Tenant` identifies the running server by a group and an instance number, for example `new Tenant("api", 1)`. Each repository picks a `TenantScope`:

| Scope | Rows belong to | Tenant id |
|---|---|---|
| `NONE` | Everyone, no tenant column | |
| `GLOBAL` | Everyone, through the tenant column | `*` |
| `GROUP` | Every server in the group | `api` |
| `INSTANCE` | This server only | `api-1` |

Tenant scoped tables are keyed by tenant and id, so two servers can hold rows with the same id. Any scope other than `NONE` requires a `Tenant` on the `CoreDatabase`.

### Exceptions

Every exception extends `DatabaseException`, an unchecked exception.

| Exception | Thrown when |
|---|---|
| `ConnectionException` | PostgreSQL or Redis cannot be reached, or is used before connecting |
| `SchemaException` | An entity, property, converter or repository is set up wrong |
| `QueryException` | A query uses a property or operator incorrectly |
| `UniqueValueTakenException` | Saving a value that another entity already owns |

## Database-Storage

### Redis

```java
final RedisDriver redisDriver = new RedisDriver(new RedisSettings("localhost", 6379, "password", 0, Duration.ofSeconds(2)));

redisDriver.connect();

redisDriver.subscribe("app:events", message -> System.out.println(message));

redisDriver.publish("app:events", "Cache cleared");
```

`RedisSettings` takes the host, port, password (`null` or empty for none), database index and timeout. The driver is built on Lettuce and also gives direct access to synchronous and asynchronous commands. Subscriptions survive reconnecting, and messages are delivered on their own thread.

### Local cache

`LocalStorage` is an in memory map with per entry expiry and pinning. Pinned entries never expire, and unpinning puts the normal expiry back. Sliding expiry is opt-in; MULTI_INSTANCE holders use it while SINGLETON holders retain their existing fixed expiry behavior.

### Redis storage

`RedisStorage` keeps each entity as a Redis hash under `table:id`, or `table:tenant:id` on tenant scoped tables, with every property as a field and an expiry refreshed on each write. Only changed fields are written. A hash that can no longer be read, for example after a property changed type, is deleted rather than failing the lookup.

### Codecs

`EntityCodec` and `ValueCodec` convert between entities and Redis hashes. Strings, UUIDs, every number type, booleans, characters, `BigDecimal`, `BigInteger`, `Instant` and enums work out of the box, as does any type with a static `valueOf(String)` method. Anything else needs a `ValueConverter`.

## Database-Lookup

`TieredLookup` walks the tiers in order and stops at the first hit:

1. **Local:** the in memory cache, in both holder modes when configured. MULTI_INSTANCE local entries use sliding expiry and Redis invalidation.
2. **Redis:** when a Redis driver is configured.
3. **Database:** PostgreSQL.

A value found in a later tier is written back into every earlier tier it missed, so the next lookup is served locally. Concurrent same-JVM lookups for the same key share one lookup. For an id missing from Redis, instances coordinate a short-lived Redis fill lock, recheck Redis after acquiring it, and only the lock owner loads from PostgreSQL. Other instances wait for that Redis result. The lock is a load-reduction mechanism, not a database correctness lock; its lease and fenced cache write prevent a late loader from replacing a newer cache value. Batch lookups coordinate per id and load the ids owned by that caller in one database query.

Redis caches short-lived negative lookups and briefly shares a failed-fill marker, so concurrent requests do not immediately stampede PostgreSQL when an id is absent or a fill fails. A later request retries after those markers expire.

Lookups by property work the same way. Properties with a `UNIQUE` index also keep a Redis index from value to id, so another server can find an entity by name without touching PostgreSQL.

## Database-Core

### CoreDatabase

```java
new CoreDatabase(postgresDriver);
new CoreDatabase(postgresDriver, redisDriver, tenant);
new CoreDatabase(postgresDriver, redisDriver, tenant, Duration.ofMillis(250), 500);
```

Redis and the tenant are optional. The last two arguments are the flush interval and the number of writes sent per transaction, which default to 250 milliseconds and 500.

| Method | Purpose |
|---|---|
| `start()` | Synchronizes every registered repository's table and indexes, then starts flushing |
| `stop()` | Stops flushing and writes everything still pending |
| `isReady()` | Started and connected to PostgreSQL |
| `getRepositories()`, `getRepository(entityType)` | Registered repositories |
| `setFailureHandler(handler)` | Receives every write that is dropped or fails in the background |

Repositories register themselves when constructed. One registered after `start()` has its table synchronized straight away, and registering two repositories for the same entity throws `SchemaException`.

### PostgreSQL

`PostgresSettings` takes the host, port, database, username, password and maximum pool size. `PostgresDriver` runs a HikariCP pool with jOOQ on top.

### Schema synchronization

On start, each repository's table is created if it is missing, and any missing columns are added. Columns are never dropped, so removing a property leaves its data in place. Indexes declared in `getIndexes()` are created, and indexes Database created earlier that are no longer declared are dropped. Indexes you create yourself are never touched.

| Java type | Column type |
|---|---|
| `String` | `varchar` |
| `UUID` | `uuid` |
| `Long`, `Integer`, `Short`, `Byte` | `bigint`, `integer`, `smallint`, `smallint` |
| `Double`, `Float` | `double precision`, `real` |
| `Boolean` | `boolean` |
| `BigDecimal`, `BigInteger` | `numeric` |
| `Instant` | `timestamp with time zone` |
| `byte[]` | `bytea` |
| Enums | `varchar` |
| `JsonValueConverter` values | `jsonb` |

### The batch queue

Every write goes through a write behind queue instead of hitting PostgreSQL straight away:

- **Coalescing:** several saves of the same entity between flushes become one write, keeping the newest value of each column. A delete replaces anything queued before it.
- **Upserts:** saves are written as `INSERT ... ON CONFLICT DO UPDATE`, touching only the changed columns.
- **Transactions:** writes are sent in chunks, each in its own transaction. When Redis is configured, cache invalidations are added to a PostgreSQL outbox in the same transaction as the entity write.
- **Retries:** if PostgreSQL is unreachable, or fails with a temporary error such as a lost connection, a deadlock or running out of resources, the writes stay queued and are tried again on the next flush.
- **Bad writes:** a write PostgreSQL rejects outright, such as a duplicate unique value, is retried on its own so the rest of the chunk still lands, then dropped and reported to the failure handler.
- **Shutdown:** `stop()` flushes up to ten more times to empty the queue, and reports anything it still could not write.
- **Capacity:** the queue holds at most 10,000 distinct outstanding entity keys by default, including writes currently being flushed. Pass `maximumPendingWrites` to the extended `CoreDatabase` constructor to change the limit. When full, a new distinct write throws `DatabaseException` synchronously; the caller must handle or retry it. Writes for an already queued key continue to coalesce. The queue is in memory, so this is bounded overload protection, not durable write storage.

Reads go straight to PostgreSQL, so a value saved through the repository is visible there after the next flush.

### Holders

`AbstractEntityHolder` keeps entities in the local cache for 30 minutes and in Redis for an hour by default. In MULTI_INSTANCE, the 30-minute local expiry is an idle timeout refreshed on reads. Both can be changed through its other constructor:

```java
super(accountRepository, InstanceMode.SINGLETON, Duration.ofMinutes(10), Duration.ofHours(6));
```

- **Change tracking:** the holder remembers each entity's values when it is loaded or saved. Saving writes only the properties that changed.
- **Automatic writes:** on every flush, changes made to entities in the local cache are written without calling `save`, including entities whose cache entry just expired.
- **Unique claims:** saving a value for a `UNIQUE` property claims it in Redis with a short lease. A value owned by another entity throws `UniqueValueTakenException` before anything is written. A claim held by an entity that no longer has that value is taken over, and a claim still being made on another server is respected. Deleting an entity releases its claims.
- **Cache invalidation:** committed writes and deletes enqueue an outbox event in the PostgreSQL transaction. A Redis-coordinated dispatcher removes the entity hash and negative-cache marker, then publishes the id so MULTI_INSTANCE local caches evict it. Events are idempotent and remain in PostgreSQL if Redis dispatch fails; reconnecting Pub/Sub listeners clear their local Multi caches to recover missed messages. Delivery is eventual, so reads can briefly observe a cached value between commit and invalidation. Outbox dispatch is throttled and batched.
- **Increments:** `increment` uses Redis `HINCRBY` when Redis is configured and queues the resulting entity value for PostgreSQL persistence. The Redis operation is atomic, but this is not a PostgreSQL compare-and-swap protocol; concurrent absolute writes from different JVMs still use last committed write semantics. Use a database-native atomic update for counters that must remain correct across failures or conflicting writers.

Last-write-wins remains the default. To reject stale edits for a repository, implement `RevisionedEntity` on its entity and construct its repository with the optimistic-locking option enabled. The database adds a `revision` column, reads and Redis cache entries carry the revision, and each changed write updates only when its expected revision still matches. A stale write is rejected and reported to the configured failure handler as `OptimisticLockException`; reload or merge the entity before retrying. The revision increments only when persisted values change. Existing tables receive the revision column with a default of zero. Optimistic locking detects conflicting entity snapshots; it does not merge independent field edits automatically.

## Dependency injection

Every class above takes its dependencies through its constructor, so any constructor based container can wire it. The rule to follow is that drivers connect before `CoreDatabase` is used, and on shutdown the database stops before the drivers disconnect.

### With Injector

Using [Injector](https://github.com/TR4E/Foundation/tree/master/Injector), one component connects the drivers and provides the `CoreDatabase`. Repositories and holders are ordinary singletons:

```java
@Singleton
public final class DatabaseConnector implements Lifecycle {

    private final PostgresDriver postgresDriver = new PostgresDriver(new PostgresSettings("localhost", 5432, "app", "postgres", "password", 10));
    private final RedisDriver redisDriver = new RedisDriver(new RedisSettings("localhost", 6379, null, 0, Duration.ofSeconds(2)));

    private CoreDatabase coreDatabase;

    @Provider
    public CoreDatabase coreDatabase() {
        this.postgresDriver.connect();
        this.redisDriver.connect();

        this.coreDatabase = new CoreDatabase(this.postgresDriver, this.redisDriver, new Tenant("api", 1));
        this.coreDatabase.start();

        return this.coreDatabase;
    }

    @Override
    public void onComponentShutdown() {
        this.coreDatabase.stop();

        this.redisDriver.disconnect();
        this.postgresDriver.disconnect();
    }
}

@Singleton
public final class AccountRepository extends AbstractEntityRepository<Account> {

    public AccountRepository(final CoreDatabase coreDatabase) {
        super(coreDatabase, Account.class, "accounts", TenantScope.NONE, AccountProperties.class);
    }
}

@Singleton
public final class AccountHolder extends AbstractEntityHolder<Account> {

    public AccountHolder(final AccountRepository accountRepository) {
        super(accountRepository, InstanceMode.SINGLETON);
    }
}
```

Any component can then take `AccountHolder` in its constructor. Because `DatabaseConnector` is created before everything that needs the database, it shuts down last, after every holder and repository. The connection details can come from an Injector `@Configuration` class injected into `DatabaseConnector`.

### With Spring and Spring Boot

The same wiring as beans, using Spring's own dependency injection:

```java
@Configuration
public class DatabaseConfiguration {

    @Bean(destroyMethod = "disconnect")
    public PostgresDriver postgresDriver() {
        final PostgresDriver postgresDriver = new PostgresDriver(new PostgresSettings("localhost", 5432, "app", "postgres", "password", 10));
        
        postgresDriver.connect();
        
        return postgresDriver;
    }

    @Bean(destroyMethod = "disconnect")
    public RedisDriver redisDriver() {
        final RedisDriver redisDriver = new RedisDriver(new RedisSettings("localhost", 6379, null, 0, Duration.ofSeconds(2)));
        
        redisDriver.connect();
        
        return redisDriver;
    }

    @Bean(destroyMethod = "stop")
    public CoreDatabase coreDatabase(final PostgresDriver postgresDriver, final RedisDriver redisDriver) {
        final CoreDatabase coreDatabase = new CoreDatabase(postgresDriver, redisDriver, new Tenant("web", 1));
        
        coreDatabase.start();
        
        return coreDatabase;
    }
}

@Component
public class AccountRepository extends AbstractEntityRepository<Account> {

    public AccountRepository(final CoreDatabase coreDatabase) {
        super(coreDatabase, Account.class, "accounts", TenantScope.NONE, AccountProperties.class);
    }
}

@Service
public class AccountHolder extends AbstractEntityHolder<Account> {

    public AccountHolder(final AccountRepository accountRepository) {
        super(accountRepository, InstanceMode.SINGLETON);
    }
}
```

Spring destroys beans in reverse dependency order, so holders and repositories go first, then `CoreDatabase.stop()` flushes what is left, then the drivers disconnect. Settings can come from `@Value` or `@ConfigurationProperties`. Mark repositories with `@Component` rather than `@Repository`, because Spring's exception translation proxy for `@Repository` is not needed here.

## Building and testing

The tests use real PostgreSQL and Redis. Start the isolated, disposable Docker Compose environment from the repository root:

```
docker compose -p foundation-db-audit -f Database/docker-compose.audit.yml up -d --wait
```

It binds only to localhost on ports `55432` and `56379`, creates no persistent volumes, and uses dedicated test credentials. Run the database suite with:

```
mvn -f Database/pom.xml test `
  "-Dpostgres.host=localhost" `
  "-Dpostgres.port=55432" `
  "-Dpostgres.database=foundation_audit" `
  "-Dpostgres.username=foundation_audit" `
  "-Dpostgres.password=foundation_audit_test_only" `
  "-Dredis.host=localhost" `
  "-Dredis.port=56379" `
  "-Dredis.database=0"
```

Stop and remove only this test project when finished:

```
docker compose -p foundation-db-audit -f Database/docker-compose.audit.yml down
```

Connection details default to `localhost` and can be changed with system properties:

| Property | Default |
|---|---|
| `postgres.host`, `postgres.port` | `localhost`, `5432` |
| `postgres.database` | `foundation_test` |
| `postgres.username`, `postgres.password` | `postgres`, `postgres` |
| `redis.host`, `redis.port` | `localhost`, `6379` |
| `redis.password` | None |
| `redis.database` | `15` |

Tests that need PostgreSQL or Redis are skipped when either cannot be reached. Use the Compose command above to ensure the integration tests actually run.
