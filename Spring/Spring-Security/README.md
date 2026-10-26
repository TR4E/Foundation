<h1 align="center">Spring-Security</h1>

<p align="center">Security response headers with per-directive Content Security Policy and double-submit CSRF protection for Spring Boot.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet-0769AD?logo=jakartaee&logoColor=white"></a>
</p>

Spring-Security provides security response headers with a per-directive Content Security Policy and double-submit CSRF protection. Security headers cover request, error, and async dispatches; CSRF validation runs on initial request dispatches for protected methods unless the path is excluded. Depends on [Spring-Common](../Spring-Common/README.md) only.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Security headers](#security-headers)
- [Content Security Policy](#content-security-policy)
- [CSRF protection](#csrf-protection)
- [Excluding endpoints from CSRF](#excluding-endpoints-from-csrf)
- [Filter order and dispatch types](#filter-order-and-dispatch-types)
- [Configuration reference](#configuration-reference)
- [Auto-configuration](#auto-configuration)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Purpose |
| --- | --- |
| `SecurityHeadersProperties` | Every header value and CSP directive, configurable per deployment |
| `ContentSecurityPolicyBuilder` | Assembles the CSP string once at startup from the directive lists |
| `SecurityHeadersFilter` | Writes security headers on every dispatch, including error pages |
| `CsrfProperties` | Cookie name, header name, lifetime, same-origin toggle |
| `CsrfTokenService` | Issues and reads the CSRF token cookie using `SecureRandom` |
| `CsrfFilter` | Rejects forged state-changing requests with constant-time token comparison |
| `@CsrfExclude` | Opts a controller or method out of CSRF protection |
| `CsrfRegistry` | Built once at startup from the handler mappings |

## Security headers

Sent on every response by default:

| Header | Default value | Purpose |
| --- | --- | --- |
| `X-Content-Type-Options` | `nosniff` | Prevents MIME-type sniffing |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Controls referrer leakage |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` | Disables device APIs |
| `X-Frame-Options` | `DENY` | Prevents clickjacking |
| `Content-Security-Policy` | Built from directive lists below | Controls resource loading |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload` | Forces HTTPS (production only) |

HSTS is gated on `foundation.production` deliberately. Sending it from a development machine pins that hostname to HTTPS in the browser's cache, which is tedious to undo.

Set any value to blank to drop that header entirely.

The filter runs on the `ERROR` dispatch as well as the normal one, so a `403`, `404`, or `500` carries the same headers as a successful response.

## Content Security Policy

Configured per directive, so adding one source does not mean restating the whole policy:

```yaml
foundation:
  security:
    headers:
      script-src:
        - "'self'"
        - https://cdn.example.com
      frame-src:
        - https://challenges.example.com
      connect-src:
        - "'self'"
        - https://api.example.com
```

The defaults are locked down:

| Directive | Default |
| --- | --- |
| `default-src` | `'self'` |
| `base-uri` | `'self'` |
| `form-action` | `'self'` |
| `script-src` | `'self'` |
| `style-src` | `'self'` |
| `font-src` | `'self'` |
| `img-src` | `'self'`, `data:` |
| `connect-src` | `'self'` |
| `object-src` | `'none'` |
| `frame-ancestors` | `'none'` |
| `frame-src` | `'none'` |
| `child-src` | `'none'` |

An empty list drops that directive entirely. To disable the entire policy:

```yaml
foundation:
  security:
    headers:
      content-security-policy-enabled: false
```

The policy string is assembled once at construction by `ContentSecurityPolicyBuilder`, not per request.

## CSRF protection

### How it works

Double-submit cookie pattern: a random token in a cookie, echoed back in a header, compared in constant time using `MessageDigest.isEqual`. Protection applies to `POST`, `PUT`, `PATCH`, and `DELETE` on every path, and is never applied to `GET`, `HEAD`, or `OPTIONS`, which must be safe by definition. A `GET` that changes state is a bug this module cannot fix.

The cookie is issued automatically on any safe request that does not already carry one, so no controller advice or model attribute is needed.

### Token generation

`CsrfTokenService` generates 32 random bytes using `SecureRandom` and Base64-URL-encodes them without padding. The token is cryptographically random and unpredictable.

### Cookie behavior by environment

| Attribute | Production | Development |
| --- | --- | --- |
| Cookie name | `__Host-XSRF-TOKEN` | `XSRF-TOKEN` |
| `Secure` | Yes | No |
| `SameSite` | `Strict` | `Lax` |
| `Path` | `/` | `/` |
| `HttpOnly` | No | No |
| `Domain` | None | None |

The `__Host-` prefix in production stops a compromised subdomain from overwriting the cookie, which is the attack plain double-submit is otherwise open to. In development, `Lax` and no `Secure` allows local testing over plain HTTP.

The cookie is deliberately not `HttpOnly`. The pattern requires the page to read it and echo it back:

```javascript
const token = document.cookie
        .split("; ")
        .find(entry => entry.startsWith("XSRF-TOKEN="))
        ?.split("=")[1];

await fetch("/api/order/create", {
    method: "POST",
    headers: {"Content-Type": "application/json", "X-XSRF-TOKEN": token},
    body: JSON.stringify({customerId: "alice", total: 49.95})
});
```

In production the cookie name is `__Host-XSRF-TOKEN`. Read it from `CsrfTokenService.getCookieName()` and put it in the model rather than hardcoding either form.

### Same-origin validation

When `require-same-origin` is `true` (the default), the filter compares the host in `Origin`, falling back to `Referer`, with the request's server name, ignoring case. It does not compare the scheme or port. A request carrying neither header is rejected. Token comparison is still required.

### Rejection behavior

A rejected request gets `403` and never reaches the controller.

## Excluding endpoints from CSRF

Protection is a blocklist, not an allowlist. Everything state-changing is protected unless it opts out, so an endpoint that is forgotten is merely protected when it maybe did not need to be, rather than silently open.

```java
@CsrfExclude
@RestController
@RequestMapping("/api/webhook")
public final class WebhookController {

    @PostMapping("/payment")
    public ResponseEntity<?> payment(@RequestBody final String body, @RequestHeader("X-Signature") final String signature) {
        return ResponseEntity.ok().build();
    }
}
```

Works on a single method too.

What legitimately belongs here: webhook receivers, which cannot carry the application's cookie and are authenticated by signature instead, and APIs authenticated purely by an `Authorization` header, which are not CSRF-vulnerable because a browser will not attach that header on a cross-site request. Nothing else.

`@CsrfExclude` is read at startup and turned into `PathPattern` instances, so the exclusion is applied by the filter before the request reaches a handler. Exclusions match paths, independent of the incoming HTTP method. CSRF validation also applies to initial requests outside the handler mapping; internal forward, error, and async dispatches are not rechecked.

Paths are matched canonically, with and without a trailing slash.

## Filter order and dispatch types

Both filters are registered through `FilterRegistrationBean` with explicit order and dispatch types:

| Filter | Order | Dispatch types | Why |
| --- | --- | --- | --- |
| `SecurityHeadersFilter` | 0 | `REQUEST`, `ERROR`, `ASYNC` | Every response carries headers, even errors |
| `CsrfFilter` | 2 | `REQUEST` | Only the initial request; internal forwards carry no header |

CSRF is deliberately limited to `REQUEST` dispatch. An internal forward to an error page carries no CSRF header of its own and would otherwise be rechecked and rejected.

Order values come from `FilterOrderConstants` in [Spring-Common](../Spring-Common/README.md), so the chain is declared in one place across modules. See the [Spring-Pages guide](../Spring-Pages/README.md#filter-and-interceptor-order) for the filters at positions 1, 3, 4, and 5.

## Configuration reference

### Security headers

```yaml
foundation:
  security:
    headers:
      content-type-options: nosniff
      referrer-policy: strict-origin-when-cross-origin
      permissions-policy: "camera=(), microphone=(), geolocation=()"
      frame-options: DENY
      strict-transport-security: "max-age=31536000; includeSubDomains; preload"
      content-security-policy-enabled: true
      default-src:
        - "'self'"
      script-src:
        - "'self'"
      style-src:
        - "'self'"
      font-src:
        - "'self'"
      img-src:
        - "'self'"
        - "data:"
      connect-src:
        - "'self'"
      base-uri:
        - "'self'"
      form-action:
        - "'self'"
      object-src:
        - "'none'"
      frame-ancestors:
        - "'none'"
      frame-src:
        - "'none'"
      child-src:
        - "'none'"
```

### CSRF

```yaml
foundation:
  security:
    csrf:
      cookie-name: XSRF-TOKEN
      header-name: X-XSRF-TOKEN
      cookie-duration: 7d
      require-same-origin: true
```

| Property | Default | Purpose |
| --- | --- | --- |
| `cookie-name` | `XSRF-TOKEN` | Base cookie name; `__Host-` is prepended in production |
| `header-name` | `X-XSRF-TOKEN` | Header the client sends the token back in |
| `cookie-duration` | `7d` | Cookie `Max-Age` |
| `require-same-origin` | `true` | Additionally validate `Origin`/`Referer` |

## Auto-configuration

`SecurityAutoConfiguration` is conditional on a servlet web application and runs after `CommonAutoConfiguration`. It registers:

- `CsrfTokenService` (replaceable with `@ConditionalOnMissingBean`)
- `CsrfRegistry`
- A `ContextRefreshedEvent` listener that scans `@CsrfExclude` annotations from the handler mappings
- Two `FilterRegistrationBean` instances for the security headers and CSRF filters

There is no `@Configuration` class, `@ComponentScan` entry, or `@Enable` annotation to add.

Adding the JAR to a non-web or reactive application registers nothing.

**Setup checklist:**

1. Set `foundation.production: true` in your production profile. This enables HSTS and the `__Host-` CSRF cookie prefix.
2. Add your CDN, font, and API origins to the CSP directive lists. The default policy is `'self'` only and blocks anything external.
3. Put `@CsrfExclude` on webhook receivers and controllers authenticated purely by an `Authorization` header.
4. Have your pages read the cookie name from `CsrfTokenService.getCookieName()` rather than hardcoding it, since it changes between environments.

## Installation and build

```xml
<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Security</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Spring-Common comes in transitively. From the repository root with JDK 25 and Maven:

```shell
mvn -pl Spring/Spring-Security -am install
```

Return to the [Spring guide](../README.md) or [Foundation](../../README.md).
