<h1 align="center">Spring-Ratelimit</h1>

<p align="center">Annotation-driven rate limiting for Spring Boot controllers with per-IP and per-account scoping.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet-0769AD?logo=jakartaee&logoColor=white"></a>
</p>

Install Spring-Ratelimit into a servlet Spring Boot application using the steps below. The [README](README.md) describes the module and [SECURITY.md](SECURITY.md) explains its security boundaries.

## Contents

<details>
<summary>On this page</summary>

- [Requirements](#requirements)
- [Dependency](#dependency)
- [With no configuration](#with-no-configuration)
- [Application contracts](#application-contracts)
- [Deliberately shared named budgets](#deliberately-shared-named-budgets)
- [Startup budget validation](#startup-budget-validation)
- [Storage and environment](#storage-and-environment)
- [Settings](#settings)
- [Extension points](#extension-points)
- [Minimal working setup](#minimal-working-setup)
- [Verification](#verification)
- [Troubleshooting](#troubleshooting)

</details>

## Requirements

| Requirement | Use | Failure if absent |
| --- | --- | --- |
| JDK 25 | Compile and run with Java 25; check `mvn -version` | Older compilers reject target 25; older runtimes reject class files |
| Spring Boot servlet MVC application | This repository builds against the Boot 4.1.1 dependency BOM | Non-web and reactive contexts do not activate these auto-configurations; other Boot lines are not verified here |
| Servlet runtime | Use `spring-boot-starter-webmvc` with embedded server, or a compatible servlet container | These libraries do not bring an embedded server or view engine themselves |
| Maven and access to the artifacts | Install the reactor locally or publish it to your organization's repository | Coordinates cannot resolve otherwise |

Foundation's root build uses Lombok annotation processing. If you copy Lombok-based examples, enable the same processor in your application. No preview flag is required for these examples.
## Dependency

From the Foundation repository root, install the library and its reactor dependencies:

```shell
mvn -pl Spring/Spring-Ratelimit -am install
```

In your application's Maven project, use Java 25 and the Boot 4.1.1 parent or equivalent imported BOM. Add normal compile dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Ratelimit</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Keep the Foundation dependency on the runtime classpath. Its provided `jakarta.servlet-api` dependency is supplied by your application server. Lombok is provided and not a transitive runtime dependency; the optional configuration processor is not required at runtime. Include Lombok as a provided dependency and annotation processor when copying these examples. Spring-Security, Common, Utilities and their JWT libraries arrive transitively. Jakarta annotation support is also included. No Redis client or distributed store is supplied.
### Complete application pom

Use this as the starting `pom.xml` for the examples instead of repeating the dependency fragments above. Supply your own group and artifact names when integrating into an existing application.

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>
    <groupId>example</groupId>
    <artifactId>orders-web</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <java.version>25</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>me.trae.foundation.spring</groupId>
            <artifactId>Spring-Ratelimit</artifactId>
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
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
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
</project>
```

## With no configuration

RateLimitAutoConfiguration runs after Security in servlet applications. No module enable annotation, added component scan or manual import is needed. Your normal Boot bootstrap and controller bean registration remain necessary. JWT is the generic application-owned bean exception when using JWT authentication.

| Registration | Default behavior |
| --- | --- |
| RateLimitStore | Conditional MemoryRateLimitStore, per-process fixed windows |
| RateLimitRegistry | Mapping from reflected methods to limit declarations |
| Context-refresh registrar | Scan RateLimit and inherited RateLimitShared from MVC mappings; replace registry on refresh |
| WebMvcConfigurer | Register RateLimitInterceptor before mapped handler execution |

No Ratelimit filter registers. Common supplies the IP resolver; Security supplies the empty or application SecurityAccountProvider plus headers at order 0 and CSRF at order 2. Ratelimit does not itself register the Security provider. ACCOUNT falls back to IP with the empty provider. Unannotated handlers are not limited. Headers and CSRF still affect them through Security.

Interceptors run inside MVC after filters reach the handler. Relative ordering against Pages' interceptor is not explicitly declared. A public endpoint pays account lookup cost only when its configured scope asks for it.

## Application contracts

| Contract | Required behavior |
| --- | --- |
| Annotated MVC handlers | Choose attempts, duration, TimeUnit and scope; attempts below 1 or duration below 1 millisecond after conversion fail registration |
| Optional SecurityAccountProvider | Verified account UUID via getAccountByRequest(request, response); implement its session methods if using JWT |
| RateLimitStore.tryConsume(final String key, final RateLimitData rateLimitData) | Atomically allow and consume one attempt, returning nonpositive; or reject and return positive remaining milliseconds |

For ACCOUNT scope, all sessions of the same account share one budget. Different account UUIDs have separate budgets. Anonymous requests fall back to IP; submitted usernames do not create target-account budgets. Pass the real response to JWT-backed resolution. Complete the [Security application contracts](../Spring-Security/INSTALLATION.md#application-contracts) if using accounts. IP-only limits need no account implementation.

The full key is reflected method signature or controller class name, then `:account:<UUID>` or `:ip:<address>`. A nonblank annotation name replaces the reflected key. A named shared METHOD declaration therefore shares that name across its methods; use consistent scope, attempts and duration for every declaration sharing a name. Treat the supplied full key as opaque in a replacement store. With unnamed METHOD sharing, settings repeat but budgets remain separate; TYPE shares across that controller's eligible methods. A method RateLimit replaces the class rule for that method and stops it counting toward the shared budget.

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

## Storage and environment

The default fixed window starts at first use. It admits the configured attempts until expiry, without extending the window on refusal. Concurrent same-key consumption is atomic. MemoryRateLimitStore caps windows at 250,000 per instance, sweeps every 60 seconds and requests a capacity sweep at most once per second. Existing tracked keys keep accounting at capacity; new keys are rejected if no space is freed. These are implementation constants, not properties. Restart loses counters; multiple instances multiply independent allowances.

| Shared store requirement | What breaks otherwise |
| --- | --- |
| Atomic expiry check, allowance check and consume | Concurrent callers over-admit |
| Same full key and semantics on every instance | One account gets independent cluster budgets |
| Never expire or evict live budgets early | Attackers regain attempts by cycling keys |
| Positive remaining milliseconds on rejection | Interceptor may treat refusal as allowance |
| Explicit outage policy and bounded cleanup | Backend failure can bypass or deny protection |
| Consistent configuration and time behavior | Instances disagree about limits and expiry |

Supply a RateLimitStore bean backed by your chosen shared infrastructure. The module ships no distributed implementation or configurable failure policy. A server-side atomic script or equivalent transactional primitive is appropriate; independent network read/check/write is not. Fixed windows allow a burst across their boundary and IP limits do not stop a distributed attacker.

Use Common's trusted proxy CIDRs and match the origin firewall. Empty lists trust client headers from anyone. A peer outside a configured list uses its socket address, which can collapse all clients to one proxy budget if that peer is actually an omitted trusted proxy. An unacceptable private peer in production instead resolves empty. A trusted peer missing acceptable headers also resolves empty with no fallback. No identifier means 429, not unlimited access.

## Settings

Ratelimit has no ConfigurationProperties class of its own. Its annotations are the settings for budgets; all attributes except name are required.

| Annotation member | Type | Default | Effect | Change when |
| --- | --- | --- | --- | --- |
| name | String | Empty | Nonblank value replaces the reflected method/class key | Deliberately share budgets with consistent settings |
| scope | RateLimitScope | Required | IP_ADDRESS or ACCOUNT with IP fallback | Choose who shares a budget |
| attempts | int | Required | Allowed attempts per window | Tune endpoint cost and legitimate usage |
| duration | int | Required | Window size in unit | Tune exposure and recovery |
| unit | TimeUnit | Required | Converted to milliseconds | Choose readable duration units |
| target, RateLimitShared only | RateLimitTarget | Required | TYPE or METHOD budget key | Share one controller budget or repeat settings |

### `foundation`

| Property | Type | Default | Effect | Change when |
| --- | --- | --- | --- | --- |
| `production` | `boolean` | `false` | Switch cookie Secure/Strict/host prefix, HSTS, IP header/validation policy and missing JWT seed behavior | Explicitly set true for production HTTPS; review HSTS before development use |

### `foundation.ip-address`

| Property | Type | Default | Effect | Change when |
| --- | --- | --- | --- | --- |
| `proxy-header-list` | `List<String>` | `["CF-Connecting-IP"]` | Ordered production client header literals | Your trusted proxy overwrites a different header |
| `header-list` | `List<String>` | `["X-Forwarded-For", "X-Real-IP", "X-Client-IP", "Forwarded"]` | Ordered development client header literals | Local proxy uses another header; structured Forwarded syntax is not parsed |
| `trusted-proxy-list` | `List<String>` | `[]` | Peer IP/CIDR allowlist; EMPTY TRUSTS ANY PEER | Always configure for a reachable production origin and match the firewall |

Security is transitive. See [Security settings](../Spring-Security/INSTALLATION.md#settings) for every header, CSP, CSRF and JWT property, including their defaults and change conditions. The production flag selects Common's header list and address validation and also changes Security's cookies, HSTS and missing-seed policy.

The following inherited Security settings also apply when this module is present. JWT settings take effect only when the application constructs its generic JwtSecurity bean.

### `foundation.security.jwt`

| Property | Type | Default | Effect | Change when |
| --- | --- | --- | --- | --- |
| `issuer` | `String` | `"foundation"` | Issued and required iss | Separate token domains |
| `audience` | `String` | `"foundation"` | Issued and required aud | Separate recipients |
| `access-token-cookie-name` | `String` | `"ACCESS-TOKEN"` | Access cookie base name | Avoid naming collisions |
| `refresh-token-cookie-name` | `String` | `"REFRESH-TOKEN"` | Refresh cookie base name | Avoid naming collisions |
| `fingerprint-cookie-name` | `String` | `"FINGERPRINT"` | Fingerprint cookie base name | Avoid naming collisions |
| `access-token-key-seed` | `String` | `Unset` | Base64 of exactly 32 bytes; missing production seed throws | Always provide a stable distinct production secret |
| `refresh-token-key-seed` | `String` | `Unset` | Refresh Ed25519 seed; same rules | Always provide a different stable secret |
| `access-token-duration` | `Duration` | `15m` | Access JWT and cookie lifetime | Tune access exposure and refresh traffic |
| `refresh-token-duration` | `Duration` | `7d` | Session expiry and refresh/fingerprint lifetimes | Tune device persistence |
| `clock-skew` | `Duration` | `5s` | Parser time allowance, whole seconds | Accommodate measured clock differences |
| `rotation-grace` | `Duration` | `5s` | Allow exactly the previous session hash briefly | Accommodate concurrent browser requests |
| `max-sessions` | `int` | `10` | Prune expired and evict least recently rotated sessions on login; uses values below 1 for unlimited sessions | Choose device cap; 1 restores single-device behavior |

### `foundation.security.csrf`

| Property | Type | Default | Effect | Change when |
| --- | --- | --- | --- | --- |
| `require-same-origin` | `boolean` | `true` | Compare source hostname before token check | Only with a reviewed alternate deployment policy |
| `cookie-name` | `String` | `"XSRF-TOKEN"` | CSRF base name; production adds host prefix | Align clients or avoid collisions |
| `header-name` | `String` | `"X-XSRF-TOKEN"` | Echo header name | Align clients |
| `cookie-duration` | `Duration` | `7d` | CSRF cookie max-age | Tune token persistence |

### `foundation.security.headers`

| Property | Type | Default | Effect | Change when |
| --- | --- | --- | --- | --- |
| `content-type-options` | `String` | `"nosniff"` | MIME sniffing policy | Review content handling; blank omits header |
| `referrer-policy` | `String` | `"strict-origin-when-cross-origin"` | Referrer leakage policy | Review navigation requirements |
| `permissions-policy` | `String` | `"camera=(), microphone=(), geolocation=()"` | Disable camera, microphone, geolocation | Grant only required browser features |
| `strict-transport-security` | `String` | `"max-age=31536000` | Production-only HTTPS policy | Review host and subdomain HTTPS readiness |
| `frame-options` | `String` | `"DENY"` | Deny framing | Reviewed embedding use case |
| `content-security-policy-enabled` | `boolean` | `true` | Enable startup-built CSP | Disable only with a reviewed replacement policy |
| `default-src` | `List<String>` | `["'self'"]` | Replace the corresponding CSP directive list; empty omits it | Add only required source origins, retaining required existing entries |
| `base-uri` | `List<String>` | `["'self'"]` | Replace the corresponding CSP directive list; empty omits it | Add only required source origins, retaining required existing entries |
| `object-src` | `List<String>` | `["'none'"]` | Replace the corresponding CSP directive list; empty omits it | Add only required source origins, retaining required existing entries |
| `frame-ancestors` | `List<String>` | `["'none'"]` | Replace the corresponding CSP directive list; empty omits it | Add only required source origins, retaining required existing entries |
| `form-action` | `List<String>` | `["'self'"]` | Replace the corresponding CSP directive list; empty omits it | Add only required source origins, retaining required existing entries |
| `script-src` | `List<String>` | `["'self'"]` | Replace the corresponding CSP directive list; empty omits it | Add only required source origins, retaining required existing entries |
| `style-src` | `List<String>` | `["'self'"]` | Replace the corresponding CSP directive list; empty omits it | Add only required source origins, retaining required existing entries |
| `font-src` | `List<String>` | `["'self'"]` | Replace the corresponding CSP directive list; empty omits it | Add only required source origins, retaining required existing entries |
| `img-src` | `List<String>` | `["'self'", "data:"]` | Replace the corresponding CSP directive list; empty omits it | Add only required source origins, retaining required existing entries |
| `connect-src` | `List<String>` | `["'self'"]` | Replace the corresponding CSP directive list; empty omits it | Add only required source origins, retaining required existing entries |
| `frame-src` | `List<String>` | `["'none'"]` | Replace the corresponding CSP directive list; empty omits it | Add only required source origins, retaining required existing entries |
| `child-src` | `List<String>` | `["'none'"]` | Replace the corresponding CSP directive list; empty omits it | Add only required source origins, retaining required existing entries |

## Extension points

RateLimitStore is the only local ConditionalOnMissingBean default. Registry, listener and MVC configurer are not conditional replacement hooks. Inherited conditional Common and Security beans are documented in their installation guides. The memory store's alternate-cap constructor is package-private, not a public customization setting.

`@RateLimit` applies to methods. `@RateLimitShared` applies to types and is @Inherited for class inheritance, not interfaces. Subclass declarations replace inherited shared settings; method declarations take precedence. A method annotation is read from the actual reflected method resolved by MVC, not merged from every interface/override. Registry construction rejects attempts below 1 and durations that convert to fewer than 1 millisecond; use positive values. This includes sub-millisecond units rounded to zero.

## Minimal working setup

1. Create a Boot MVC application with Java 25 and the dependency above.
2. Create Application.java and InvoiceController.java below under the same scanned package.
3. Add the local configuration below and start with `mvn spring-boot:run`.
4. Request /invoices three times: the third returns 429. This GET example avoids confusing CSRF rejection with rate rejection.
5. To use account identity, complete Security's setup and log in with two separate cookie jars before requesting /account-budget. Both sessions count toward the same account budget. Without that wiring, this endpoint uses IP fallback.
6. For clustered production traffic, replace the store and configure trusted infrastructure before exposing login or expensive handlers.

`src/main/java/example/Application.java`:

```java
package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(proxyBeanMethods = false)
public final class Application {

    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

`src/main/java/example/InvoiceController.java`:

```java
package example;

import java.util.concurrent.TimeUnit;
import me.trae.foundation.spring.ratelimit.annotation.RateLimit;
import me.trae.foundation.spring.ratelimit.annotation.RateLimitShared;
import me.trae.foundation.spring.ratelimit.scope.RateLimitScope;
import me.trae.foundation.spring.ratelimit.scope.RateLimitTarget;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RateLimitShared(target = RateLimitTarget.TYPE, scope = RateLimitScope.IP_ADDRESS, attempts = 30, duration = 1, unit = TimeUnit.MINUTES)
@RestController
public final class InvoiceController {

    @RateLimit(scope = RateLimitScope.IP_ADDRESS, attempts = 2, duration = 1, unit = TimeUnit.MINUTES)
    @GetMapping("/invoices")
    public String invoices() {
        return "invoices";
    }

    @GetMapping("/orders")
    public String orders() {
        return "orders";
    }

    @RateLimit(scope = RateLimitScope.ACCOUNT, attempts = 2, duration = 1, unit = TimeUnit.MINUTES)
    @GetMapping("/account-budget")
    public String accountBudget() {
        return "account budget";
    }
}
```

`src/main/resources/application.yaml` for a direct local demo:

```yaml
foundation:
  production: false
  ip-address:
    trusted-proxy-list: [192.0.2.0/24]
```

This example CIDR does not trust loopback, so local requests use their actual peer address without requiring a forwarded header. It is not a production proxy range. Replace it, the header list and the origin firewall for your real proxy.

## Verification

| Check | Expected |
| --- | --- |
| First two GET /invoices from one identity | 200 |
| Third GET /invoices | 429, UTF-8 JSON, Retry-After rounded up with minimum 1 second, Cache-Control no-store |
| GET /orders | Independent shared-class allowance; /invoices uses its method override |
| Two authenticated sessions for one account hitting /account-budget | Two allowed requests total, then 429 across either session |
| Another account | Separate budget |
| No account and no acceptable IP | 429, minimum one-second retry, not unlimited requests |
| POST without required CSRF cookie/header | 403 from Security before rate-limited handler execution |
| Saturated memory store | Tracked keys keep accounting; new identities refused |

The rejection body is generic and includes `message` and `retryAfter`, not the key. Retry timing is advisory and missing identity may still fail after waiting. Authentication cookie behavior, seed logs, device logout and explicit revoke are covered in [Security verification](../Spring-Security/INSTALLATION.md#verification). Protected Page and asset verification is in [Pages](../Spring-Pages/INSTALLATION.md#verification); rate limiting alone grants no authorization.

```shell
mvn -pl Spring/Spring-Ratelimit -am test
```

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| ACCOUNT behaves per IP | Empty Security provider or authentication failure; wire provider, JWT and current session storage |
| Every request shares one proxy budget | Configured trust list omits the actual acceptable proxy peer, so its socket IP is used; correct peer CIDRs and origin firewall |
| Every IP-scoped request gets 429 immediately | Missing acceptable header from trusted peer, invalid/nonpublic production address, or full store for new keys |
| Unexpected 403 on writes | Inherited CSRF runs earlier; echo the correct cookie value and send matching source hostname |
| Budget increases with instance count | Default counters are per-process; supply a correct shared store |
| Named METHOD rules unexpectedly share a budget | Nonblank name overrides the reflected TYPE/METHOD fallback; clear the name for independent method keys |
| Method seems outside class budget | Method RateLimit replaces, rather than stacks with, shared declaration |
| UnsupportedOperationException during JWT operations | Empty Security provider's session methods reached; implement all six methods |
| Circular provider and JWT bean | Use Security's ObjectProvider cycle break |
| Sessions remain after password change | Explicitly call Rotation.revoke(account); the limiter does not manage sessions |

Return to the [Spring guide](../README.md) or [Foundation](../../README.md).