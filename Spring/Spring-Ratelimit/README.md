<h1 align="center">Spring-Ratelimit</h1>

<p align="center">Annotation-driven rate limiting for Spring Boot controllers with per-IP and per-account scoping.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet-0769AD?logo=jakartaee&logoColor=white"></a>
</p>

Put `@RateLimit` on a method or `@RateLimitShared` on a controller, and the limit is enforced before the handler runs. The module uses a fixed-window algorithm keyed by client IP or account identity, with an in-memory default store that can be replaced with a shared store for multi-instance deployments. Depends on [Spring-Security](../Spring-Security/README.md), which brings Spring-Common transitively.

Follow [Installation](INSTALLATION.md) for complete compiling setup examples, settings and troubleshooting.

## Contents

<details>
<summary>On this page</summary>

- [Installation](INSTALLATION.md)
- [At a glance](#at-a-glance)
- [Per-method limits](#per-method-limits)
- [Per-controller limits](#per-controller-limits)
- [Precedence](#precedence)
- [Base controllers and inheritance](#base-controllers-and-inheritance)
- [Deliberately shared named budgets](#deliberately-shared-named-budgets)
- [Startup budget validation](#startup-budget-validation)
- [Scope and account resolution](#scope-and-account-resolution)
- [Trusted proxies](#trusted-proxies)
- [Unresolvable IPs](#unresolvable-ips)
- [Response format](#response-format)
- [Storage and multi-instance deployments](#storage-and-multi-instance-deployments)
- [What it does not cover](#what-it-does-not-cover)
- [Auto-configuration](#auto-configuration)
- [Security model](SECURITY.md)
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
| `SecurityAccountProvider` | Resolves the authenticated application account |
| `RateLimitRegistry` | Rebuilt on context refresh from the handler mappings |
| `RateLimitInterceptor` | Enforces the limit in `preHandle` before the controller method runs |

See the [security model](SECURITY.md) for trust assumptions, cluster limits and abuse coverage.

## Per-method limits

With a blank name, each annotated method gets its own bucket:

See the [complete application files](INSTALLATION.md#minimal-working-setup).

The installation example allows two requests per minute per IP for /invoices; the named example below shares five across its two endpoints. By default the key includes the method signature. A nonblank name overrides it and can deliberately share budgets; keep scope and settings consistent across uses of a name.

## Per-controller limits

`@RateLimitShared` declares the limit once for the whole controller, and `target` decides what that means.

### `RateLimitTarget.TYPE`: one shared bucket

See the [complete application files](INSTALLATION.md#minimal-working-setup).

Thirty requests across both endpoints combined, not thirty each. Use this to cap what one client can do to a whole area of the application regardless of which endpoint they pick.

### `RateLimitTarget.METHOD`: same numbers, separate buckets

See the [complete application files](INSTALLATION.md#minimal-working-setup).

Thirty each. Use this when every endpoint in a controller wants the same limit without competing for one budget, and you do not want to repeat `@RateLimit` on every method.

With no nonblank name, TYPE gives methods the class key and METHOD gives each its reflected key. A nonblank name replaces either fallback and can make METHOD rules share a budget.

## Precedence

A method-level `@RateLimit` replaces whatever the controller declared, for that method only. It does not stack, and the method no longer counts against the controller's bucket:

See the [complete application files](INSTALLATION.md#minimal-working-setup).

Every attribute except name is required. A nonblank name replaces the reflected key; attempts below 1 or durations below 1 millisecond after conversion fail registration.

## Base controllers and inheritance

`@RateLimitShared` is `@Inherited`, so a limit on an abstract base controller applies to everything extending it:

See the [complete application files](INSTALLATION.md#minimal-working-setup).

Subclasses inherit the shared settings unless they replace them; a replacement can be tighter or looser. A subclass declaring `@RateLimitShared` replaces the inherited one rather than adding to it.

Two limitations of Java's `@Inherited`: it follows class inheritance only, so a limit on an interface is not inherited by implementors. And it does nothing for `@RateLimit`, which is read from the method directly, so a method declared on a superclass or interface needs its annotation on the declaration the handler mapping actually resolves to.

## Deliberately shared named budgets

A nonblank name is the registry key for both annotations. Without one, a method RateLimit uses its reflected method signature; RateLimitShared uses the type name for TYPE and the reflected method signature for METHOD. A method annotation still replaces the class annotation. A nonblank shared name overrides either target form. Names are global within the store namespace, so use the same scope, attempts and duration everywhere a name is shared.

These two controllers deliberately share `name = "login"`. They demonstrate budgeting only and do not authenticate anyone. Keep real credential verification and CSRF protection in actual login handlers.

`src/main/java/example/PasswordLoginBudgetController.java`:

```java
package example;

import java.util.concurrent.TimeUnit;
import me.trae.foundation.spring.ratelimit.annotation.RateLimit;
import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class PasswordLoginBudgetController {

    @RateLimit(name = "login", scope = RateLimitScope.IP_ADDRESS, attempts = 5, duration = 1, unit = TimeUnit.MINUTES)
    @PostMapping("/examples/login/password")
    public String password() {
        return "budget demonstration";
    }
}
```

`src/main/java/example/ChallengeLoginBudgetController.java`:

```java
package example;

import java.util.concurrent.TimeUnit;
import me.trae.foundation.spring.ratelimit.annotation.RateLimit;
import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class ChallengeLoginBudgetController {

    @RateLimit(name = "login", scope = RateLimitScope.IP_ADDRESS, attempts = 5, duration = 1, unit = TimeUnit.MINUTES)
    @PostMapping("/examples/login/challenge")
    public String challenge() {
        return "budget demonstration";
    }
}
```

After five allowed requests from one resolved IP across either endpoint, the next request to either is rejected with 429. Echo the CSRF cookie/header and matching source hostname on these POST requests; a request rejected by CSRF never consumes this MVC budget. Different names give independent budgets for the same identity. With the default store, sharing is per application instance, not cluster-wide.

## Startup budget validation

Context refresh builds the registry from handler mappings. Both annotations reject attempts below 1, including zero and negative values, and durations converting to fewer than 1 millisecond. Zero duration fails in every TimeUnit; a positive nanosecond or microsecond duration can also round down to zero. Registration throws IllegalStateException before startup completes, naming the reflected method, with a message shaped like `Rate limit on <method> declares 0 attempts, expected at least 1` or `Rate limit on <method> declares a duration of 0 milliseconds, expected at least 1`. Correct the annotation instead of diagnosing the result as runtime throttling.

## Scope and account resolution

`RateLimitScope.IP_ADDRESS` keys the bucket by the resolved client IP from [Spring-Common's `IpAddressResolver`](../Spring-Common/README.md#ip-address-resolution).

`RateLimitScope.ACCOUNT` keys it by the UUID from `AccountIdentity.getId()`, falling back to IP when the account provider returns empty.

Register your existing `AccountManager` as a Spring bean implementing `SecurityAccountProvider<Account>`. Its `getAccountByRequest(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse)` returns `Optional<? extends Account>` in the interface; an implementation can return `Optional<Account>`. Your account implements `AccountIdentity<AccountRole>` and its role enum implements `AccountRoleDefinition`. The provider is shared with Pages. Ratelimit passes the actual response so a JWT refresh can write new cookies while resolving the account. Account data lookup by UUID remains in Common; authenticated request lookup belongs to Security.

The default `EmptySecurityAccountProvider` returns empty, so `ACCOUNT` falls back to `IP_ADDRESS` until your application supplies authenticated UUIDs.

See the [complete Security provider implementation and bean example](../Spring-Security/README.md#creating-a-security-account-provider), plus the [Common lookup provider examples](../Spring-Common/README.md#creating-a-common-account-provider). Register the Security provider once; both Ratelimit and Pages consume it.

### Trusted proxies

IP resolution uses the Common configuration. Fill `trusted-proxy-list` with your CDN's actual peer ranges and lock the origin firewall to match. The ranges below are documentation examples only:

```yaml
foundation:
  ip-address:
    proxy-header-list:
      - CF-Connecting-IP
    trusted-proxy-list:
      - 192.0.2.0/24
      - 2001:db8:1234::/48
```

| Property | Default | Effect |
| --- | --- | --- |
| `foundation.ip-address.trusted-proxy-list` | Empty | Empty trusts forwarded headers from any peer for compatibility. Otherwise only matching IPv4/IPv6 literals or CIDRs may supply them |
| `foundation.ip-address.proxy-header-list` | `CF-Connecting-IP` | Ordered production client header list for trusted peers |
| `foundation.ip-address.header-list` | `X-Forwarded-For`, `X-Real-IP`, `X-Client-IP`, `Forwarded` | Ordered development client header list for trusted peers |

An untrusted peer's forged headers cannot select its budget: the resolver uses its acceptable remote address instead. An untrusted private peer does not resolve in production. A trusted peer with no acceptable forwarded header yields no IP identifier, so IP-scoped endpoints and unauthenticated account-scoped endpoints answer 429. See [Common's resolution rules](../Spring-Common/README.md#ip-address-resolution).

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

The bucket key never appears in the response. It is passed to the store, not exposed in the rejection response.

A client already over its limit does not increment the counter further, so hammering a limited endpoint cannot extend its own lockout.

## Storage and multi-instance deployments

### Default: in-memory fixed window

`MemoryRateLimitStore` keeps at most **250,000 windows** per instance. Existing keys continue to consume their normal allowance at capacity; new keys are refused with a positive delay, which the interceptor converts to 429. New-key admission and sweeping are coordinated so concurrent callers cannot exceed the bound. Attempt accounting remains atomic per key.

A daemon sweeps expired entries every 60 seconds and shuts down on context close. At capacity, a new key also requests an on-demand sweep, throttled to at most once per second. If it frees space, the new key is admitted; otherwise it is rejected. Existing expired windows reopen during consumption.

The cap bounds attacker-controlled key growth. Refusing new keys preserves the limits of tracked callers; evicting live windows would let a caller reset its budget. The trade-off is that legitimate new callers may be rejected while the map is full. Capacity refusal reports the configured window duration as the delay, not the earliest expiry of another entry. This store is process-local.

Run more than one instance and each gets its own counters, so the effective limit multiplies by the instance count.

### Custom store for shared state

For a multi-instance deployment, implement the interface against a shared store:

See the [complete application files](INSTALLATION.md#minimal-working-setup).

Returns a nonpositive value when the request is allowed, or the milliseconds remaining in the window when it is limited.

It is a single atomic call rather than a read followed by a write on purpose. Anything else cannot be honored across instances, since two requests landing at once would both read the same count and both be allowed. A shared implementation needs an atomic operation, such as a server-side script, for expiry, allowance and consumption together. No Redis implementation ships here.

Declare it as a bean and it replaces the default:

See the [complete application files](INSTALLATION.md#minimal-working-setup).

### Why fixed windows

Fixed windows are deliberate over sliding windows. A sliding request history can smooth boundary bursts at the cost of additional history and storage work. Other algorithms have different trade-offs; this module implements fixed windows only. The fixed window is simple, predictable, and cheap.

## What it does not cover

The limit is enforced by a `HandlerInterceptor`, which runs after the entire filter chain and only for requests that reach a mapped handler method. Requests rejected earlier in the chain and requests to paths with no handler are never counted. Choose limits for handler work and account for earlier filter and infrastructure costs separately.

## Auto-configuration

`RateLimitAutoConfiguration` is conditional on a servlet web application and runs after `SecurityAutoConfiguration`. It consumes the SecurityAccountProvider registered by Security and registers:

- `RateLimitStore` (default: `MemoryRateLimitStore`, replaceable with `@ConditionalOnMissingBean`)
- `RateLimitRegistry`
- A `ContextRefreshedEvent` listener that scans `@RateLimit` and `@RateLimitShared` annotations from the handler mappings
- A `WebMvcConfigurer` adding `RateLimitInterceptor`

There is no `@Configuration` class, `@ComponentScan` entry, or `@Enable` annotation to add. Anything unannotated is not limited.

Adding the JAR to a non-web or reactive application registers nothing.

## Security model

Read the [security model](SECURITY.md) for the controls, design trade-offs, configuration risks and application responsibilities. Vulnerability reporting guidance is included there.

## Installation and build

```xml
<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Ratelimit</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Spring-Security and Spring-Common come in transitively. From the repository root with JDK 25 and Maven:

```shell
mvn -pl Spring/Spring-Ratelimit -am install
```

Return to the [Spring guide](../README.md) or [Foundation](../../README.md).