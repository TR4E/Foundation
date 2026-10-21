<h1 align="center">Spring</h1>

<p align="center">Reusable Spring Boot web components for security headers, CSRF protection, page frameworks, and rate limiting.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet-0769AD?logo=jakartaee&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Multi--module-C71A36?logo=apachemaven&logoColor=white"></a>
</p>

Foundation's Spring modules are independent, auto-configured web components for Spring Boot servlet applications. Each module registers its beans, filters, and interceptors through `@AutoConfiguration` when the JAR is on the classpath, with no `@Enable` annotations, `@ComponentScan` entries, or manual `@Configuration` classes required. Every module depends on Spring-Common for shared configuration and can otherwise be adopted independently.

## Contents

<details>
<summary>On this page</summary>

- [Modules](#modules)
- [Architecture](#architecture)
- [Installation](#installation)
- [Configuration overview](#configuration-overview)
- [Building and testing](#building-and-testing)

</details>

## Modules

| Module | Artifact | Purpose |
| --- | --- | --- |
| [Spring-Common](Spring-Common/README.md) | `me.trae.foundation.spring:Spring-Common` | Production flag, IP resolution, path canonicalization, and filter order constants |
| [Spring-Pages](Spring-Pages/README.md) | `me.trae.foundation.spring:Spring-Pages` | Page-as-a-class framework with role-based access, asset gating, cache control, navigation, and pagination |
| [Spring-Ratelimit](Spring-Ratelimit/README.md) | `me.trae.foundation.spring:Spring-Ratelimit` | Annotation-driven rate limiting per IP or per account, with pluggable storage |
| [Spring-Security](Spring-Security/README.md) | `me.trae.foundation.spring:Spring-Security` | Security response headers with per-directive CSP, and double-submit CSRF protection |

All modules depend on Spring-Common. Spring-Pages, Spring-Ratelimit, and Spring-Security are independent of each other and can be combined in any combination.

`Spring` itself is a Maven aggregator with `pom` packaging. Applications depend on the individual library artifacts.

## Architecture

### Auto-configuration

Each module ships an `@AutoConfiguration` class conditional on `ConditionalOnWebApplication(type = SERVLET)`. Adding a module to a non-web or reactive application registers nothing rather than failing at startup.

| Module | Auto-configuration | Runs after |
| --- | --- | --- |
| Spring-Common | `CommonAutoConfiguration` | |
| Spring-Pages | `PagesAutoConfiguration` | `CommonAutoConfiguration` |
| Spring-Ratelimit | `RateLimitAutoConfiguration` | `CommonAutoConfiguration` |
| Spring-Security | `SecurityAutoConfiguration` | `CommonAutoConfiguration` |

### Extension points

Every module provides defaults that work out of the box, with `@ConditionalOnMissingBean` on replaceable beans:

| Bean | Default | Replace when |
| --- | --- | --- |
| `IpAddressResolver` | Header-based resolver with private IP filtering | Custom proxy setup |
| `PageAccessResolver` | `DenyPageAccessResolver` (fails closed) | Any role-gated page |
| `RateLimitStore` | `MemoryRateLimitStore` (single instance) | Multi-instance deployment |
| `RateLimitAccountResolver` | `EmptyRateLimitAccountResolver` | Account-scoped limits |
| `CsrfTokenService` | `SecureRandom` cookie token | Custom token strategy |

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

Spring-Pages, Spring-Ratelimit, and Spring-Security each build their registries on `ContextRefreshedEvent` rather than during bean wiring, so the handler mapping cannot be forced to initialize early and end up half-built. Registration is guarded against repeated context refreshes.

## Installation

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

See each module's guide for the full property reference and defaults.

## Building and testing

From the repository root with JDK 25 and Maven:

```shell
mvn -f Spring/pom.xml test
```

The Spring tests need no external services. Return to the [Foundation README](../README.md).
