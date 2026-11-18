<h1 align="center">Database Installation</h1>

<p align="center">Wire AccountRepository and AccountManager into the current Database runtime.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

This guide describes PostgreSQL/local wiring. AccountRepository owns persistent queries and SQL indexes; AccountManager owns tiered access, held entities and tracked writes. CoreDatabase connects those objects and controls their lifecycle.

Examples omit package declarations and imports. Place each public type in its own file under your application package and add the imports in your IDE. Examples use Lombok. The initial setup requires PostgreSQL and does not require Redis.

## Contents

<details>
<summary>On this page</summary>

- [Dependencies](#dependencies)
- [Database settings](#database-settings)
- [Account entity](#account-entity)
- [Account declarations](#account-declarations)
- [Account repository](#account-repository)
- [Account manager](#account-manager)
- [Application wiring](#application-wiring)
- [Reads and writes](#reads-and-writes)
- [Optional Redis wiring](#optional-redis-wiring)
- [Tenant wiring](#tenant-wiring)
- [Verification](#verification)
- [Troubleshooting](#troubleshooting)

</details>

## Dependencies

Use JDK 25 and Maven. From the Foundation repository root:

```shell
mvn -pl Database/Database-Core -am install
```

In the consuming project's POM, add Core and configure Lombok processing for the example annotations:

```xml
<properties>
    <maven.compiler.release>25</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>

<dependencies>
    <dependency>
        <groupId>me.trae.foundation.database</groupId>
        <artifactId>Database-Core</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>

    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>1.18.46</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.16.0</version>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>1.18.46</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

Merge these elements into the existing POM. Core brings in the other Database libraries, PostgreSQL JDBC, HikariCP, jOOQ, Lettuce and Utilities. Applications may supply an SLF4J provider for logging. No Injector or Spring annotations are required for this setup.

## Database settings

Have a PostgreSQL server reachable at the configured host and port. The application user needs access to the selected database and permission to create its tables, columns and indexes. The driver can create a missing database through the postgres maintenance database if that user has CREATEDB; otherwise pre-create the database.

The application example reads these environment variables:

| Variable | Example | Meaning |
| --- | --- | --- |
| `DATABASE_HOST` | localhost | PostgreSQL host |
| `DATABASE_PORT` | 5432 | PostgreSQL port |
| `DATABASE_NAME` | foundation_example | Database name |
| `DATABASE_USERNAME` | foundation | Database user |
| `DATABASE_PASSWORD` | Your configured password | Database password |

These names belong to the example; Foundation does not automatically read environment variables or an application.yaml file.

DatabaseSettings requires all values, including the tenant String after the enabled tier set. Null or blank selects no tenant. The example enables LOCAL and POSTGRES and supplies a five-minute entity TTL, one-minute refresh, one-second batch interval, five-second marker lease and two-second marker wait. The initial setup writes synchronously to PostgreSQL and uses in-process coalescing for simultaneous ID loads.

## Account entity

`src/main/java/example/database/Account.java`:

```java
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
@Getter
@Setter
public final class Account implements Entity {

    @EqualsAndHashCode.Include
    private final UUID id;

    private Long loginCount = 0L;

    private String username, region;
}
```

The generated constructor takes only UUID, as required by EntityInstantiator. The final id has no setter. The boxed Long starts at 0L so a newly constructed account has a usable login counter. Username and region are assigned before save and before runtime indexing, so the example has useful initial lookup keys; null extracted values remove previous mappings.

## Account declarations

`src/main/java/example/database/AccountProperty.java`:

```java
@UtilityClass
public final class AccountProperty {

    public static final Property<Account, String> USERNAME = Property.persistent(Account.class, "username", String.class, Account::getUsername, Account::setUsername);
    public static final Property<Account, String> REGION = Property.persistent(Account.class, "region", String.class, Account::getRegion, Account::setRegion);

    public static final Property<Account, Long> LOGIN_COUNT = Property.persistent(Account.class, "login_count", Long.class, Account::getLoginCount, Account::setLoginCount);
}
```

`src/main/java/example/database/AccountIndexes.java`:

```java
@UtilityClass
public final class AccountIndexes {

    public static final UniqueIndex<Account, String> USERNAME = UniqueIndex.of("account_username", AccountProperty.USERNAME);

    public static final GroupedIndex<Account, String> REGION = GroupedIndex.of("account_region", AccountProperty.REGION, Set.of(Tier.LOCAL));
}
```

Properties register through their static initializers. The repository and holder constructors explicitly load the declaration classes before building row mappers, codecs and runtime index stores.

The username index inherits the holder's resolved tiers. Region is explicitly LOCAL so enabling Redis later does not trigger the grouped-index restriction. Neither declaration creates a SQL index. The repository declares those separately below.

For additional values, use Property.persistent for SQL-backed data and ValueConverter for types without a suitable stored representation. Property.memory is omitted from SQL schema and full saves, but still serialized into Redis; it does not mean private process-local state.

## Account repository

`src/main/java/example/database/AccountRepository.java`:

```java
public final class AccountRepository extends PostgresRepository<Account> {

    public AccountRepository(final PostgresDriver postgresDriver, final TenantScope tenantScope) {
        super(Account.class, "accounts", postgresDriver, tenantScope, AccountProperty.class);
    }

    @Override
    public Map<Property<?, ?>, PostgresIndexType> getIndexes() {
        return Map.of(AccountProperty.USERNAME, PostgresIndexType.UNIQUE, AccountProperty.REGION, PostgresIndexType.BTREE);
    }

    public Optional<Account> findByUsername(final String username) {
        return this.find(Query.where(AccountProperty.USERNAME, username)).stream().findFirst();
    }

    public List<Account> findByRegion(final String region) {
        return this.find(Query.where(AccountProperty.REGION, region));
    }
}
```

Core startup creates accounts with id, version, username, region and login_count, plus the declared SQL indexes. The UNIQUE username index enforces PostgreSQL uniqueness; the runtime UniqueIndex only stores a lookup mapping.

Repository queries read PostgreSQL directly. They do not retain entities locally or initialize runtime indexes. Use findByRegion for persistent coverage; a grouped runtime index covers only entities indexed in this process.

Use simple application-owned table and column names. Startup only adds missing columns/indexes; use explicit migrations for renames, type changes or data transformations.

## Account manager

`src/main/java/example/database/AccountManager.java`:

```java
public final class AccountManager extends EntityHolder<Account, AccountRepository> {

    public AccountManager(final AccountRepository accountRepository, final HolderContextFactory holderContextFactory) {
        super(Account.class, accountRepository, holderContextFactory, Set.of(Tier.LOCAL, Tier.POSTGRES), AccountProperty.class, AccountIndexes.class);
    }

    public Optional<Account> findByUsername(final String username) {
        return this.getByProperty(AccountProperty.USERNAME, username);
    }

    public List<Account> getIndexedByRegion(final String region) {
        return this.getAllByIndex(AccountIndexes.REGION, region);
    }

    public void rename(final Account account, final String username) {
        this.set(account, AccountProperty.USERNAME, username);
    }

    public long recordLogin(final Account account) {
        return this.increment(account, AccountProperty.LOGIN_COUNT, 1L);
    }
}
```

The holder exposes its typed `AccountRepository` through the inherited `getRepository()`. Its constructor loads declarations, asks HolderContextFactory.create(repository, tiers) for the context, initializes its helpers and calls HolderContextFactory.register(this). The factory implementation must register the holder and repository with the runtime that owns their lifecycle. Core's ContextFactory implements this contract and registers the holder with the database instance.

findByUsername uses getByProperty, allowing a PostgreSQL fallback on a cold runtime index. getByIndex(AccountIndexes.USERNAME, username) would only search index entries already populated. getIndexedByRegion likewise has no SQL fallback; use AccountRepository.findByRegion for all persisted matches.

Inherited `save`, `set`, `increment`, `decrement`, `decrementIfAtLeast` and `delete` perform tracked writes and maintain runtime indexes. Use direct setters to prepare a new entity before saving it.

## Application wiring

`src/main/java/example/database/AccountApplication.java`:

```java
public final class AccountApplication {

    public static void main(final String[] args) {
        final PostgresSettings postgresSettings = new PostgresSettings(System.getenv("DATABASE_HOST"), Integer.parseInt(System.getenv("DATABASE_PORT")), System.getenv("DATABASE_NAME"), System.getenv("DATABASE_USERNAME"), System.getenv("DATABASE_PASSWORD"), 10);
        final PostgresDriver postgresDriver = new PostgresDriver(postgresSettings);

        final DatabaseSettings databaseSettings = new DatabaseSettings(Set.of(Tier.LOCAL, Tier.POSTGRES), null, Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(2));
        final CoreDatabase coreDatabase = new CoreDatabase(databaseSettings, postgresDriver, null);

        try {
            final AccountRepository accountRepository = new AccountRepository(postgresDriver, coreDatabase.getTenantScope());
            final AccountManager accountManager = new AccountManager(accountRepository, coreDatabase.getContextFactory());

            coreDatabase.start();

            final Account account = new Account(UUID.randomUUID());

            account.setUsername("demo-%s".formatted(account.getId()));
            account.setRegion("au");
            accountManager.save(account);
            accountManager.hold(account.getId());

            final Account heldAccount = accountManager.get(account.getId()).orElseThrow();

            accountManager.recordLogin(heldAccount);

            System.out.println("Account %s has %s recorded login(s)".formatted(heldAccount.getUsername(), heldAccount.getLoginCount()));
            System.out.println("Persisted accounts in au: %s".formatted(accountRepository.findByRegion("au").size()));

            accountManager.release(account.getId());
        } finally {
            coreDatabase.stop();
        }
    }
}
```

Holder construction registers it through HolderContextFactory before start, so declarations, schema statements and refreshers are known at startup. CoreDatabase has no separate register(repository, factory, declarations) method. Drivers are not connected by their constructors. Core start connects them and applies schema; account operations run afterwards.

This example creates a new demo row on each run. save completes synchronously against PostgreSQL because no Redis entity storage is configured. hold retains a locally loaded copy; the example retrieves that held object before changing it.

Use one runtime per application lifecycle. Let the DI container construct the repository and manager before starting Core. In the application shutdown callback, stop accepting new work and call `coreDatabase.stop()`. Core drains admitted writes, closes registered holders and gives the batch scheduler a final flush window before disconnecting the drivers. Construct a fresh runtime for a new lifecycle.

Register the driver, tenant scope and holder factory in your application container. Use one lifecycle owner for Core startup and shutdown. `CoreDatabase.getHolder(Account.class)` and `getRepository(Account.class)` expose the API contracts after registration.

## Reads and writes

| Goal | Call | Coverage |
| --- | --- | --- |
| Fetch by UUID | accountManager.get(id) | Enabled tiers |
| Retain active account | accountManager.hold(id) | Load, Local retention, indexes and optional subscription |
| Check local retention | accountManager.holds(id) | Local only |
| Find username | accountManager.findByUsername(username) | Runtime index, then SQL fallback |
| Read indexed username | accountManager.getByIndex(AccountIndexes.USERNAME, username) | Populated runtime entries only |
| Query all region matches | accountRepository.findByRegion(region) | PostgreSQL |
| Read indexed region matches | accountManager.getIndexedByRegion(region) | Current process's grouped entries |
| Rename | accountManager.rename(account, username) | Property write and index update |
| Increment counter | accountManager.recordLogin(account) | Long increment write |
| Decrement with a floor | accountManager.decrementIfAtLeast(account, AccountProperty.LOGIN_COUNT, 1L, 0L) | Returns true only when the authority accepts the debit |
| Delete | accountManager.delete(account) | Authority, persistence, indexes and release |
| Stop retaining | accountManager.release(id) | Remove hold/index entries and unsubscribe |

holdAll() loads every repository row and retains/indexes each one; use it only when that working set fits memory. release is not deletion. save does not automatically hold an entity. Reindex changes runtime keys without persisting values.

`hold` and `holdAll` seed tracked versions from Redis when it is configured for the holder. Reload samples the Redis version around its read. A plain `get` leaves tracking alone. On a missing Redis hash, ordinary untracked mutations can fall through to PostgreSQL. Tracked non-SAVE writes and guarded decrements reject a missing hash. Use unconverted `Long` properties for counter operations.

## Optional Redis wiring

Redis acts as the write authority when enabled for a holder. Supply the driver and enable REDIS in both the database settings and the holder's tier set. The following setup is for a shared deployment:

```java
final Set<Tier> tierSet = Set.of(Tier.REDIS, Tier.POSTGRES);
final DatabaseSettings databaseSettings = new DatabaseSettings(tierSet, null, Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(2));
final RedisDriver redisDriver = new RedisDriver(new RedisSettings("localhost", 6379, null, null, 0, Duration.ofSeconds(2)));
final CoreDatabase coreDatabase = new CoreDatabase(databaseSettings, postgresDriver, redisDriver);
```

### Spring multi-instance

For a Spring application that selects a `MULTI_INSTANCE` profile, have the application's wiring give `AccountManager` the same `Set.of(Tier.REDIS, Tier.POSTGRES)` tier set. Its constructor passes those tiers to `EntityHolder`:

```java
public AccountManager(final AccountRepository accountRepository, final HolderContextFactory holderContextFactory) {
    super(Account.class, accountRepository, holderContextFactory, Set.of(Tier.REDIS, Tier.POSTGRES), AccountProperty.class, AccountIndexes.class);
}
```

This manager reads from Redis first and falls back to PostgreSQL. `holds(id)` is false and `getHeld()` is empty. The username index can use shared Redis storage. To keep all runtime indexes shared, omit the LOCAL region index declaration and the corresponding manager method. Use `AccountRepository.findByRegion` for region queries.

For a holder that also retains active entities in its own process, add LOCAL to the holder and database tier sets. Keep grouped indexes explicitly local and set a positive TTL longer than the holder refresh interval.

### Shared reads and writes

A PostgreSQL lookup can backfill Redis after a miss. Holders with REDIS use shared load markers when available. Holders without REDIS still coalesce same-process lookups through `InFlight` and call the repository loader directly.

Redis SAVE creates a hash when needed. Successful writes publish the version, origin, write kind and encoded changes. Core filters its own origin and sends PostgreSQL copies with expected version `0` to the in-memory batch queue. See [Redis entity writes](Database-Redis/README.md#entity-writes) and [batch scheduling](Database-Batch/README.md#scheduling-and-failures).

For holders with LOCAL, remote events update held objects via the holder's event executor. Gaps and SAVE events at or below the tracked version trigger a reload. A remote DELETE removes the held object and its index entries and leaves a delete marker. Redis Pub/Sub supplies live delivery.

## Tenant wiring

Set the tenant String in DatabaseSettings before constructing Core:

```java
final DatabaseSettings databaseSettings = new DatabaseSettings(Set.of(Tier.LOCAL, Tier.POSTGRES), "shop", Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(2));
```

Core creates TenantScope from that setting. Pass coreDatabase.getTenantScope() to repositories. Repository queries add tenant predicates; holder write keys and cache namespaces capture the instance tenant. Tenanted SQL indexes include tenant.

Do not switch a registered holder across tenants using TenantScope.run/call. Those overrides affect repository reads at call time, while its cache namespaces and write tenant were captured at registration. Use separate runtimes with fixed scopes. New tenanted tables use (tenant, id) as their primary key and SAVE conflict target; existing UUID-only primary keys require an explicit migration.

A no-tenant repository is unfiltered. Shared-load keys include tenant, table and UUID. `TenantScope` supplies the current tenant context.

## Verification

Run the example against a disposable PostgreSQL database, then inspect accounts with your usual SQL client:

```sql
SELECT id, version, username, region, login_count
FROM accounts
WHERE region = 'au';
```

| Check | Expected |
| --- | --- |
| First startup | accounts table and username/region indexes exist |
| Example save and login | A demo row with region au and login_count 1 |
| New application instance | Repository can load the same UUID |
| get(id) before hold | Returns data without creating a new Local hold |
| hold(id), then holds(id) | true with LOCAL enabled |
| findByUsername on a cold runtime | PostgreSQL fallback finds the row |
| getByIndex on a cold runtime | May be empty until indexing occurs |
| Grouped lookup after hold | Held account is included under its region |
| release(id) | holds(id) becomes false; SQL row remains |
| delete on disposable account | SQL row is removed in the no-Redis setup |
| Duplicate username save | PostgreSQL UNIQUE index rejects it |
| decrementIfAtLeast with floor 0 | The debit succeeds when it lands at 0, and a further debit returns false |

Test sources live under Database-Holder/src/test/java and Database-Core/src/test/java. They include JUnit/Mockito checks, real PostgreSQL/Redis tests through Testcontainers, benchmarks and a sixty-second soak. Run them from the repository root with JDK 25:

```shell
mvn -pl Database/Database-Core -am test
```

For optional Redis, additionally test a missing hash, hash expiry, consecutive/conflicting writes, a cold process, disconnect/reconnect, deletion, and PostgreSQL queue drainage. Inspect both stores after each case rather than relying on isReady(), which reports driver connection state only.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| No properties registered | Pass AccountProperty.class into the repository constructor |
| Index is never found | Pass AccountIndexes.class to the holder superclass before context creation |
| UUID constructor failure | Entity must declare a constructor taking UUID only |
| Grouped Redis index rejected | Give that index Set.of(Tier.LOCAL) |
| Cold runtime index misses an account | Use getByProperty or a repository query for SQL fallback |
| New reads never appear in getHeld | Call hold explicitly; ordinary reads do not retain locally |
| A direct setter did not persist | Use manager set/increment or save for persistent changes |
| Missing database cannot be created | Pre-create it or grant the configured user CREATEDB |
| Startup schema fails | Check DDL privileges, identifier names and stored property types |
| PostgreSQL has yet to reflect a Redis write | Check the batch interval and persistence logs |
| Redis refresh removes keys | Avoid zero TTL with refresh |
| Version conflict or stale row after batching | Redis is the authority; Core queues SQL copies with expected version 0. Inspect queue failures and compare both stores |
| Data appears under the wrong tenant | Scope must be fixed when registering the holder |
| Runtime cannot start after stop | Construct a new CoreDatabase and drivers for the new lifecycle |

Return to the [Database guide](README.md) or [Foundation](../README.md).

