<h1 align="center">Spring-Common</h1>

<p align="center">Shared configuration, IP resolution, path canonicalization, and filter ordering for Foundation's Spring modules.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet-0769AD?logo=jakartaee&logoColor=white"></a>
</p>

Spring-Common gives the Spring libraries shared account contracts, environment policy, cookie handling, client address resolution and canonical request paths. It depends on Foundation Utilities as well as Spring libraries.

Follow [Installation](INSTALLATION.md) for complete setup, configuration and troubleshooting. Read the [security model](SECURITY.md) for threats, trade-offs and trust boundaries.

## Contents

<details>
<summary>On this page</summary>

- [Installation](INSTALLATION.md)
- [Security model](SECURITY.md)
- [At a glance](#at-a-glance)
- [Account contracts](#account-contracts)
- [Creating a Common account provider](#creating-a-common-account-provider)
- [Production flag](#production-flag)
- [IP address resolution](#ip-address-resolution)
- [Path canonicalization](#path-canonicalization)
- [Filter order constants](#filter-order-constants)
- [Auto-configuration](#auto-configuration)
- [Security model](#security-model)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Responsibility |
| --- | --- |
| `FoundationProperties` | `foundation.production`, default false |
| `CookieHelper` | Set, delete and optionally Base64-encode cookies |
| `IpAddressProperties`, `IpAddressResolver`, `IpAddressRange` | Header selection, trusted peer literals/CIDRs and address validation |
| `UtilRequestPath` | Shared path normalization and segment-aware prefix checks |
| `AccountIdentity`, `AccountRoleDefinition` | UUID identity and ordinal role threshold |
| `CommonAccountProvider` | Account lookup by UUID |
| `FilterOrderConstants` | Shared filter order values |

## Account contracts

`AccountIdentity<AccountRole>` exposes `getId()` and `getRole()`. The role type implements `AccountRoleDefinition`, whose `name()` and `ordinal()` are supplied by an enum. The default `hasRole` accepts current ordinals greater than or equal to the required ordinal. Declare lower privileges first or override the policy. Common provides no account entity or role values.

## Creating a Common account provider

Implement `Optional<Account> getAccountById(final UUID id)` and register the provider as a bean. Return empty for an unknown UUID. This is data lookup, not request authentication. See the [complete account and provider setup](INSTALLATION.md#minimal-working-setup). Request authentication belongs to [Security](../Spring-Security/INSTALLATION.md#application-contracts).

## Production flag

`foundation.production` defaults to false. It selects cookie Secure, SameSite and host-prefix behavior, HSTS, IP header/validation policy and JWT missing-seed behavior. Set it explicitly in production. Read [Production flag](SECURITY.md#production-flag) before enabling it on a development HTTPS hostname.

## IP address resolution

Production reads `proxy-header-list`, initially `CF-Connecting-IP`; development reads `header-list`, initially `X-Forwarded-For`, `X-Real-IP`, `X-Client-IP`, `Forwarded`. The empty default `trusted-proxy-list` trusts headers from any peer and is unsafe on an internet-reachable origin. Configure real proxy CIDRs, header overwriting and an origin firewall.

With a nonempty trust list, untrusted peers use their acceptable socket address and their headers are ignored. Trusted peers without an acceptable header resolve empty with no peer fallback. Production rejects local address classes; development accepts private literals. See [resolution boundaries](SECURITY.md#client-address-resolution) and [all properties](INSTALLATION.md#settings).

`IpAddressResolver` is final. A replacement bean must be another explicitly constructed instance of that type with selected properties, rather than a subclass implementing a different algorithm.

## Path canonicalization

`UtilRequestPath.canonicalise` collapses slash runs, resolves dot segments clamped at root, supplies a leading slash and strips non-root trailing slashes. It preserves case. `getCanonicalPath(request)` applies it to Spring's application-relative path. `matchesPrefix` respects segment boundaries, so `/assetsx` is outside `/assets`.

## Filter order constants

| Order | Consumer |
| --- | --- |
| 0 | Security headers |
| 1 | Robots tag |
| 2 | CSRF |
| 3 | Page cache control |
| 4 | Canonical redirect |
| 5 | Static resource gate |

Common registers none of these filters itself. The consuming modules use the constants.

## Auto-configuration

`CommonAutoConfiguration` applies to servlet web applications. It binds Foundation and IP address properties and registers `CommonAccountProvider` as an empty provider, `IpAddressResolver` and `CookieHelper`, all conditional on a missing bean of the corresponding type. No module enable annotation or component scan is required. Non-web and reactive contexts do not activate it.

## Security model

Read [SECURITY.md](SECURITY.md) for the shared trust boundaries and vulnerability reporting.

## Installation and build

Use `me.trae.foundation.spring:Spring-Common:1.0-SNAPSHOT`. Other Spring library modules include it transitively. Install from the repository root with JDK 25 and Maven:

```shell
mvn -pl Spring/Spring-Common -am install
```

Return to the [Spring guide](../README.md) or [Foundation](../../README.md).