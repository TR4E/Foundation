<h1 align="center">Spring-Ratelimit</h1>

Annotation driven rate limiting for Spring Boot controllers. Put `@RateLimit` on a method or `@RateLimitShared` on a controller, and the limit is enforced before the handler runs.

Depends on Spring-Common only. It does not depend on Spring-Security, so the two can be used independently.

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet%20applications-0769AD?logo=jakartaee&logoColor=white"></a>
</p>



<details>
<summary>On this page</summary>

- [Contents](#contents)
- [Annotations](#annotations)
- [Scope](#scope)
- [When the IP cannot be resolved](#when-the-ip-cannot-be-resolved)
- [Response](#response)
- [Storage](#storage)
- [What it does not cover](#what-it-does-not-cover)
- [How to Wire into a Spring Boot Application](#how-to-wire-into-a-spring-boot-application)
</details>



## Contents

| Type | Purpose |
| --- | --- |
| `@RateLimit` | Per method limit, its own bucket |
| `@RateLimitShared` | Controller wide limit, either one shared bucket or the same numbers per method |
| `RateLimitTarget` | Which of those two a `@RateLimitShared` means |
| `RateLimitScope` | Whether the bucket is keyed by IP or by account |
| `RateLimitStore` | The storage contract, one atomic call |
| `MemoryRateLimitStore` | In memory default, single instance |
| `RateLimitAccountResolver` | How the module finds the current account, implemented by your application |
| `RateLimitRegistry` | Built once at startup from the handler mappings |
| `RateLimitInterceptor` | Enforces the limit in `preHandle` |

## Annotations

### Per method

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

### Per controller

`@RateLimitShared` declares the limit once for the whole controller, and `target` decides what that means.

`RateLimitTarget.TYPE` is one bucket for the lot:

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

`RateLimitTarget.METHOD` applies the same numbers to each method separately:

```java
@RateLimitShared(target = RateLimitTarget.METHOD, scope = RateLimitScope.IP_ADDRESS, attempts = 30, duration = 1, unit = TimeUnit.MINUTES)
@RestController
@RequestMapping("/api/order")
public final class OrderController {
}
```

Thirty each. This is the one to reach for when every endpoint in a controller wants the same limit and you do not want them competing for one budget, since it saves repeating `@RateLimit` on every method.

Only the key changes between the two. `TYPE` gives every method in the controller the same key so they share one window, `METHOD` gives each its own.

### Precedence

A method level `@RateLimit` replaces whatever the controller declared, for that method only. It does not stack, and the method no longer counts against the controller's bucket:

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

Every attribute except `name` is required, so a limit can never be half declared.

### Base controllers

`@RateLimitShared` is `@Inherited`, so a limit declared on an abstract base controller applies to everything extending it:

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

Every controller extending the base gets the floor, and writes its own annotation only when it wants something tighter. A subclass declaring `@RateLimitShared` itself replaces the inherited one rather than adding to it.

Two limits of Java's `@Inherited`, worth knowing before you lean on it. It follows class inheritance only, so a limit on an interface is not inherited by implementors. And it does nothing for `@RateLimit`, which is read straight off the method, so a method declared on a superclass or an interface needs its annotation on the declaration the handler mapping actually resolves to.

## Scope

`IP_ADDRESS` keys the bucket by the resolved client IP. `ACCOUNT` keys it by the current account, falling back to the IP when nobody is authenticated.

Account scope is the only thing that stops an attacker rotating through addresses, but it needs the module to see your principal, which it cannot do on its own. Implement the resolver:

```java
@Component
public final class MyRateLimitAccountResolver implements RateLimitAccountResolver {

    private final AccountManager accountManager;

    public MyRateLimitAccountResolver(final AccountManager accountManager) {
        this.accountManager = accountManager;
    }

    @Override
    public Optional<String> getAccountIdByRequest(final HttpServletRequest httpServletRequest) {
        return this.accountManager.getAccountByRequest(httpServletRequest).map(Account::getId);
    }
}
```

Without one, `ACCOUNT` behaves exactly like `IP_ADDRESS`, since the default resolver always returns empty.

## When the IP cannot be resolved

The request is rejected. An unresolvable IP means the request did not arrive through the expected proxy, and allowing it through unlimited would be a free bypass of every IP keyed limit on the application.

## Response

A limited request never reaches the controller. It gets `429` with `Retry-After` in seconds, `Cache-Control: no-store`, and a UTF-8 JSON body:

```json
{
  "message":" Too many requests.",
  "retryAfter": 42
}
```

The bucket key never appears in the response. It exists only as a map key inside the store.

A client already over its limit stops incrementing the counter, so hammering a limited endpoint cannot extend its own lockout.

## Storage

The default is in memory with a fixed window per key, swept every sixty seconds by a daemon thread, shut down on context close.

That is correct for a single instance. Run more than one and each gets its own counters, so the effective limit multiplies by the instance count. For a multi instance deployment, implement the interface against a shared store:

```java
public interface RateLimitStore {

    long tryConsume(final String key, final RateLimitData rateLimitData);
}
```

Returns `0` when the request is allowed, or the milliseconds remaining when it is limited.

It is a single atomic call rather than a read followed by a write on purpose. Anything else cannot be honoured across instances, since two requests landing at once would both read the same count and both be allowed. A Redis implementation belongs in a Lua script doing the check, the increment and the expiry in one round trip, checking the count before incrementing rather than after.

Declare it as a bean and it replaces the default:

```java
@Bean
public RateLimitStore rateLimitStore(final StringRedisTemplate stringRedisTemplate) {
    return new RedisRateLimitStore(stringRedisTemplate);
}
```

Keys carry the declaring class and, for method scoped limits, the method signature. That is internal detail, so keep it out of anything user facing if you log rejections.

Fixed windows are deliberate over sliding windows. A sliding window needs a sorted set per key and a trim on every call, which is far more traffic for a guarantee an API limiter does not need.

## What it does not cover

The limit is enforced by an interceptor, which runs after the whole filter chain and only for requests that reach a mapped handler method. Requests rejected earlier in the chain, and requests to paths with no handler, are never counted. For endpoints you wrote, which all have handlers, that is not a gap.

## How to Wire into a Spring Boot Application

Add the dependency:

```xml
<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Ratelimit</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

That is the whole wiring step. `RateLimitAutoConfiguration` registers the store, the account resolver, the registry and the interceptor on the classpath alone, so there is no `@Configuration` class, no `@ComponentScan` entry and no `@Enable` annotation to add in the consuming application. Spring-Common comes in transitively.

Then annotate the endpoints you want limited. Anything unannotated is not limited.

Optional, in order of how often you will want them:

* Implement `RateLimitAccountResolver` as a bean to make `ACCOUNT` scope work.
* Implement `RateLimitStore` as a bean if you run more than one instance.

The registry is populated on `ContextRefreshedEvent` rather than during bean wiring, so it cannot force the handler mapping to initialise early and end up with a half built map.

The auto configuration is conditional on a servlet web application, so adding the jar to a non web or reactive application registers nothing rather than failing at startup.
