<h1 align="center">Spring-Common</h1>

<p align="center">Shared configuration, IP resolution, path canonicalization, and filter ordering for Foundation's Spring modules.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet-0769AD?logo=jakartaee&logoColor=white"></a>
</p>

Spring-Common is the base module every other Foundation Spring module depends on. It provides the production/development flag that drives behavior across modules, a security-aware IP address resolver, request path canonicalization, and the shared filter order constants that keep the entire filter chain declared in one place. It depends on Spring Boot and nothing else in Foundation.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Production flag](#production-flag)
- [IP address resolution](#ip-address-resolution)
- [Path canonicalization](#path-canonicalization)
- [Filter order constants](#filter-order-constants)
- [Auto-configuration](#auto-configuration)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Purpose |
| --- | --- |
| `FoundationProperties` | The `foundation.production` flag read by every module |
| `IpAddressProperties` | Header lists for production and development IP resolution |
| `IpAddressResolver` | Resolves the real client IP from a request, or returns empty when it cannot be trusted |
| `UtilRequestPath` | Canonicalizes request paths so every filter and interceptor compares the same string |
| `FilterOrderConstants` | Shared filter chain order across all Foundation Spring modules |

## Production flag

```yaml
foundation:
  production: true
```

One flag, read by every module. It controls:

| Module | Production behavior | Development behavior |
| --- | --- | --- |
| Spring-Common | `IpAddressResolver` uses proxy headers only; rejects private IPs | Uses standard headers; accepts private IPs |
| Spring-Security | Sends `Strict-Transport-Security` | Omits HSTS to avoid pinning localhost |
| Spring-Security | CSRF cookie uses `__Host-` prefix, `Secure`, `SameSite=Strict` | Plain `XSRF-TOKEN`, `SameSite=Lax`, no `Secure` |

The default is `false`. Set it explicitly in your production profile. Nothing warns you if it is missing, and every module falls back to development behavior.

## IP address resolution

### Configuration

```yaml
foundation:
  ip-address:
    proxy-header-list:
      - CF-Connecting-IP
    header-list:
      - X-Forwarded-For
      - X-Real-IP
      - X-Client-IP
      - Forwarded
```

In production only `proxy-header-list` is consulted. In development only `header-list` is.

The split is deliberate. `X-Forwarded-For` is client-settable and most reverse proxies append to it rather than replacing it, so trusting it in production lets anyone claim any IP. `CF-Connecting-IP` is overwritten by the edge proxy on every request, so it cannot be forged, but only while the origin is unreachable except through that proxy. If the origin has a public address someone can reach directly, the guarantee is gone.

`True-Client-IP` is deliberately absent from the defaults. It is only set by some edge providers on specific plan tiers with the feature enabled, and passes through untouched otherwise. Add it only if your deployment sets it.

### Usage

```java
@AllArgsConstructor
@RestController
@RequestMapping("/api/order")
public final class OrderController {

    private final IpAddressResolver ipAddressResolver;

    @PostMapping("/create")
    public ResponseEntity<?> create(final HttpServletRequest httpServletRequest) {
        final String address = this.ipAddressResolver.getIpAddressByRequest(httpServletRequest)
                .orElse(null);

        if (address == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok().build();
    }
}
```

### Why `Optional` instead of a fallback

The return is `Optional` rather than a fallback to `getRemoteAddr()` on purpose. Behind a reverse proxy or tunnel, every request carries the same remote address, so a fallback would quietly collapse every client into one bucket for anything keyed by IP. An empty result means the request did not arrive through the expected proxy path, which is something the application should know rather than paper over.

### Address validation

In production, the resolver rejects any local, loopback, site-local, link-local, multicast, or IPv6 unique-local address. IPv4-mapped IPv6 addresses such as `::ffff:127.0.0.1` are unwrapped before testing, since the embedded address would otherwise slip past every check. Carrier-grade NAT (`100.64.0.0/10`) is accepted, because that is a real client behind a carrier.

In development, every parseable address is accepted, including private ranges, so local testing works.

### Replacing the resolver

Declare a bean of the same type and the auto-configuration yields:

```java
@Bean
public IpAddressResolver ipAddressResolver(final FoundationProperties foundationProperties, final IpAddressProperties ipAddressProperties) {
    return new MyIpAddressResolver(foundationProperties, ipAddressProperties);
}
```

## Path canonicalization

### `UtilRequestPath`

```java
final String path = UtilRequestPath.getCanonicalPath(httpServletRequest);
```

Collapses runs of slashes, strips the trailing slash, and guarantees a leading slash. Case is left alone, since paths are case-sensitive and folding case would create duplicate URLs rather than remove them. Returns `"/"` for null or blank input.

`matchesPrefix` is segment-aware, so `/apifoo` does not match the prefix `/api`:

```java
if (UtilRequestPath.matchesPrefix(path, "/api")) {
    return;
}
```

The match succeeds when the path equals the prefix exactly, or when the character immediately after the prefix is `/`. This prevents partial-segment matches from bypassing prefix-based filters.

This is only a security property if every component compares canonical paths. One filter reading `getRequestURI()` directly reopens the mismatch it closes.

## Filter order constants

```java
public static final int SECURITY_HEADERS = 0;
public static final int ROBOTS_TAG = 1;
public static final int CSRF = 2;
public static final int PAGE_CACHE_CONTROL = 3;
public static final int PAGE_TRAILING_SLASH = 4;
public static final int STATIC_RESOURCE = 5;
```

All Foundation Spring filters register their order from this class so the chain is declared in one place across modules. Headers run first so even a rejected response carries them. CSRF runs before anything page-aware so a forged write is rejected before the rest of the chain touches it.

| Position | Filter | Module |
| --- | --- | --- |
| 0 | `SecurityHeadersFilter` | Spring-Security |
| 1 | `RobotsTagFilter` | Spring-Pages |
| 2 | `CsrfFilter` | Spring-Security |
| 3 | `PageCacheControlFilter` | Spring-Pages |
| 4 | `PageTrailingSlashFilter` | Spring-Pages |
| 5 | `StaticResourceFilter` | Spring-Pages |

## Auto-configuration

`CommonAutoConfiguration` is conditional on a servlet web application. It registers `FoundationProperties`, `IpAddressProperties`, and `IpAddressResolver` automatically when the JAR is on the classpath. There is no `@Configuration` class, `@ComponentScan` entry, or `@Enable` annotation to add. Adding the JAR to a non-web or reactive application registers nothing rather than failing at startup.

The `IpAddressResolver` bean is `@ConditionalOnMissingBean`, so declaring your own replaces the default.

## Installation and build

```xml
<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Common</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Other Foundation Spring modules bring this in transitively. From the repository root with JDK 25 and Maven:

```shell
mvn -pl Spring/Spring-Common -am install
```

Return to the [Spring guide](../README.md) or [Foundation](../../README.md).
