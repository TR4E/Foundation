<h1 align="center">Spring</h1>

<p align="center">Reusable Spring Boot web components for Ed25519 JWT authentication, security headers, CSRF protection, pages and rate limiting.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet-0769AD?logo=jakartaee&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Multi--module-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Foundation's Spring modules provide Ed25519 JWT authentication and auto-configured web components for Spring Boot servlet applications. Each module registers its beans, filters, and interceptors through `@AutoConfiguration` when the JAR is on the classpath, with no `@Enable` annotations, `@ComponentScan` entries, or manual `@Configuration` classes required. Every module uses Spring-Common for shared configuration. Pages and Ratelimit depend on Spring-Security for authenticated request lookup and security filters.

## Contents

<details>
<summary>On this page</summary>

- [Modules](#modules)
- [Architecture](#architecture)
- [Installation](#installation)
- [Configuration overview](#configuration-overview)
- [Security models](#security-models)
- [Building and testing](#building-and-testing)

</details>

## Modules

| Module | Artifact | Purpose |
| --- | --- | --- |
| [Spring-Common](Spring-Common/README.md) | `me.trae.foundation.spring:Spring-Common` | Production flag, IP resolution, path canonicalization, and filter order constants |
| [Spring-Pages](Spring-Pages/README.md) | `me.trae.foundation.spring:Spring-Pages` | Page-as-a-class framework with role-based access, asset gating, cache control, navigation, and pagination |
| [Spring-Ratelimit](Spring-Ratelimit/README.md) | `me.trae.foundation.spring:Spring-Ratelimit` | Annotation-driven rate limiting per IP or per account, with pluggable storage |
| [Spring-Security](Spring-Security/README.md) | `me.trae.foundation.spring:Spring-Security` | Ed25519 JWT authentication with rotation and revocation, response headers, CSP and double-submit CSRF protection |

Spring-Security depends on Spring-Common. Spring-Pages and Spring-Ratelimit depend on Spring-Security and can be used together or separately.

`Spring` itself is a Maven aggregator with `pom` packaging. Applications depend on the individual library artifacts.

## Architecture

### Auto-configuration

Each module ships an `@AutoConfiguration` class conditional on `ConditionalOnWebApplication(type = SERVLET)`. Adding a module to a non-web or reactive application registers nothing rather than failing at startup.

| Module | Auto-configuration | Runs after |
| --- | --- | --- |
| Spring-Common | `CommonAutoConfiguration` | |
| Spring-Pages | `PagesAutoConfiguration` | `SecurityAutoConfiguration` |
| Spring-Ratelimit | `RateLimitAutoConfiguration` | `SecurityAutoConfiguration` |
| Spring-Security | `SecurityAutoConfiguration` | `CommonAutoConfiguration` |

### Extension points

Each module provides defaults, but authentication and deployment trust require application wiring, with `@ConditionalOnMissingBean` on replaceable beans:

| Bean | Default | Replace when |
| --- | --- | --- |
| `IpAddressResolver` | Configured final resolver, local-address filtering in production | Explicit construction with selected properties |
| `CookieHelper` | Environment-aware cookie attributes and optional Base64 | Explicit construction with selected FoundationProperties |
| `RateLimitStore` | `MemoryRateLimitStore` (single instance) | Multi-instance deployment |
| `PageAccessResolver` | `AccountPageAccessResolver` using the shared account provider | Custom page authorization or response behavior |
| `CommonAccountProvider` | `EmptyCommonAccountProvider` in Spring-Common | Account data lookup by UUID |
| `SecurityAccountProvider` | `EmptySecurityAccountProvider` in Spring-Security | Authenticated request lookup for Pages and Ratelimit |
| `CsrfTokenService` | `SecureRandom` cookie token | Explicit construction with application-selected properties; the service class is final |

Provider setup examples: [Common lookup by UUID](Spring-Common/README.md#creating-a-common-account-provider) and [Security request authentication](Spring-Security/README.md#creating-a-security-account-provider). The linked installation guides contain complete provider wiring.

### Filter chain

All filters share order constants from `FilterOrderConstants` in Spring-Common. The complete chain across all modules:

| Order | Filter | Module | Dispatch types |
| --- | --- | --- | --- |
| 0 | `SecurityHeadersFilter` | Spring-Security | `REQUEST`, `ERROR`, `ASYNC` |
| 1 | `RobotsTagFilter` | Spring-Pages | `REQUEST`, `ERROR` |
| 2 | `CsrfFilter` | Spring-Security | `REQUEST` |
| 3 | `PageCacheControlFilter` | Spring-Pages | `REQUEST`, `ERROR` |
| 4 | `PageTrailingSlashFilter` | Spring-Pages | `REQUEST` |
| 5 | `StaticResourceFilter` | Spring-Pages | `REQUEST` |

Interceptors run after the filter chain on mapped handler methods:

| Interceptor | Module | Purpose |
| --- | --- | --- |
| `PageAccessInterceptor` | Spring-Pages | Role enforcement before page rendering |
| `RateLimitInterceptor` | Spring-Ratelimit | Rate limit enforcement before handler execution |

### Registry pattern

Spring-Pages, Spring-Ratelimit, and Spring-Security each build their registries on `ContextRefreshedEvent` rather than during bean wiring, so the handler mapping cannot be forced to initialize early and end up half-built. Page registration is guarded against repeated context refreshes; the CSRF and rate-limit listeners replace their registry contents on refresh.

## Installation

Follow the installation guides for [Common](Spring-Common/INSTALLATION.md), [Security](Spring-Security/INSTALLATION.md), [Pages](Spring-Pages/INSTALLATION.md) and [Ratelimit](Spring-Ratelimit/INSTALLATION.md). The Spring aggregator has no library artifact to add.

Requires JDK 25 and Maven. Build and install from the repository root:

```shell
mvn -pl Spring/Spring-Common,Spring/Spring-Pages,Spring/Spring-Ratelimit,Spring/Spring-Security -am install
```

Then add the modules your application needs:

```xml
<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Security</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Pages</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Ratelimit</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Spring-Common comes in transitively through any of them.

## Configuration overview

All configuration lives under the `foundation` namespace:

```yaml
foundation:
  production: true

  ip-address:
    proxy-header-list:
      - CF-Connecting-IP
    trusted-proxy-list:
      - 192.0.2.0/24

  pages:
    login-path: /auth
    redirect-parameter: redirect
    page-size: 10
    system-path-list:
      - /api
      - /stream

  security:
    headers:
      script-src:
        - "'self'"
        - https://cdn.example.com
    csrf:
      cookie-duration: 7d
      require-same-origin: true
```

The CIDR is a documentation example only; replace it with actual proxy ranges and match the origin firewall. See each installation guide for all properties and defaults. JWT setup also requires distinct key seeds and application providers.

## Security models

Read the security models for [Common](Spring-Common/SECURITY.md), [Security](Spring-Security/SECURITY.md), [Ratelimit](Spring-Ratelimit/SECURITY.md) and [Pages](Spring-Pages/SECURITY.md). For application authentication setup, use [Wiring JWT end to end](Spring-Security/README.md#wiring-jwt-end-to-end).

## Building and testing

From the repository root with JDK 25 and Maven:

```shell
mvn -pl Spring/Spring-Common,Spring/Spring-Security,Spring/Spring-Pages,Spring/Spring-Ratelimit -am test
```

The Spring tests need no external services. Return to the [Foundation README](../README.md).