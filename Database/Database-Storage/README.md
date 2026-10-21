<h1 align="center">Database-Storage</h1>

<p align="center">Local caching, Redis storage, connection management, and entity codecs.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://redis.io/"><img alt="Redis" src="https://img.shields.io/badge/Redis-Lettuce-DC382D?logo=redis&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven library" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database-Storage implements the cache layer below [Database-Lookup](../Database-Lookup/README.md) and [Database-Core](../Database-Core/README.md). It depends on Database-API and Lettuce, with no PostgreSQL dependency. Applications using Core receive this module transitively; direct use is useful for custom storage and lookup integrations.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Local storage](#local-storage)
- [Redis connection](#redis-connection)
- [Redis entity storage](#redis-entity-storage)
- [Cache fill coordination](#cache-fill-coordination)
- [Namespaces and invalidation](#namespaces-and-invalidation)
- [Codecs](#codecs)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Purpose |
| --- | --- |
| `LocalStorage<Key, Value>` | Concurrent local storage with expiry and pinning |
| `RedisDriver` / `RedisSettings` | Lettuce connections, commands, subscriptions, and locks |
| `RedisStorage<Entity>` | Entity hashes, expiry, coordinated cache fills, and invalidation |
| `RedisNamespace` | Table and tenant key construction |
| `EntityCodec` / `ValueCodec` | Entity-to-hash and value-to-string conversion |
| `EntityRevisionStore` | Track optimistic revisions for entity instances |

## Local storage

```java
final LocalStorage<UUID, String> localStorage = new LocalStorage<>(Duration.ofMinutes(10));
final UUID id = UUID.randomUUID();
localStorage.put(id, "cached value");

final Optional<String> value = localStorage.get(id);
```

| Constructor | Expiry policy |
| --- | --- |
| `new LocalStorage<>()` | No configured expiry |
| `new LocalStorage<>(expiry)` | Fixed expiry |
| `new LocalStorage<>(expiry, true)` | Sliding idle expiry plus a five-minute maximum age |
| `new LocalStorage<>(expiry, true, maximumAge)` | Sliding idle expiry plus the supplied absolute maximum age |

The full constructor also accepts `false` for fixed expiry with a separate maximum age. Expired entries are removed during reads or `evictExpired()`. A sliding read refreshes idle expiry but cannot extend absolute maximum age.

`pin(key, value)` prevents expiry until `unpin(key)`. `remove(key)` clears both the entry and its pin; `evict(key)` removes only the entry, retaining pin intent for a later insertion. `clear()` clears both. At the holder level, pinning is supported only by `SINGLE_INSTANCE` mode.

Local storage contains object references. Mutating a referenced object changes the local view; PostgreSQL persistence and change tracking are responsibilities of Core's holders.

## Redis connection

```java
final RedisSettings redisSettings = new RedisSettings(
        "localhost",
        6379,
        null,
        0,
        Duration.ofSeconds(2)
);
final RedisDriver redisDriver = new RedisDriver(redisSettings);
redisDriver.connect();

try {
    redisDriver.subscribe("app:events", message -> System.out.println(message));
    redisDriver.publish("app:events", "Cache cleared");
} finally {
    redisDriver.disconnect();
}
```

The five-argument settings constructor takes host, port, password, Redis database index, and timeout. The six-argument constructor adds an ACL username before the password. A null or empty password selects no password authentication when no username is supplied.

| Method | Purpose |
| --- | --- |
| `connect()` / `disconnect()` | Open and close command and Pub/Sub connections |
| `isConnected()` | Inspect the current command connection |
| `getCommands()` / `getAsyncCommands()` | Access Lettuce synchronous or asynchronous commands |
| `subscribe`, `unsubscribe`, `publish` | Manage channel listeners and send messages |
| `tryAcquireLock`, `releaseLock` | Acquire a leased owner-token lock and release it conditionally |

Subscriptions are retained for reconnect, and message delivery is dispatched separately from Lettuce's listener. Direct command access requires a connected driver and can throw connection errors; higher-level lookup code supplies its own fallback behavior.

## Redis entity storage

For an entity and properties defined as in the [API guide](../Database-API/README.md#entities-and-properties):

```java
final RedisStorage<Account> redisStorage = new RedisStorage<>(
        redisDriver,
        RedisNamespace.of("accounts"),
        Account.class,
        Duration.ofHours(1)
);
redisStorage.put(account.getId(), account);

final Optional<Account> cachedAccount = redisStorage.get(account.getId());
```

The example assumes a connected driver and an initialized property registry. Each entity is a hash with property names as fields. Writes refresh hash expiry; `putProperties` writes selected properties. Bulk reads use asynchronous Redis requests before collecting the results. An undecodable hash is discarded so higher layers can reload it.

These operations update cache storage. Use a Core holder for application mutations so PostgreSQL writes, claims, tracking, and invalidation remain coordinated.

## Cache fill coordination

`getOrLoad(id, loader)` and `getOrLoadAll(ids, loader)` combine cache reads with a database loader. A leased Redis fill lock coordinates concurrent misses across JVMs. Loaders recheck Redis after acquiring a lock, and cache writes are fenced by lock ownership to prevent an expired loader from publishing a late result.

| Current coordination setting | Value |
| --- | ---: |
| Fill-lock lease | 30 seconds |
| Fill wait budget | 35 seconds |
| Missing-entity marker | 1 second |
| Failed-fill marker | 500 milliseconds |

Negative and failure markers briefly reduce repeated loads. These are implementation constants, not constructor settings. Fill locks reduce redundant loads; authoritative constraints and transactions remain in PostgreSQL.

## Namespaces and invalidation

| Namespace | Entity key shape |
| --- | --- |
| `RedisNamespace.of("accounts")` | `accounts:<uuid>` |
| `RedisNamespace.of("accounts", "api-1")` | `accounts:api-1:<uuid>` |

Related locks, markers, and invalidation channels share the same table/tenant namespace. `forTenant(tenantId)` creates a Redis storage view for another tenant.

The namespace does not include the PostgreSQL database name. Separate database contexts that share a Redis database must use distinct table/tenant combinations or separate Redis databases to avoid collisions.

Core writes committed invalidation events to a PostgreSQL outbox and dispatches them through this layer. Storage alone does not provide transaction durability or the full holder consistency protocol.

## Codecs

Entities need a UUID-only constructor and registered properties. `EntityCodec` reconstructs them and applies `ValueCodec` to property values.

Supported scalar values include strings, UUIDs, numeric wrappers, booleans, characters, `BigDecimal`, `BigInteger`, `Instant`, and enums. Types with a compatible public static `valueOf(String)` method can also be parsed. Other values need an API `ValueConverter` with a supported stored type.

Converters are shared metadata across PostgreSQL and Redis. A type that maps to a PostgreSQL column is not automatically guaranteed a Redis representation; verify its codec or supply a converter.

## Installation and build

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-Storage</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

From the repository root with JDK 25 and Maven:

```shell
mvn -pl Database/Database-Storage -am install
```

Local and codec tests run without Redis; Redis integration tests require a reachable disposable Redis instance. See [Database testing](../README.md#building-and-testing) for the isolated Compose setup, connection properties, and test commands.
