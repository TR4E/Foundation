<h1 align="center">Spring-Security</h1>

The request hardening layer every Spring Boot site needs and nobody wants to write twice: security response headers with a per directive Content Security Policy, and double submit CSRF protection that fails closed.

Depends on Spring-Common only.

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet%20applications-0769AD?logo=jakartaee&logoColor=white"></a>
</p>



<details>
<summary>On this page</summary>

- [Contents](#contents)
- [Security headers](#security-headers)
- [Content Security Policy](#content-security-policy)
- [CSRF](#csrf)
- [Excluding endpoints](#excluding-endpoints)
- [Filter order](#filter-order)
- [How to Wire into a Spring Boot Application](#how-to-wire-into-a-spring-boot-application)
</details>



## Contents

| Type | Purpose |
| --- | --- |
| `SecurityHeadersProperties` | Every header value and CSP directive, per deployment |
| `ContentSecurityPolicyBuilder` | Assembles the policy string once at startup |
| `SecurityHeadersFilter` | Writes the headers on every dispatch, errors included |
| `CsrfProperties` | Cookie name, header name, lifetime, same origin toggle |
| `CsrfTokenService` | Issues and reads the token cookie |
| `CsrfFilter` | Rejects forged state changing requests |
| `@CsrfExclude` | Opts a controller or method out of CSRF protection |
| `CsrfRegistry` | Built once at startup from the handler mappings |

## Security headers

Sent on every response by default:

| Header | Default |
| --- | --- |
| `X-Content-Type-Options` | `nosniff` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` |
| `X-Frame-Options` | `DENY` |
| `Content-Security-Policy` | built from the directive lists below |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload`, production only |

HSTS is gated on `foundation.production` deliberately. Sending it from a development machine pins that hostname to HTTPS in the browser's cache, which is tedious to undo.

Set any value to blank to drop that header.

The filter runs on the error dispatch as well as the normal one, so a `403`, a `404` or a `500` carries the same headers as a successful response.

## Content Security Policy

Per directive, so adding one source does not mean restating the whole policy:

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

The defaults are locked down: everything is `'self'`, and `object-src`, `frame-ancestors`, `frame-src` and `child-src` are `'none'`. Directives available: `default-src`, `base-uri`, `object-src`, `frame-ancestors`, `form-action`, `script-src`, `style-src`, `font-src`, `img-src`, `connect-src`, `frame-src`, `child-src`.

An empty list drops that directive entirely. To turn the policy off:

```yaml
foundation:
  security:
    headers:
      content-security-policy-enabled: false
```

The policy string is assembled once at construction, not per request.

## CSRF

Double submit: a random token in a cookie, echoed back in a header, compared in constant time. Protection applies to `POST`, `PUT`, `PATCH` and `DELETE` on every path, and is never applied to `GET`, `HEAD` or `OPTIONS`, which must be safe by definition. A `GET` that changes state is a bug this cannot fix.

The cookie is issued automatically on any safe request that does not already carry one, so no controller advice or model attribute is needed.

In production the cookie is `__Host-XSRF-TOKEN` with `Secure`, `Path=/`, no `Domain` and `SameSite=Strict`. The `__Host-` prefix is what stops a compromised subdomain overwriting it, which is the attack plain double submit is otherwise open to. In development it is `XSRF-TOKEN` with `SameSite=Lax` and no `Secure`, so local testing works over plain HTTP.

The cookie is deliberately not `HttpOnly`. The pattern requires the page to read it and echo it:

```javascript
const token = document.cookie.split("; ").find(entry => entry.startsWith("XSRF-TOKEN="))?.split("=")[1];

await fetch("/api/order/create", {
    method: "POST",
    headers: {"Content-Type": "application/json", "X-XSRF-TOKEN": token},
    body: JSON.stringify({customerId: "alice", total: 49.95})
});
```

In production the cookie name on the client side is `__Host-XSRF-TOKEN`. Read it from `CsrfTokenService.getCookieName()` and put it in the model rather than hardcoding either form.

Configuration:

```yaml
foundation:
  security:
    csrf:
      cookie-name: XSRF-TOKEN
      header-name: X-XSRF-TOKEN
      cookie-duration: 7d
      require-same-origin: true
```

`require-same-origin` additionally checks `Origin`, falling back to `Referer`, against the request host. A request carrying neither is rejected.

A rejected request gets `403` and never reaches the controller.

## Excluding endpoints

Protection is a blacklist, not a whitelist. Everything state changing is protected unless it opts out, so an endpoint you forget about is merely protected when it maybe did not need to be, rather than silently open.

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

What legitimately belongs here: webhook receivers, which cannot carry your cookie and are authenticated by signature instead, and APIs authenticated purely by an `Authorization` header, which are not CSRF vulnerable because a browser will not attach that header on a cross site request. Nothing else.

`@CsrfExclude` is read at startup and turned into path patterns, so the exclusion is applied by the filter before the request reaches the handler. An interceptor could not do this: at filter time no handler has been matched yet, and moving CSRF to an interceptor would leave forwards, error dispatches and every endpoint outside the handler mapping uncovered, which is exactly where forgotten endpoints live.

Paths are matched canonically, with and without a trailing slash.

## Filter order

Both filters are registered through a `FilterRegistrationBean` rather than as bare beans, so the order and the dispatch types are explicit:

| Filter | Order | Dispatch types |
| --- | --- | --- |
| `SecurityHeadersFilter` | 0 | `REQUEST`, `ERROR`, `ASYNC` |
| `CsrfFilter` | 2 | `REQUEST` |

Headers get the error dispatch so nothing escapes without them. CSRF is deliberately limited to the initial request: an internal forward to an error page carries no header of its own and would otherwise be rechecked and rejected.

Order values come from `FilterOrderConstants` in Spring-Common, so the chain stays declared in one place across modules.

## How to Wire into a Spring Boot Application

Add the dependency:

```xml
<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Security</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

That is the whole wiring step. `SecurityAutoConfiguration` registers both filters, the token service and the CSRF registry on the classpath alone, so there is no `@Configuration` class, no `@ComponentScan` entry and no `@Enable` annotation to add in the consuming application. Spring-Common comes in transitively.

Then set the production flag, which drives HSTS and the cookie attributes:

```yaml
foundation:
  production: true
```

Then do these three, in this order, because each one is a way the defaults will be wrong for your application:

1. Add your CDN, font and API origins to the CSP directive lists. The default policy is `'self'` only and will block anything external.
2. Put `@CsrfExclude` on webhook receivers and on any controller authenticated purely by an `Authorization` header.
3. Have your pages read the cookie name from `CsrfTokenService.getCookieName()` rather than hardcoding it, since it changes between development and production.

To replace the token service with your own, declare a bean of the same type. `@ConditionalOnMissingBean` means yours wins.

The CSRF registry is populated on `ContextRefreshedEvent` rather than during bean wiring, so it cannot force the handler mapping to initialise early and end up with a half built exclusion list.

The auto configuration is conditional on a servlet web application, so adding the jar to a non web or reactive application registers nothing rather than failing at startup.
