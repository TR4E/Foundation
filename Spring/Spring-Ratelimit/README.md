<h1 align="center">Spring-Ratelimit</h1>

<p align="center">Annotation-driven rate limiting for Spring Boot controllers with per-IP and per-account scoping.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet-0769AD?logo=jakartaee&logoColor=white"></a>
</p>

Put `@RateLimit` on a method or `@RateLimitShared` on a controller, and the limit is enforced before the handler runs. The module uses a fixed-window algorithm keyed by client IP or account identity, with an in-memory default store that can be replaced with a shared store for multi-instance deployments. Depends on [Spring-Common](../Spring-Common/README.md) only.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Per-method limits](#per-method-limits)
- [Per-controller limits](#per-controller-limits)
- [Precedence](#precedence)
- [Base controllers and inheritance](#base-controllers-and-inheritance)
- [Scope and account resolution](#scope-and-account-resolution)
- [Unresolvable IPs](#unresolvable-ips)
- [Response format](#response-format)
- [Storage and multi-instance deployments](#storage-and-multi-instance-deployments)
- [What it does not cover](#what-it-does-not-cover)
- [Auto-configuration](#auto-configuration)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Purpose |
| --- | --- |
| `@RateLimit` | Per-method limit with its own bucket |
| `@RateLimitShared` | Controller-wide limit, either one shared bucket or the same numbers per method |
| `RateLimitTarget` | Whether a `@RateLimitShared` creates one bucket for the whole controller or one per method |
| `RateLimitScope` | Whether the bucket is keyed by IP address or by account identity |
| `RateLimitStore` | The storage contract: one atomic `tryConsume` call |
| `MemoryRateLimitStore` | In-memory default implementation for single-instance deployments |
| `RateLimitAccountResolver` | How the module finds the current account, implemented by the application |
| `RateLimitRegistry` | Built once at startup from the handler mappings |
| `RateLimitInterceptor` | Enforces the limit in `preHandle` before the controller method runs |

## Per-method limits

Each endpoint gets its own bucket:

```java
@RestController
@RequestMapping("/api/invoice")
public final class InvoiceController {

    @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 5, duration = 1, unit = TimeUnit.MINUTES)
    @PostMapping("/create")
    public ResponseEntity<?> create(@Valid @RequestBody final CreateInvoiceRequest request) {
        return ResponseEntity.ok().build();
    }
}
```

Five requests per minute per IP address for this endpoint. The bucket key includes the method signature, so two methods on the same controller each have independent counters.

## Per-controller limits

`@RateLimitShared` declares the limit once for the whole controller, and `target` decides what that means.

### `RateLimitTarget.TYPE`: one shared bucket

```java
@RateLimitShared(target = RateLimitTarget.TYPE, scope = RateLimitScope.IP_ADDRESS, attempts = 30, duration = 1, unit = TimeUnit.MINUTES)
@RestController
@RequestMapping("/api/order")
public final class OrderController {

    @GetMapping("/{id}")
    public ResponseEntity<?> fetch(@PathVariable final String id) {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@Valid @RequestBody final CreateOrderRequest request) {
        return ResponseEntity.ok().build();
    }
}
```

Thirty requests across both endpoints combined, not thirty each. Use this to cap what one client can do to a whole area of the application regardless of which endpoint they pick.

### `RateLimitTarget.METHOD`: same numbers, separate buckets

```java
@RateLimitShared(target = RateLimitTarget.METHOD, scope = RateLimitScope.IP_ADDRESS, attempts = 30, duration = 1, unit = TimeUnit.MINUTES)
@RestController
@RequestMapping("/api/order")
public final class OrderController {
}
```

Thirty each. Use this when every endpoint in a controller wants the same limit without competing for one budget, and you do not want to repeat `@RateLimit` on every method.

The only difference between the two is the bucket key. `TYPE` gives every method the same key so they share one window; `METHOD` gives each its own.

## Precedence

A method-level `@RateLimit` replaces whatever the controller declared, for that method only. It does not stack, and the method no longer counts against the controller's bucket:

```java
@RateLimitShared(target = RateLimitTarget.TYPE, scope = RateLimitScope.IP_ADDRESS, attempts = 30, duration = 1, unit = TimeUnit.MINUTES)
@RestController
@RequestMapping("/api/order")
public final class OrderController {

    @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 3, duration = 10, unit = TimeUnit.MINUTES)
    @PostMapping("/refund")
    public ResponseEntity<?> refund(@Valid @RequestBody final RefundOrderRequest request) {
        return ResponseEntity.ok().build();
    }
}
```

Every attribute except `name` is required, so a limit can never be half-declared.

## Base controllers and inheritance

`@RateLimitShared` is `@Inherited`, so a limit on an abstract base controller applies to everything extending it:

```java
@RateLimitShared(target = RateLimitTarget.METHOD, scope = RateLimitScope.IP_ADDRESS, attempts = 60, duration = 1, unit = TimeUnit.MINUTES)
public abstract class AbstractApiController {
}

@RestController
@RequestMapping("/api/order")
public final class OrderController extends AbstractApiController {

    @PostMapping("/create")
    public ResponseEntity<?> create(@Valid @RequestBody final CreateOrderRequest request) {
        return ResponseEntity.ok().build();
    }
}
```

Every controller extending the base gets the floor rate, and writes its own annotation only when it wants something tighter. A subclass declaring `@RateLimitShared` replaces the inherited one rather than adding to it.

Two limitations of Java's `@Inherited`: it follows class inheritance only, so a limit on an interface is not inherited by implementors. And it does nothing for `@RateLimit`, which is read from the method directly, so a method declared on a superclass or interface needs its annotation on the declaration the handler mapping actually resolves to.

## Scope and account resolution

`RateLimitScope.IP_ADDRESS` keys the bucket by the resolved client IP from [Spring-Common's `IpAddressResolver`](../Spring-Common/README.md#ip-address-resolution).

`RateLimitScope.ACCOUNT` keys it by the current account identity, falling back to IP when nobody is authenticated. Account scope is the only thing that stops an attacker rotating through addresses, but it needs the module to see your principal. Implement the resolver:

```java
@AllArgsConstructor
@Component
public final class MyRateLimitAccountResolver implements RateLimitAccountResolver {

    private final AccountManager accountManager;

    @Override
    public Optional<String> getAccountIdByRequest(final HttpServletRequest httpServletRequest) {
        return this.accountManager.getAccountByRequest(httpServletRequest)
                .map(account -> account.getId().toString());
    }
}
```

Without one, the default `EmptyRateLimitAccountResolver` always returns empty, so `ACCOUNT` behaves exactly like `IP_ADDRESS`.

## Unresolvable IPs

An IP-scoped request with no resolvable IP is rejected with `429`. Account-scoped requests use an authenticated account ID when available and need an IP only for the anonymous fallback.

## Response format

A limited request never reaches the controller. It gets:

- **Status:** `429 Too Many Requests`
- **Headers:** `Retry-After` (seconds), `Cache-Control: no-store`
- **Body:** UTF-8 JSON

```json
{
    "message": "Too many requests.",
    "retryAfter": 42
}
```

The bucket key never appears in the response. It exists only as a map key inside the store.

A client already over its limit does not increment the counter further, so hammering a limited endpoint cannot extend its own lockout.

## Storage and multi-instance deployments

### Default: in-memory fixed window

`MemoryRateLimitStore` uses a `ConcurrentHashMap` of `RateLimitWindow` entries, swept every 60 seconds by a daemon thread that shuts down on context close. This is correct for a single instance.

Run more than one instance and each gets its own counters, so the effective limit multiplies by the instance count.

### Custom store for shared state

For a multi-instance deployment, implement the interface against a shared store:

```java
public interface RateLimitStore {

    long tryConsume(final String key, final RateLimitData rateLimitData);
}
```

Returns `0` when the request is allowed, or the milliseconds remaining in the window when it is limited.

It is a single atomic call rather than a read followed by a write on purpose. Anything else cannot be honored across instances, since two requests landing at once would both read the same count and both be allowed. A Redis implementation belongs in a Lua script that checks the count, increments, and sets the expiry in one round trip.

Declare it as a bean and it replaces the default:

```java
@Bean
public RateLimitStore rateLimitStore(final StringRedisTemplate stringRedisTemplate) {
    return new RedisRateLimitStore(stringRedisTemplate);
}
```

### Why fixed windows

Fixed windows are deliberate over sliding windows. A sliding window needs a sorted set per key and a trim on every call, which is far more storage traffic for a guarantee an API rate limiter does not need. The fixed window is simple, predictable, and cheap.

## What it does not cover

The limit is enforced by a `HandlerInterceptor`, which runs after the entire filter chain and only for requests that reach a mapped handler method. Requests rejected earlier in the chain and requests to paths with no handler are never counted. For endpoints the application defines, which all have handlers, that is not a gap.

## Auto-configuration

`RateLimitAutoConfiguration` is conditional on a servlet web application and runs after `CommonAutoConfiguration`. It registers:

- `RateLimitStore` (default: `MemoryRateLimitStore`, replaceable with `@ConditionalOnMissingBean`)
- `RateLimitAccountResolver` (default: `EmptyRateLimitAccountResolver`, replaceable)
- `RateLimitRegistry`
- A `ContextRefreshedEvent` listener that scans `@RateLimit` and `@RateLimitShared` annotations from the handler mappings
- A `WebMvcConfigurer` adding `RateLimitInterceptor`

There is no `@Configuration` class, `@ComponentScan` entry, or `@Enable` annotation to add. Anything unannotated is not limited.

Adding the JAR to a non-web or reactive application registers nothing.

## Installation and build

```xml
<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Ratelimit</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Spring-Common comes in transitively. From the repository root with JDK 25 and Maven:

```shell
mvn -pl Spring/Spring-Ratelimit -am install
```

Return to the [Spring guide](../README.md) or [Foundation](../../README.md).
