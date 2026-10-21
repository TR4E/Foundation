<h1 align="center">Database-API</h1>

<p align="center">Entity, property, query, repository, holder, and tenant contracts for Foundation persistence.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven library" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database-API describes entities and persistence operations without depending on PostgreSQL, Redis, or a dependency injection framework. Use it in shared domain code, then add [Database-Core](../Database-Core/README.md) in the application that supplies persistence.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Entities and properties](#entities-and-properties)
- [Value converters](#value-converters)
- [Queries and pagination](#queries-and-pagination)
- [Repositories and holders](#repositories-and-holders)
- [Indexes](#indexes)
- [Tenants](#tenants)
- [Exceptions](#exceptions)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Contract | Purpose |
| --- | --- |
| `Entity` | Identify an entity through `UUID getId()` |
| `EntityProperty` / `EntityPropertyRegistry` | Describe and register typed values |
| `ValueConverter<Value, Stored>` | Convert domain values to supported stored values |
| `Query`, `Operator`, `Direction` | Describe filters, ordering, and tenant selection |
| `EntityPage` | Hold page contents, total count, page number, and size |
| `EntityRepository` | Query persistent rows and submit writes |
| `EntityHolder` | Cache-aware access, tracking, pinning, and mutations |
| `EntityIndex` / `IndexType` | Describe composite unique indexes and property index types |
| `Tenant` / `TenantScope` | Resolve row-level tenant identity |
| `Storage<Key, Value>` | A common read/write interface for storage implementations |
| `Database` | Inspect readiness and registered repositories |

## Entities and properties

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
}
```

Examples use Lombok and omit imports. The runtime reconstructs an entity using a constructor that accepts only its UUID; `@RequiredArgsConstructor` generates it for this model. The UUID is part of the entity contract and does not need property registration.

Register every other persisted value with its getter, setter, and wrapper type:

```java
public final class AccountProperties {

    public static final EntityProperty<Account, String> NAME = EntityProperty.registerCaseInsensitive(
            Account.class,
            "name",
            Account::getName,
            Account::setName,
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
}
```

| Registration choice | Behavior |
| --- | --- |
| `register(..., valueType, true)` | Persist the property in PostgreSQL |
| `register(..., valueType, false)` | Keep it in cache storage only |
| `registerCaseInsensitive(...)` | Preserve the stored string while normalizing lookup/index comparisons |
| `registerWithConverter(...)` | Persist a custom value using a converter |

Property types use wrappers such as `Long.class`, not primitive classes. `id` and `tenant_id` are reserved names. Registered superclass properties are inherited by subclasses. Registration happens during class initialization; pass the property-holder class to the Core repository constructor so it is loaded before schema inspection.

## Value converters

`ValueConverter<Value, Stored>` exposes `getValueType()`, `getStoredType()`, `serialize(value)`, and `deserialize(stored)`. Both PostgreSQL mapping and Redis codecs use this metadata.

Core provides `JsonValueConverter.of`, `ofList`, `ofSet`, and `ofMap` for JSONB values, plus `EnumValueConverter` for enum names. Enum values can also be persisted by name without an explicit converter. See the [Core guide](../Database-Core/README.md#schema-and-value-mapping) for supported column types.

## Queries and pagination

```java
final Query<Account> query = Query.where(AccountProperties.COINS, Operator.GREATER_THAN, 1000L)
        .orderBy(AccountProperties.COINS, Direction.DESCENDING)
        .limit(10);

final List<Account> accountList = accountRepository.findMany(query);
final EntityPage<Account> entityPage = accountRepository.findPage(query, 1, 10);
```

These examples assume the Core repository from the [Database quick start](../README.md#quick-start). Query methods mutate and return the same query object; build separate queries when filters need independent lifecycles.

| Method or operator | Purpose |
| --- | --- |
| `Query.of(Account.class)` | Start without a property condition |
| `where(...)` / `and(...)` | Add a condition; multiple conditions are combined with AND |
| `whereIn(...)` / `andIn(...)` | Match a collection of values |
| `EQUALS`, `NOT_EQUALS` | Equality checks |
| `GREATER_THAN`, `GREATER_THAN_OR_EQUALS`, `LESS_THAN`, `LESS_THAN_OR_EQUALS` | Range checks |
| `LIKE` | SQL pattern matching |
| `IS_NULL`, `IS_NOT_NULL` | Null checks |
| `andIdNotEquals(id)` | Exclude one built-in UUID without registering it as a property |
| `orderBy(...)`, `limit(...)`, `offset(...)` | Sort and limit direct query results |
| `tenant(tenantId)` / `allTenants()` | Override the default own-tenant query selection |

Use `whereIn` and `andIn` for `IN`; passing `Operator.IN` to an ordinary condition is rejected. Repository queries read PostgreSQL directly and see writes after commit.

Core pagination uses one-based page numbers, requires a positive size, and clamps the requested page to the available range. Use `getEntities()`, `getTotalCount()`, `getPage()`, and `getSize()` on the result. Choose a deterministic ordering for repeatable page contents.

## Repositories and holders

| Operation | Repository | Holder |
| --- | --- | --- |
| UUID lookup | `findById`, `findManyById` | `getById` through selected cache tiers |
| Property/query lookup | `findOne`, `findMany` | `getByProperty`, `getByIndex` |
| Counts and pages | `count`, `exists`, `findPage` | Use the repository |
| Creation and mutation | `save`, `update`, `delete` | `save`, `update`, `delete` with tracking and cache coordination |
| Local retention | Not applicable | `cache`, `evict`, `pin`, `unpin`, `getPinned` |
| Atomic numeric update | Runtime-specific implementation | `increment` |
| Transfer a unique value | Runtime-specific implementation | `transferUniqueProperty` |

The holder interface uses two type parameters: `EntityHolder<Account, AccountRepository>`. Some extended operations have default implementations that throw `UnsupportedOperationException`; Core's holder implements them.

`LookupTier` selects `LOCAL`, `REDIS`, and `DATABASE`. `InstanceMode` chooses single-instance tracking or multi-instance coordination. `RedisMode` enables or disables Redis for that holder. See the [Core guide](../Database-Core/README.md#holder-modes-and-cache-consistency) for supported combinations.

## Indexes

| Definition | Purpose |
| --- | --- |
| `getIndexes()` with `IndexType.UNIQUE` | A unique property, also used by holder value-to-ID indexes |
| `IndexType.BTREE` | A regular PostgreSQL index |
| `IndexType.GIN_TRGM` | A trigram index for pattern searches |
| `IndexType.BRIN` | A PostgreSQL block range index |
| `getCompositeIndexes()` with `EntityIndex.of(name, properties...)` | A unique combination of persistent properties |

Composite lookups supply values in the declared property order. See [lookup indexes](../Database-Lookup/README.md#property-and-composite-indexes) for cache behavior.

## Tenants

For `new Tenant("api", 1)`:

| Scope | Resolved tenant ID | Rows shared by |
| --- | --- | --- |
| `NONE` | No tenant column | All callers of the table |
| `GLOBAL` | `*` | All instances using that scope |
| `GROUP` | `api` | The group |
| `INSTANCE` | `api-1` | That specific instance |

Tenant scope partitions rows within a selected database. `GlobalDatabase` and `LocalDatabase` select configured database contexts; they do not replace tenant scope.

## Exceptions

All persistence exceptions extend unchecked `DatabaseException`.

| Exception | Meaning |
| --- | --- |
| `ConnectionException` | Driver connection failure or use before connecting |
| `SchemaException` | Invalid entity, property, converter, or repository setup |
| `QueryException` | Unsupported or invalid query construction |
| `UniqueValueTakenException` | A unique value is already claimed by another entity |
| `OptimisticLockException` | A persisted revision no longer matches the editing snapshot |

Background write failures are delivered to the Core failure handler rather than necessarily being thrown by the earlier mutation call.

## Installation and build

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-API</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

This module has no persistence-driver runtime dependency. From the repository root with JDK 25 and Maven:

```shell
mvn -pl Database/Database-API -am install
```

API tests need no external services. Return to the [Database guide](../README.md) or [Foundation](../../README.md).
