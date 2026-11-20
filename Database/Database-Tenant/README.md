<h1 align="center">Database-Tenant</h1>

<p align="center">Tenant identities and scoped repository selection.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Database-Tenant supplies Tenant and a Java 25 ScopedValue-backed TenantScope. Core creates the instance scope from DatabaseSettings.tenant and shares it with its context and namespace factories.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Instance tenant](#instance-tenant)
- [Lexical overrides](#lexical-overrides)
- [Row and cache boundaries](#row-and-cache-boundaries)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Responsibility |
| --- | --- |
| Tenant | Validated name and namespace/key helpers |
| TenantScope | Default tenant and lexical overrides |
| TenantException | Null/blank tenant name passed to Tenant.of |

## Instance tenant

DatabaseSettings.tenant is a String. Core treats null/blank as no tenant and otherwise calls Tenant.of. Obtain the resulting scope with coreDatabase.getTenantScope() and pass it to repository constructors.

Tenant.of rejects null/blank names and otherwise preserves the supplied text. prefix(table) produces tenant:table; key(table, id) appends UUID.

```java
final TenantScope tenantScope = coreDatabase.getTenantScope();
final ExampleRepository exampleRepository = new ExampleRepository(postgresDriver, tenantScope);
```

A standalone new TenantScope(null) returns Optional.empty() outside a binding. getTenant checks isBound() before consulting the nullable default.

## Lexical overrides

run(tenant, runnable) binds a tenant for a callback. call(tenant, supplier) returns the callback's result. The previous binding is restored when it exits. This does not automatically propagate into arbitrary executor tasks.

Repository reads resolve scope at call time. Holder contexts capture tenant identity and namespaces at construction. An override around an existing holder does not switch its cache keys or captured write tenant. Use fixed scopes for database instances.

## Row and cache boundaries

Local capacity and TTL are database settings. Tenant prefixes still apply to cache namespaces and shared load keys.

A tenant adds a PostgreSQL predicate and tenant column during schema setup. Without a tenant, repository reads are unfiltered. Unique SQL indexes include tenant when schema generation sees one.

New tenanted tables use (tenant, id) as their primary key and SAVE conflict target, allowing the same UUID in different tenants. Existing tables with an id-only primary key require an explicit migration. Core cache namespaces include tenant. Holders with shared Redis load coordination also include tenant in their marker keys. Marker keys resolve scope at lookup time, while cache namespaces remain fixed for an existing holder. The marker prefix uses the literal string `null` for an absent tenant. Core registries use entity class/table rather than tenant. TenantScope supplies execution context, not authorization.

## Installation and build

Compile dependencies: Database-API. The parent also supplies SLF4J and provided Lombok. Test-only dependencies do not become part of the runtime.

```xml
<dependency>
    <groupId>me.trae.foundation.database</groupId>
    <artifactId>Database-Tenant</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

From the repository root, using JDK 25 and Maven:

```shell
mvn -pl Database/Database-Tenant -am install
```

See the [Database guide](../README.md) for generic examples and test commands. Return to [Foundation](../../README.md).
