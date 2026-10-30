<h1 align="center">Database</h1>

<p align="center">PostgreSQL entity persistence with optional Redis caching, tenant isolation, and coordinated writes.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://www.postgresql.org/"><img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-Persistence-4169E1?logo=postgresql&logoColor=white"></a>
  <a href="https://redis.io/"><img alt="Redis" src="https://img.shields.io/badge/Redis-Optional%20cache-DC382D?logo=redis&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Multi--module-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database describes entity properties once and uses them for PostgreSQL schemas, typed queries, cached lookups, and tracked writes. Redis adds shared caching and coordination across application instances. The runtime is plain Java and can be wired manually, with [Injector](../Injector/README.md), or with Spring. See [Dependency injection](#dependency-injection).

## Contents

<details>
<summary>On this page</summary>

- [Modules](#modules)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Database API](#database-api)
- [Database storage](#database-storage)
- [Database lookup](#database-lookup)
- [Database core](#database-core)
- [Choosing a configuration](#choosing-a-configuration)
- [Troubleshooting](#troubleshooting)
- [Dependency injection](#dependency-injection)
- [Building and testing](#building-and-testing)
</details>

## Modules

| Module | Artifact | Purpose |
|---|---|---|
| [Database-API](Database-API/README.md) | `me.trae.foundation.database:Database-API` | Entities, properties, queries, repositories, holders, tenants and exceptions. No dependencies. |
| [Database-Storage](Database-Storage/README.md) | `me.trae.foundation.database:Database-Storage` | The local cache, Redis storage, the Redis driver and the codecs that turn entities into Redis hashes. |
| [Database-Lookup](Database-Lookup/README.md) | `me.trae.foundation.database:Database-Lookup` | Tiered lookups that walk local, Redis and PostgreSQL and fill the tiers they missed. |
| [Database-Core](Database-Core/README.md) | `me.trae.foundation.database:Database-Core` | The PostgreSQL driver, schema synchronization, the write behind batch queue, base repository and holder classes. |

Each module builds on the one above it, so depending on Database-Core brings in the complete stack. The links above open module-specific guides; this page covers application wiring and the behavior shared across the stack. `Database` itself is a Maven aggregator with `pom` packaging.

## Installation

Requires JDK 25 and Maven. Build and install Foundation from the repository root:

```shell
mvn -pl Database/Database-Core -am install
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
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
@Getter
@Setter
public final class Account implements Entity {

    @EqualsAndHashCode.Include
    private final UUID id;

    private String name;

    private Long coins;

    private Rank rank;

    private List<String> tagList;

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
            Account::getTagList,
            Account::setTagList,
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
        super(coreDatabase, Account.class, "accounts", TenantScope.NONE, false, AccountProperties.class);
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
public final class AccountHolder extends AbstractEntityHolder<Account, AccountRepository> {

    public AccountHolder(final AccountRepository accountRepository) {
        super(accountRepository, InstanceMode.SINGLE_INSTANCE, RedisMode.ENABLED, Duration.ofMinutes(30), Duration.ofHours(1));
    }
}
```

**5. Start it up:**

```java
final PostgresDriver postgresDriver = new PostgresDriver(new PostgresSettings("localhost", 5432, "app", "postgres", "password", 10));
final RedisDriver redisDriver = new RedisDriver(new RedisSettings("localhost", 6379, null, 0, Duration.ofSeconds(2)));

postgresDriver.connect();
redisDriver.connect();

final CoreDatabase coreDatabase = new CoreDatabase(postgresDriver, redisDriver, new Tenant("api", 1), Duration.ofMillis(250), 500, 10_000);
final AccountRepository accountRepository = new AccountRepository(coreDatabase);
final AccountHolder accountHolder = new AccountHolder(accountRepository);

coreDatabase.setFailureHandler(databaseException -> databaseException.printStackTrace());
coreDatabase.start();

final Account account = new Account(UUID.randomUUID());
account.setName("example-user");
account.setCoins(100L);
accountHolder.save(account);

final Account existing = accountHolder.getById(account.getId()).orElseThrow();
existing.setCoins(250L);
accountHolder.update(existing);

accountHolder.delete(existing);

coreDatabase.stop();
redisDriver.disconnect();
postgresDriver.disconnect();
```

Examples omit imports and use Lombok for constructors, identity equality, and accessors. Put public types in separate files and enable Lombok annotation processing in the consumer project.

The `accounts` table is created on `start()`. `save` queues a new row for the next flush. `update` queues only changed persistent properties, and `delete` queues removal. Writes are asynchronous with respect to PostgreSQL durability: they become durable on a successful flush. MULTI_INSTANCE peers observe committed changes after invalidation delivery, so this is not an immediate cross-JVM read-after-write guarantee.

## Database-API

The contracts everything else implements. It has no dependencies, so it can be shared with code that only needs to describe entities or build queries.

### Entities

An entity implements `Entity`, which only requires `getId()`. It must declare a constructor taking only its `UUID`, which is how entities are rebuilt from PostgreSQL and Redis. The constructor may be private.

### Properties

`EntityProperty` describes one value on an entity: its name, getter, setter and type.

```java
EntityProperty.register(Account.class, "name", Account::getName, Account::setName, String.class, true);

EntityProperty.registerWithConverter(Account.class, "tags", Account::getTagList, Account::setTagList, JsonValueConverter.ofList(String.class), true);
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

final Optional<Account> previousNameOwner = accountRepository.findOne(
        Query.where(AccountProperties.NAME, Operator.EQUALS, previousName)
                .andIdNotEquals(loginAccountId));

final List<Account> everyone = accountRepository.findMany(Query.of(Account.class));
```

`andIdNotEquals(id)` adds a predicate on the built-in `id` column alongside registered property conditions. It does not register `id` as an entity property or add it to schema properties. Repository queries go directly to PostgreSQL; holder lookups such as `getByProperty` use their cache/index path instead. A unique name index permits only one row per name (per tenant when tenant scoped), so if the excluded account currently owns `previousName`, this query returns empty. It cannot find a second account with that name, and therefore does not by itself solve a previous-name lookup when the triggering account still owns the name.

| Operator | Notes |
|---|---|
| `EQUALS`, `NOT_EQUALS` | |
| `GREATER_THAN`, `GREATER_THAN_OR_EQUALS`, `LESS_THAN`, `LESS_THAN_OR_EQUALS` | |
| `LIKE` | Standard SQL pattern, for example `"Tra%"` |
| `IS_NULL`, `IS_NOT_NULL` | The value is ignored |
| `IN` | Only through `whereIn` and `andIn`, which take a collection |

Queries only see the repository's own tenant by default. `tenant("api-2")` targets another tenant, and `allTenants()` removes the filter.

### Pagination and composite indexes

Repositories also expose one-based pagination:

```java
final EntityPage<Account> entityPage = accountRepository.findPage(
        Query.of(Account.class).orderBy(AccountProperties.COINS, Direction.DESCENDING),
        1,
        20
);
final List<Account> accountList = entityPage.getEntities();
final long totalCount = entityPage.getTotalCount();
```

Core clamps the requested page to the available range and requires a positive page size. Use a stable ordering when paging through results.

A repository can override `getCompositeIndexes()` to declare `EntityIndex.of(name, properties...)` unique combinations. The holder's `getByIndex(entityIndex, values...)` looks up values in declaration order, with optional tier selection. All indexed properties must be persistent. See the [API guide](Database-API/README.md#indexes) for declarations and the [Lookup guide](Database-Lookup/README.md#property-and-composite-indexes) for cache behavior.

### Repositories

`EntityRepository` reads straight from PostgreSQL:

| Method | Purpose |
|---|---|
| `findById(id)`, `findManyById(ids)` | Load by id |
| `findOne(query)`, `findMany(query)` | Load by query |
| `count(query)`, `exists(query)` | Count or check without loading |
| `save(entity)` | Queue an insert; an existing UUID is rejected and never overwritten |
| `update(entity)` | Load the current PostgreSQL row as a baseline for an untracked entity, then queue only changed persistent properties; throws if the row is missing |
| `delete(entity)` | Queue deletion of the row |
| `getIndexes()` | The indexes to keep on the table |

Direct repository writes are low-level PostgreSQL operations and do not dispatch holder Redis invalidations. If an entity may already be cached by a holder, perform application mutations through the holder or manager's `save`, `update` or `delete` methods so its change tracking and configured invalidation/outbox behavior run. A direct repository mutation can leave existing holder caches stale until their entries expire or are evicted.

| Index type | Creates |
|---|---|
| `UNIQUE` | A unique index, per tenant on tenant scoped tables, also enforced across servers through Redis claims |
| `BTREE` | A regular index |
| `GIN_TRGM` | A trigram index for fast `LIKE` searches, enabling `pg_trgm` when needed |
| `BRIN` | A block range index, for large tables ordered by time |

Redis use is a holder setting, not a repository setting. `RedisMode.DISABLED` keeps that holder's cache, unique-claim and write-invalidation paths off Redis, even if another plugin uses Redis through the same server configuration. `MULTI_INSTANCE` must use `RedisMode.ENABLED` and a CoreDatabase with a Redis driver. A Redis outage after startup is handled through the existing fallback and recovery paths, but a missing Redis driver is rejected when constructing a MULTI_INSTANCE holder.

```java
public final class GroupRepository extends AbstractEntityRepository<Group> {

    public GroupRepository(final LocalDatabase localDatabase) {
        super(localDatabase, Group.class, "groups", TenantScope.INSTANCE, false, GroupProperties.class);
    }
}

public final class AccountRepository extends AbstractEntityRepository<Account> {

    public AccountRepository(final GlobalDatabase globalDatabase) {
        super(globalDatabase, Account.class, "accounts", TenantScope.NONE, false, AccountProperties.class);
    }
}
```

### Holders

`EntityHolder` is what most code should use. It sits in front of a repository:

| Method | Purpose |
|---|---|
| `getById(id)`, `getById(id, lookupTiers)` | Load through the cache tiers, optionally only some of them |
| `getByProperty(property, value)` | Load by any property |
| `save(entity)` | Create a new entity and track/cache it locally |
| `update(entity)` | Detect changed properties and queue changes for an existing entity |
| `delete(entity)` | Remove the entity and queue deletion |
| `cache(entity)`, `evict(entity)` | Add to or remove from the local cache, with `evict` writing any changes first |
| `pin(entity)`, `unpin(entity)`, `getPinned()` | Keep an entity cached until unpinned, for example while a user is logged in |
| `increment(entity, property, delta)` | Atomically add to an unindexed persistent `Long` or `Integer` property in PostgreSQL |

`InstanceMode.SINGLE_INSTANCE` keeps a local copy of each entity and tracks changes to it. Its existing pinning behavior is unchanged. It can use a CoreDatabase with or without Redis. `InstanceMode.MULTI_INSTANCE` requires a Redis-configured CoreDatabase and uses a short-lived local cache; reads refresh that entry's idle expiry, but cannot extend its absolute age. The default maximum age is five minutes and can be changed through the `HolderComponents.create` overload. Redis Pub/Sub invalidation evicts changed entries on other instances; the absolute age bounds stale data if a notification is missed. Pinning is not available in that mode. If Redis becomes unavailable after startup, Foundation uses its existing PostgreSQL fallback and recovery behavior; cache coordination and invalidation resume when Redis recovers.

`RedisMode.ENABLED` uses the Redis driver on the repository's database context. `RedisMode.DISABLED` is available to SINGLE_INSTANCE holders that share a server-wide database context but should not use Redis for that entity type. MULTI_INSTANCE holders require Redis enabled and configured.

`LookupTier` is `LOCAL`, `REDIS` or `DATABASE`.

### GLOBAL and LOCAL database drivers

GLOBAL and LOCAL are separate configured database contexts. Each context owns its PostgreSQL driver, optional Redis driver, write queue, transaction context and revision state. `GlobalDatabase` and `LocalDatabase` are distinct injectable types over the shared `CoreDatabase` implementation, so repository constructors can request the intended destination directly. Their PostgreSQL `databaseName` comes from their own `PostgresSettings`. This is independent of `TenantScope`, which determines row-level tenant columns, and `InstanceMode`, which controls holder caching and coordination requirements.

```java
final PostgresDriver globalPostgresDriver = new PostgresDriver(globalPostgresSettings);
final RedisDriver globalRedisDriver = new RedisDriver(redisSettings);
final GlobalDatabase globalDatabase = new GlobalDatabase(globalPostgresDriver, globalRedisDriver, tenant, Duration.ofMillis(250), 500, 10_000);

final PostgresDriver localPostgresDriver = new PostgresDriver(localPostgresSettings);
final LocalDatabase localDatabase = new LocalDatabase(localPostgresDriver, null, new Tenant("game", 1), Duration.ofMillis(250), 500, 10_000);

final AccountRepository accountRepository = new AccountRepository(globalDatabase);
final AccountManager accountManager = new AccountManager(accountRepository); // MULTI_INSTANCE
final GroupRepository groupRepository = new GroupRepository(localDatabase);
final GroupManager groupManager = new GroupManager(groupRepository); // SINGLE_INSTANCE
```

The managers use the same lifecycle methods; the holder selects local or multi-instance coordination and its Redis policy:

```java
public final class AccountManager extends AbstractEntityHolder<Account, AccountRepository> {

    public AccountManager(final AccountRepository accountRepository) {
        super(accountRepository, InstanceMode.MULTI_INSTANCE, RedisMode.ENABLED, Duration.ofMinutes(30), Duration.ofHours(1));
    }
}

public final class GroupManager extends AbstractEntityHolder<Group, GroupRepository> {

    public GroupManager(final GroupRepository groupRepository) {
        super(groupRepository, InstanceMode.SINGLE_INSTANCE, RedisMode.DISABLED, Duration.ofMinutes(30), Duration.ofHours(1));
    }
}
```

Both expose `save`, `update` and `delete`. Account changes go through the GLOBAL repository's Redis invalidation and outbox after PostgreSQL commit. Group changes use the LOCAL repository's own queue and PostgreSQL driver; `RedisMode.DISABLED` keeps that holder's entity path off Redis even if the server's shared configuration also provides Redis.

`CoreDatabase` is the shared implementation; `GlobalDatabase` and `LocalDatabase` are separate configured instances. Set their PostgreSQL names in their respective `PostgresSettings`, such as `application_global` and `application_local`. Register each through its own `@Provider` method, then type repository constructors to the matching database so the Injector resolves the right destination without ambiguity. Start and stop each database independently; their queues and transactions never cross. Configure the local database with the server's unique tenant instance (for example, `new Tenant("game", 1)`) and use `TenantScope.INSTANCE` so each game server stores its own group rows. `TenantScope` partitions rows inside the chosen database; it does not select that database. Redis namespaces contain the table and tenant, but not the PostgreSQL database name. If the two contexts share a Redis database, use distinct table/tenant combinations or separate Redis databases so unrelated rows cannot collide.

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
| `OptimisticLockException` | A write uses an entity revision that is no longer current |

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

`LocalStorage` is an in memory map with per entry expiry and pinning. Pinned entries never expire, and unpinning puts the normal expiry back. Sliding expiry is opt-in; MULTI_INSTANCE holders use it while SINGLE_INSTANCE holders retain their existing fixed expiry behavior.

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

### ID and property indexes

`getById(id)` is the primary-key lookup. It checks the local cache by UUID, then the Redis entity hash, then PostgreSQL's primary key. When it loads an entity from Redis or PostgreSQL, the holder fills the earlier cache tiers and populates every registered property and composite index for that entity.

`getByProperty(property, value)` uses an O(1) local value-to-id map and, when Redis is enabled, a Redis value-to-id index only when that property is declared `UNIQUE` by the repository. It then loads the entity through the normal ID lookup. If the property is not indexed, the local fallback scans cached entities and PostgreSQL performs a property query; use a declared index when indexed lookup performance is required.

Holder `save(entity)` puts the entity in that holder's local cache immediately and queues the PostgreSQL insert. After the insert commits, a Redis-enabled holder reloads the committed row under the cache-fill lock and warms the shared Redis entity cache and registered Redis indexes. In MULTI_INSTANCE mode, Redis also reserves the entity ID while the insert is pending, so another holder cannot queue a second insert with that ID.

Each lookup also accepts a `Set<LookupTier>` to limit which tiers it reads. The overload without a tier set searches all available tiers. A local-only lookup returns empty on a local miss and does not fall through to Redis or PostgreSQL. For a registered index, the local lookup uses its value-to-id map; unindexed property lookups use the existing local scan.

```java
final Set<LookupTier> localOnly = EnumSet.of(LookupTier.LOCAL);
holder.getById(entityId, localOnly);
holder.getByProperty(property, value, localOnly);
holder.getByIndex(entityIndex, localOnly, firstValue, secondValue);
```

For a case-insensitive property, register it with `EntityProperty.registerCaseInsensitive`. Foundation lowercases values only for local and Redis index keys and uses `lower(column)` for PostgreSQL comparisons and functional indexes. It does not lowercase or rewrite the stored property value: a mixed-case display value remains exactly as saved in PostgreSQL and on the loaded entity. Changing only the input casing does not change the normalized index key.

```java
@Override
public Map<EntityProperty<? super Account, ?>, IndexType> getIndexes() {
    return Map.of(AccountProperties.NAME, IndexType.UNIQUE);
}

final Optional<Account> byId = accountHolder.getById(accountId);
final String lookupValue = "some-value";
final Optional<Account> byName = accountHolder.getByProperty(AccountProperties.NAME, lookupValue);
```

The local index points to the entity in local storage; it is not a second entity cache. Its lookup is constant-time, and an expired or missing entity makes the holder discard that stale local reference. Pinning a single-instance entity also makes its indexed properties resolve locally until unpinned. Multi-instance holders do not support pinning. Redis entity and index entries use the holder's Redis expiry; local entries follow the holder's local expiry and maximum-age rules.

Holder updates reindex the changed entity locally. After PostgreSQL commits, the outbox invalidates the entity hash and tells other multi-instance holders to evict their local copy. A Redis property pointer that no longer matches the loaded entity is removed and repaired on a later lookup. Invalidation is eventual, so a stale cached result may be visible briefly before delivery.

When ownership of a unique indexed value must move between entities, use the holder's transfer operation instead of two independent `update` calls:

```java
holder.transferUniqueProperty(
        claimantId,
        currentOwnerId,
        uniqueProperty,
        claimedValue,
        replacementValue
);
```

The operation requires a persistent unique property and two existing, different entities. It changes both rows in one PostgreSQL transaction, verifies that the previous owner still holds the requested value, and records both invalidations in the outbox. A conflicting replacement or stale revision rolls the transaction back. The replacement value is chosen by the caller. Pending writes are flushed first, and the transfer fails if the database is still unable to drain them.

## Database-Core

### CoreDatabase

```java
final CoreDatabase coreDatabase = new CoreDatabase(
        postgresDriver,
        redisDriver,
        tenant,
        Duration.ofMillis(250),
        500,
        10_000
);
```

The current constructor requires all six arguments: PostgreSQL driver, optional Redis driver, optional tenant, flush interval, transaction chunk size, and maximum pending writes. `GlobalDatabase` and `LocalDatabase` use the same signature. Pass `null` for Redis when unused, and for the tenant only when every repository uses `TenantScope.NONE`. The values above explicitly choose a 250-millisecond flush interval, chunks of 500 writes, and capacity for 10,000 distinct outstanding entity keys.

| Method | Purpose |
|---|---|
| `start()` | Synchronizes every registered repository's table and indexes, then starts flushing |
| `stop()` | Stops flushing and writes everything still pending |
| `isReady()` | Started and connected to PostgreSQL |
| `getRepositories()`, `getRepository(entityType)` | Registered repositories |
| `setFailureHandler(handler)` | Receives background write failures, rejected commits, and shutdown drain failures |
| `getBatchQueue().flush()` | Request an immediate flush; temporary failures may leave writes pending |

Set a failure handler before starting work; the default handler performs no action. An accepted `save` or `update` can still fail later when the queue commits. Queue contents remain in memory until commit, so enqueueing is not a durability acknowledgment.

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

- **Coalescing:** a create followed by updates before its first flush becomes one insert containing the latest values. Updates to the same entity coalesce by column. A queued update followed by a delete becomes a delete. Unsafe create/delete or delete/recreate sequences are rejected while the earlier operation is pending.
- **Insert-only creation:** `save` issues an insert with conflict rejection. It never overwrites a row with the same UUID. PostgreSQL constraints decide cross-JVM duplicate races; the losing create is reported to the failure handler.
- **Update-only changes:** `update` issues an `UPDATE` against the existing id and tenant; it cannot recreate a missing row. Holder updates use tracked snapshots, while a direct repository update loads a PostgreSQL baseline before calculating changes. An unchanged entity causes no PostgreSQL write or invalidation.
- **Transactions:** writes are sent in chunks, each in its own transaction. When Redis is configured, cache invalidations are added to a PostgreSQL outbox in the same transaction as the entity write.
- **Retries:** if PostgreSQL is unreachable, or fails with a temporary error such as a lost connection, a deadlock or running out of resources, the writes stay queued and are tried again on the next flush.
- **Bad writes:** a write PostgreSQL rejects outright, such as a duplicate UUID or unique value, is retried on its own so the rest of the chunk still lands, then reported to the failure handler. A failed create removes its provisional local cache/tracking state. A missing-row update is reported and never becomes an insert.
- **Shutdown:** `stop()` flushes up to ten more times to empty the queue, and reports anything it still could not write.
- **Capacity:** the queue holds at most 10,000 distinct outstanding entity keys by default, including writes currently being flushed. Set `maximumPendingWrites` in the database constructor to choose the limit. When full, a new distinct write throws `DatabaseException` synchronously; the caller must handle or retry it. Writes for an already queued key continue to coalesce. The queue is in memory, so this is bounded overload protection, not durable write storage.
- **Commit callbacks:** existing holder callbacks report write completion without waiting for remote acknowledgements. All holder user callbacks, including unchanged updates, rejections, and Redis-disabled operations, run asynchronously on dedicated framework virtual threads. There is no callback execution-order guarantee across separate calls; an ordinary callback is never queued behind an acknowledgement waiter. Callback registrations remain registered across write retries. Outstanding callback registrations are capped at 100,000 by default, including callbacks attached to in-flight writes. A write that would exceed this limit throws `DatabaseException` synchronously without changing the queue.

- **Peer synchronization:** use `saveAndSynchronize`, `updateAndSynchronize`, or `deleteAndSynchronize` to opt in. The full overload takes `(entity, timeout, synchronizedRunnable, writeFailureRunnable, synchronizationFailureConsumer)`, with a `Duration` timeout. The shorter overload defaults to five seconds. A committed-but-unsynchronized write invokes the `Consumer<SynchronizationException>`; it never invokes the write-failure runnable. Late acknowledgements cannot run a suppressed success callback. Coalesced explicit waiters share publication work but have independent deadlines. Unchanged explicit requests join pending writes or synchronize the existing committed state without SQL. Ordinary calls notify owners without acknowledgement tracking. Owner markers remain scoped by table, tenant, and UUID; cache hits do not contact Redis. Notifications are grouped per JVM connection with one lease heartbeat every ten seconds and thirty-second expiry. Failed receivers retry independently after 100 ms, 250 ms, 500 ms, one second, two seconds, then at five-second intervals while still caching the entity. Pins and pending edits are preserved, and existing cached reads remain available during repair. Initial receiver failures are logged independently of application failure handlers; repairs still pending produce warnings at most every thirty seconds and successful repairs produce a recovery log with elapsed time and retry count. Deploy the updated Database library across participating JVMs together.

Reads go straight to PostgreSQL, so a value saved through the repository is visible there after the next flush.

### Holders

`AbstractEntityHolder` receives the local-cache expiry and Redis expiry in its constructor. In MULTI_INSTANCE, the local expiry is an idle timeout refreshed on reads.

```java
super(accountRepository, InstanceMode.SINGLE_INSTANCE, RedisMode.ENABLED, Duration.ofMinutes(10), Duration.ofHours(6));
```

- **Change tracking:** the holder remembers each entity's values when it is loaded or saved. `persistent` controls PostgreSQL storage only. `update` synchronizes changed nonpersistent properties through Redis and notifies other holders without queuing SQL. Remote refreshes replace those values while preserving pins and pending local edits. Shared nonpersistent state is kept separately from the expiring database cache until the entity is deleted or the values are explicitly changed, so database cache invalidation and refill do not reset it. Existing automatic flush behavior also writes dirty tracked entities, including entities whose local cache entry expires.
- **Automatic writes:** on every flush, changes made to entities in the local cache are written without calling `save`, including entities whose cache entry just expired.
- **Unique claims:** saving a value for a `UNIQUE` property claims it in Redis with a short lease. In Redis-enabled holders, saving also reserves the entity UUID while the insert is pending, preventing another holder from queueing a duplicate UUID. A value owned by another entity throws `UniqueValueTakenException` before anything is written. A claim held by an entity that no longer has that value is taken over, and a claim still being made on another server is respected. Deleting an entity releases its claims.
- **Cache invalidation:** committed writes and deletes enqueue an outbox event in the PostgreSQL transaction. A Redis-coordinated dispatcher removes stale entity hashes and negative-cache markers, then publishes the id so MULTI_INSTANCE local caches evict it. Successful inserts preserve the Redis hash warmed from the committed PostgreSQL row while clearing negative markers. Events are idempotent and remain in PostgreSQL if Redis dispatch fails; reconnecting Pub/Sub listeners clear their local Multi caches to recover missed messages. Delivery is eventual, so reads can briefly observe a cached value between commit and invalidation. Outbox dispatch is throttled and batched.
- **Increments:** `increment` runs a PostgreSQL atomic `UPDATE ... RETURNING` inside a transaction, after flushing pending writes on that database queue. Concurrent increments from separate JVMs add to the latest stored value rather than overwriting one another. It updates the supplied entity after commit and records and dispatches cache invalidation after commit. Incremented properties must not participate in registered unique or composite indexes, because those indexes need coordinated value claims and updates.

Last-write-wins remains the default. To reject stale edits for a repository, construct its repository with the optimistic-locking option enabled; entity classes need no revision field or interface. Foundation stores revisions internally by entity object identity, persists the authoritative value in a `revision` column, and carries it in Redis cache entries. Each changed write updates only when its expected revision still matches. A stale write is rejected and reported to the configured failure handler as `OptimisticLockException`; reload or merge the entity before retrying. If another save of the same entity instance is waiting behind an in-flight write, its revision advances after the preceding write commits; separately loaded stale instances still fail the revision check. The revision increments only when persisted values change. Existing tables receive the revision column with a default of zero. Optimistic locking detects conflicting entity snapshots; it does not merge independent field edits automatically. Since revisions are scoped to an entity row and reset when a row is recreated, do not reuse an entity UUID after deletion while stale instances of the old entity may still exist; otherwise an old instance could match the recreated row's revision.

## Choosing a configuration

These settings control different parts of the runtime:

| Decision | Setting | Effect |
| --- | --- | --- |
| Which PostgreSQL database receives writes? | The context's `PostgresSettings` | Selects the database and connection pool |
| Which injectable context does a repository use? | `CoreDatabase`, `GlobalDatabase`, or `LocalDatabase` constructor parameter | Selects the owning registry, queue, and drivers |
| Which rows belong to this application instance? | Repository `TenantScope` and database `Tenant` | Controls tenant filtering and keys |
| How are entities retained and coordinated? | Holder `InstanceMode` | Selects single-instance or multi-instance behavior |
| Does this holder use Redis? | Holder `RedisMode` | Enables shared cache, claims, and invalidation |
| Should stale edits be rejected? | Repository optimistic-locking flag | Requires matching persisted revisions |

For one process with PostgreSQL only, use `SINGLE_INSTANCE` and `RedisMode.DISABLED`. For shared entities edited across JVMs, configure Redis and `MULTI_INSTANCE`; enable optimistic locking where stale edits should fail. Independent global and local data stores can use distinct context types, while tenant scope partitions rows inside either store.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| A repository sees no registered properties | Pass the property-holder class to its constructor |
| `save` returned but a query cannot find the row | Writes become visible to PostgreSQL queries after a successful flush |
| A write failed without throwing from the mutation call | Observe `setFailureHandler`; background failures are asynchronous |
| Another instance reads an older cached value | Allow for commit plus eventual invalidation, or select a database-only read |
| A multi-instance holder fails at construction | Configure a Redis driver and use `RedisMode.ENABLED` |
| A property lookup scans rather than using a value index | Declare a unique property or composite index for holder indexed lookup |
| An entity cannot be reconstructed | Declare a UUID-only constructor and compatible registered property types |
| Integration tests are unexpectedly skipped | Start the disposable services and pass their connection properties |

The [Core guide](Database-Core/README.md) explains the runtime, queue behavior, and supported holder settings in more detail.

## Dependency injection

Every class above takes its dependencies through its constructor, so any constructor based container can wire it. The rule to follow is that drivers connect before `CoreDatabase` is used, and on shutdown the database stops before the drivers disconnect.

### With Injector

Using [Injector](../Injector/README.md), one component connects the drivers and provides the `CoreDatabase`. Repositories and holders are ordinary singletons:

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

        this.coreDatabase = new CoreDatabase(this.postgresDriver, this.redisDriver, new Tenant("api", 1), Duration.ofMillis(250), 500, 10_000);
        this.coreDatabase.setFailureHandler(databaseException -> databaseException.printStackTrace());
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
        super(coreDatabase, Account.class, "accounts", TenantScope.NONE, false, AccountProperties.class);
    }
}

@Singleton
public final class AccountHolder extends AbstractEntityHolder<Account, AccountRepository> {

    public AccountHolder(final AccountRepository accountRepository) {
        super(accountRepository, InstanceMode.SINGLE_INSTANCE, RedisMode.ENABLED, Duration.ofMinutes(30), Duration.ofHours(1));
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
        final CoreDatabase coreDatabase = new CoreDatabase(postgresDriver, redisDriver, new Tenant("web", 1), Duration.ofMillis(250), 500, 10_000);

        coreDatabase.setFailureHandler(databaseException -> databaseException.printStackTrace());
        coreDatabase.start();

        return coreDatabase;
    }
}

@Component
public final class AccountRepository extends AbstractEntityRepository<Account> {

    public AccountRepository(final CoreDatabase coreDatabase) {
        super(coreDatabase, Account.class, "accounts", TenantScope.NONE, false, AccountProperties.class);
    }
}

@Service
public final class AccountHolder extends AbstractEntityHolder<Account, AccountRepository> {

    public AccountHolder(final AccountRepository accountRepository) {
        super(accountRepository, InstanceMode.SINGLE_INSTANCE, RedisMode.ENABLED, Duration.ofMinutes(30), Duration.ofHours(1));
    }
}
```

Spring destroys beans in reverse dependency order, so holders and repositories go first, then `CoreDatabase.stop()` flushes what is left, then the drivers disconnect. Settings can come from `@Value` or `@ConfigurationProperties`. Mark repositories with `@Component` rather than `@Repository`, because Spring's exception translation proxy for `@Repository` is not needed here.

## Building and testing

The tests use real PostgreSQL and Redis. Start the isolated, disposable Docker Compose environment from the repository root:

```shell
docker compose -p foundation-db-audit -f Database/docker-compose.audit.yml up -d --wait
```

It binds only to localhost on ports `55432` and `56379`, creates no persistent volumes, and uses dedicated test credentials. Run the database suite with:

```shell
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

```shell
docker compose -p foundation-db-audit -f Database/docker-compose.audit.yml down -v
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

`MultiJvmInvalidationTest` starts separate Java processes. Its cold-load test uses five JVMs by default; set `-DmultiJvm.count=20` to run the same coordinated cold-load scenario with 20 independent JVMs (the supported range is 2â€“100). The isolated PostgreSQL container allows 200 connections. Worker pools default to four PostgreSQL connections; use `-DmultiJvm.postgresPoolSize=1` for a 100-JVM cold-load run. A production deployment still needs its per-JVM pool sizes and PostgreSQL connection capacity sized together.

The destructive container-recovery test is opt-in because it stops containers. Start the isolated Compose project above, then pass `-DcontainerRecoveryTests=true`, `-Daudit.postgres.container=<postgres-container-name>`, and `-Daudit.redis.container=<redis-container-name>` to Maven. Get the exact names from `docker compose -p foundation-db-audit -f Database/docker-compose.audit.yml ps`. The test restarts both containers in its cleanup path.

### Account creation and shared Redis state

Initial saves use conditional shared-field initialization (`HSETNX`) so queued initial values cannot overwrite newer updates. Account UUIDs are stable player identifiers: normal disconnects, cache eviction, and JVM restarts do not require clearing their shared hash. This rule does not distinguish an orphaned hash from a previous entity using the same key. If an operator deliberately deletes and recreates an account, or restores PostgreSQL independently of Redis, stop participating writers and coordinate cleanup of the affected table/tenant/UUID cache, shared state, and indexes before reopening traffic. Do not clear Redis for ordinary logins or restart recovery.