<h1 align="center">Spring-Common</h1>

Shared foundation for the `me.trae.foundation.spring` modules. Holds the pieces that more than one Spring module needs: the global production flag, client IP resolution, request path canonicalisation, and the filter order constants that keep the chain declared in one place.

Every other Spring module depends on this one. It depends on nothing but Spring itself.

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet%20applications-0769AD?logo=jakartaee&logoColor=white"></a>
</p>



<details>
<summary>On this page</summary>

- [Contents](#contents)
- [Production flag](#production-flag)
- [IP address resolution](#ip-address-resolution)
- [Path canonicalisation](#path-canonicalisation)
- [Filter order](#filter-order)
- [How to Wire into a Spring Boot Application](#how-to-wire-into-a-spring-boot-application)
</details>



## Contents

| Type | Purpose |
| --- | --- |
| `FoundationProperties` | The single `foundation.production` flag that every module reads |
| `IpAddressProperties` | Which request headers carry the client IP, per environment |
| `IpAddressResolver` | Resolves the real client IP from a request, or empty when it cannot be trusted |
| `UtilRequestPath` | Canonicalises request paths so every filter and interceptor compares the same string |
| `FilterOrderConstants` | The filter chain order, shared across modules |

## Production flag

```yaml
foundation:
  production: true
```

One flag, read by every module. It changes behaviour in three places:

* `IpAddressResolver` switches to the proxy header list and rejects private addresses.
* `Strict-Transport-Security` is sent (Spring-Security).
* The CSRF cookie gains the `__Host-` prefix, `Secure`, and `SameSite=Strict` (Spring-Security).

Leaving it unset means development behaviour, and nothing warns you. Set it explicitly in your production profile.

## IP address resolution

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

That split is deliberate. `X-Forwarded-For` is client settable and most reverse proxies append to it rather than replacing it, so trusting it in production lets anyone claim any IP they like. `CF-Connecting-IP` is overwritten by the edge on every proxied request, so it cannot be forged, but only while your origin is unreachable except through that proxy. If your origin has a public address someone can reach directly, the guarantee is gone.

`True-Client-IP` is deliberately not in the defaults. It is only set by the edge on some plan tiers with the feature switched on, and passes through untouched otherwise. Add it yourself only if you know your deployment sets it.

Usage:

```java
@AllArgsConstructor
@RestController
@RequestMapping("/api/order")
public final class OrderController {

    private final IpAddressResolver ipAddressResolver;

    @PostMapping("/create")
    public ResponseEntity<?> create(final HttpServletRequest httpServletRequest) {
        final String address = this.ipAddressResolver.getIpAddressByRequest(httpServletRequest).orElse(null);

        if (address == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok().build();
    }
}
```

The return is `Optional` rather than a fallback to `getRemoteAddr()` on purpose. Behind a reverse tunnel every request carries the same remote address, so a fallback would quietly collapse every client into one bucket for anything keyed by IP. An empty result means the request did not arrive through the expected proxy, which is something you want to know rather than paper over.

What counts as unusable in production: any local, loopback, site local, link local, multicast or IPv6 unique local address. IPv4 mapped IPv6 addresses such as `::ffff:127.0.0.1` are unwrapped before testing, since the embedded address would otherwise slip past every check. Carrier grade NAT (`100.64.0.0/10`) is accepted, because that is a real client behind a carrier.

In development every parseable address is accepted, including private ones, so local testing works.

## Path canonicalisation

```java
final String path = UtilRequestPath.getCanonicalPath(httpServletRequest);
```

Collapses runs of slashes, strips the trailing slash, guarantees a leading slash. Case is left alone, since paths are case sensitive and folding case would create duplicate URLs rather than remove them.

`matchesPrefix` is segment aware, so `/apifoo` does not match the prefix `/api`:

```java
if (UtilRequestPath.matchesPrefix(path, "/api")) {
    return;
}
```

This is only a security property if every component compares canonical to canonical. One filter reading `getRequestURI()` directly reopens the mismatch it closes.

It does not redirect. Serving `/orders/` and `/orders` as one URL is a separate job for a redirect filter.

## Filter order

```java
public static final int SECURITY_HEADERS = 0;
public static final int ROBOTS_TAG = 1;
public static final int CSRF = 2;
public static final int PAGE_CACHE_CONTROL = 3;
public static final int PAGE_TRAILING_SLASH = 4;
public static final int STATIC_RESOURCE = 5;
```

Headers run first so even a rejected response carries them. CSRF runs before anything page aware so a forged write dies before the rest of the chain touches it.

## How to Wire into a Spring Boot Application

Add the dependency:

```xml
<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Common</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

That is the whole wiring step. `CommonAutoConfiguration` registers `FoundationProperties`, `IpAddressProperties` and `IpAddressResolver` on the classpath alone, so there is no `@Configuration` class, no `@ComponentScan` entry and no `@Enable` annotation to add in the consuming application.

Then set the flag in your production profile:

```yaml
foundation:
  production: true
```

To replace the resolver with your own, declare a bean of the same type. `@ConditionalOnMissingBean` means yours wins:

```java
@Bean
public IpAddressResolver ipAddressResolver(final FoundationProperties foundationProperties, final IpAddressProperties ipAddressProperties) {
    return new MyIpAddressResolver(foundationProperties, ipAddressProperties);
}
```

The auto configuration is conditional on a servlet web application, so adding the jar to a non web or reactive application registers nothing rather than failing at startup.
