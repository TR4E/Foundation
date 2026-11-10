<h1 align="center">Spring-Pages</h1>

<p align="center">Page-as-a-class framework with role-based access, asset gating, cache control, navigation, and pagination for Spring Boot.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet-0769AD?logo=jakartaee&logoColor=white"></a>
</p>

Install Spring-Pages into a servlet Spring Boot application using the steps below. The [README](README.md) describes the module and [SECURITY.md](SECURITY.md) explains its security boundaries.

## Contents

<details>
<summary>On this page</summary>

- [Requirements](#requirements)
- [Dependency](#dependency)
- [With no configuration](#with-no-configuration)
- [Application contracts](#application-contracts)
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
mvn -pl Spring/Spring-Pages -am install
```

In your application's Maven project, use Java 25 and the Boot 4.1.1 parent or equivalent imported BOM. Add normal compile dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Pages</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Keep the Foundation dependency on the runtime classpath. Its provided `jakarta.servlet-api` dependency is supplied by your application server. Lombok is provided and not a transitive runtime dependency; the optional configuration processor is not required at runtime. Include Lombok as a provided dependency and annotation processor when copying these examples. Spring-Security, Spring-Common, Utilities and their JWT dependencies arrive transitively. Pages has no template engine dependency: use @ResponseBody as below or add an application view engine such as Thymeleaf separately.
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
            <artifactId>Spring-Pages</artifactId>
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

`PagesAutoConfiguration` runs after Security, only in servlet applications. No module enable annotation, added component scan or manual import is required. It discovers your Page and AssetProvider beans through your application's normal bean registration. It does not create any pages or login flow for you. The generic JwtSecurity bean remains the application's explicit declaration if using JWT authentication.

| Registration | No-configuration effect |
| --- | --- |
| PageProperties | Bind foundation.pages defaults |
| PageAccessResolver | Conditional AccountPageAccessResolver uses Security's provider; empty provider means anonymous |
| PageRegistry, NavbarService, PaginationService | Empty page registry until Page beans are registered; configured pagination defaults |
| Context-refresh registrar | Build canonical route/asset indexes, validate and register GET render handlers once |
| WebMvcConfigurer | Register PageAccessInterceptor for mapped Page beans |
| RobotsTagFilter | Order 1, REQUEST/ERROR; blank tag means no header |
| PageCacheControlFilter | Order 3, REQUEST/ERROR; private revalidation fallback |
| PageTrailingSlashFilter | Order 4, REQUEST; noncanonical URI gives 308 |
| StaticResourceFilter | Order 5, REQUEST; role gate for declared protected assets |

Inherited Security filters run at 0 (headers, REQUEST/ERROR/ASYNC) and 2 (CSRF, REQUEST). PageAccessInterceptor runs within MVC after the filters reach the handler. No explicit cross-module interceptor ordering is declared relative to Ratelimit; do not rely on one running first. With no Page beans, ordinary controllers are not role-protected by Pages.

## Application contracts

| Extension | Methods / declaration | Contract |
| --- | --- | --- |
| Page subclass | `super(route, requiredRole)` and exactly one `@Render` String-returning method | Register a bean; null role means public; handler arguments follow MVC; @ResponseBody selects a text body, otherwise String is a view name |
| Page asset/cache overrides | `getAssetPaths()`, `getRouteCacheControlData()`, `getAssetCacheControlData()` | Declare every protected resource URL and preserve private caching |
| AssetProvider | `Collection<PageAsset> getAssets()` | Unique canonical declarations across all owners; explicit roles for shared protected assets |
| Navbar | `int getNavbarPosition()`, `String getNavbarName()`; optional `List<String> getNavbarDescription()` | Only implementing pages appear; sort by position; description defaults empty |
| PageAccessResolver replacement | `boolean isAuthenticated(final HttpServletRequest request, final HttpServletResponse response)`, `boolean hasRole(final HttpServletRequest request, final HttpServletResponse response, final AccountRoleDefinition role)` | Verified authentication and role enforcement; pass real response for cookie refresh; manage request lookup costs |

The default resolver uses SecurityAccountProvider.getAccountByRequest with request and response, caching account or absence per request. For working protected pages, complete the [Security provider and JWT setup](../Spring-Security/INSTALLATION.md#minimal-working-setup) once. Common account roles must be declared from lowest privilege upward unless hasRole is overridden. Do not compare unrelated role enums.

`PageAsset` supports `(path)` for public SHARED_SHORT, or `(path, requiredRole, cacheControlData)`. Page-owned declarations inherit their page role; gated assets force private no-store regardless of the declared policy. Exact protected matches win, otherwise longest protected `/**` prefix, case-insensitively at segment boundaries. Page routes themselves remain case-sensitive. Declare alternate URLs too; the filter does not inspect HTML or protect files served outside this servlet application.

## Storage and environment

Pages stores immutable registry snapshots in memory, not account data. Authentication/session storage is the Security provider's responsibility. Use a persistent provider for protected pages; the empty provider redirects page requests and hides protected assets. A cached resolution can remain valid within its current request even if the session is revoked elsewhere.

| CacheControlData preset | Header |
| --- | --- |
| NO_STORE | private, no-store, no-cache, must-revalidate |
| IMMUTABLE | public, max-age=31536000, immutable |
| SHARED_SHORT | public, max-age=300, must-revalidate |
| PRIVATE_REVALIDATE | private, no-cache, must-revalidate |
| PRIVATE_NO_STORE | private, no-store |

Page routes default to PRIVATE_REVALIDATE. Public owned assets default to SHARED_SHORT, protected assets to PRIVATE_NO_STORE. System prefixes select NO_STORE first in cache lookup; the later protected-asset gate forces PRIVATE_NO_STORE. A route override can weaken defaults, so never choose public caching for role-protected or personalized output. Protect object access in handlers independently of page roles.

## Settings

### `foundation.pages`

| Property | Type | Default | Effect | Change when |
| --- | --- | --- | --- | --- |
| `login-path` | `String` | `"/auth"` | Anonymous protected-page redirect destination | Your login page lives elsewhere |
| `redirect-parameter` | `String` | `"redirect"` | Name of encoded original-target parameter | Align login handler |
| `trailing-slash-redirect` | `boolean` | `true` | 308 to canonical URI with query preserved | Only when upstream canonicalization replaces it |
| `robots-tag` | `String` | `""` | X-Robots-Tag, blank disables | Choose application indexing policy |
| `page-size` | `int` | `10` | Default pagination size | Tune list page size; must be positive |
| `pagination-window-size` | `int` | `10` | Page-number window width, effective minimum 1 | Tune navigation width |
| `system-path-list` | `List<String>` | `["/api", "/stream"]` | Segment-aware no-store prefixes | Include application API and streaming prefixes |
| `robots-tag-excluded-path-list` | `List<String>` | `[]` | Canonical prefixes excluded from robots header | Keep selected public content indexable |

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

Security also arrives transitively. Its complete header, CSP, CSRF and JWT property tables, types and defaults are in [Security settings](../Spring-Security/INSTALLATION.md#settings). In particular, adding Pages adds CSRF checking to protected HTTP methods, not just pages. The production flag does not automatically infer TLS at the proxy.

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

PageAccessResolver is the only Pages bean marked ConditionalOnMissingBean. PageRegistry, NavbarService, PaginationService, the registrar, MVC configurer and filter registrations are not conditional replacement hooks. Add Page and AssetProvider beans and override Page methods rather than duplicating framework bean names. Inherited Common and Security conditional beans are documented in their installation guides.

`@Render` applies to a method. Registration rejects missing or multiple render methods and non-String return types. Duplicate canonical route keys and case-insensitive duplicate asset declarations fail at startup; exact and wildcard declarations may coexist. General route-pattern overlaps still require review and MVC mapping validation. `Navbar` visibility is presentation, not access control.

## Minimal working setup

1. Create a Boot MVC project with the dependency above and Application.java below.
2. Add WelcomePage.java. This is already a working public page with no provider wiring.
3. For protected pages, complete Security's JDBC/session setup first, reusing its Application and AccountRole. Add OrdersPage.java and the optional SharedAssetProvider.java. The example renders a text body, so no view engine or template is required.
4. Create the two static files and configuration shown below. Do not register the same asset in two owners.
5. Start the application. Visit `/`, then log in through Security's controller and visit `/orders` with that browser's cookie jar.
6. For HTML rendering, remove @ResponseBody, return your template name and add the desired view engine and templates. Add object-level ownership checks before reading real invoices.

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

`src/main/java/example/AccountRole.java`:

```java
package example;

import me.trae.foundation.spring.common.account.AccountRoleDefinition;

public enum AccountRole implements AccountRoleDefinition {

    MEMBER, ADMIN, OWNER
}
```

`src/main/java/example/WelcomePage.java`:

```java
package example;

import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.annotation.Render;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseBody;

@Component
public final class WelcomePage extends Page {

    public WelcomePage() {
        super("/", null);
    }

    @Render
    @ResponseBody
    public String render() {
        return "orders application";
    }
}
```

`src/main/java/example/OrdersPage.java`:

```java
package example;

import java.util.Set;
import me.trae.foundation.spring.pages.Page;
import me.trae.foundation.spring.pages.annotation.Render;
import me.trae.foundation.spring.pages.navbar.Navbar;
import me.trae.foundation.spring.pages.pagination.Pagination;
import me.trae.foundation.spring.pages.pagination.PaginationService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Component
public final class OrdersPage extends Page implements Navbar {

    private final PaginationService paginationService;

    public OrdersPage(final PaginationService paginationService) {
        super("/orders", AccountRole.MEMBER);

        this.paginationService = paginationService;
    }

    @Override
    public Set<String> getAssetPaths() {
        return Set.of("/js/orders.js", "/assets/orders/**");
    }

    @Override
    public int getNavbarPosition() {
        return 1;
    }

    @Override
    public String getNavbarName() {
        return "Orders";
    }

    @Render
    @ResponseBody
    public String render(@RequestParam(name = "page", defaultValue = "1") final int page) {
        final Pagination pagination = this.paginationService.getPagination(page, 35L);

        return "orders page %s, offset %s".formatted(pagination.getPage(), pagination.offset());
    }
}
```

`src/main/java/example/SharedAssetProvider.java`:

```java
package example;

import java.util.Collection;
import java.util.List;
import me.trae.foundation.spring.pages.asset.AssetProvider;
import me.trae.foundation.spring.pages.asset.PageAsset;
import me.trae.foundation.spring.pages.cache.CacheControlData;
import org.springframework.stereotype.Component;

@Component
public final class SharedAssetProvider implements AssetProvider {

    @Override
    public Collection<PageAsset> getAssets() {
        return List.of(new PageAsset("/css/shared.css"), new PageAsset("/downloads/invoices/**", AccountRole.MEMBER, CacheControlData.PRIVATE_NO_STORE));
    }
}
```

`src/main/resources/static/js/orders.js` and `src/main/resources/static/assets/orders/list.js` can each contain:

```javascript
window.orders = true;
```

Create `src/main/resources/static/css/shared.css` if you use the public shared declaration. Asset declarations only set policy; they do not create a file.

`src/main/resources/application.yaml`, merged with Security's settings when used:

```yaml
foundation:
  production: false
  pages:
    login-path: /auth
    redirect-parameter: redirect
    trailing-slash-redirect: true
    system-path-list: [/api, /stream]
```

Create your login page at /auth if you want a rendered sign-in experience; Security's sample provides /auth/login as a POST endpoint, not a login page. The interceptor encodes the original URI and query into the redirect parameter. Validate a bounce target with UtilRedirect when actually redirecting after authentication. The following optional controller demonstrates that predicate with a local fallback and does not implement authentication:

`src/main/java/example/RedirectController.java`:

```java
package example;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import me.trae.foundation.spring.pages.utility.UtilRedirect;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class RedirectController {

    @GetMapping("/auth/continue")
    public void redirect(@RequestParam(name = "redirect", required = false) final String redirect, final HttpServletResponse httpServletResponse) throws IOException {
        httpServletResponse.sendRedirect(Optional.ofNullable(UtilRedirect.sanitise(redirect)).orElse("/"));
    }
}
```

UtilRedirect rejects null/blank, non-leading-slash paths, a second slash or backslash, opening braces and literal CR/LF. It does not decode percent escapes or reject every embedded backslash. Validate the decoded target actually used and do not decode again after checking.

Use PaginationService.getPagination(page, totalCount) or its custom-size overload. Pagination provides getPage, getSize, getTotalCount, getTotalPages, getWindowStart, getWindowEnd, offset, hasPrevious, hasNext and isEmpty. Requested page is clamped into range; size below 1 throws. NavbarService.getNavbarEntryList(request, response) gives sorted visible entries; more than one base-route prefix can be active.

## Verification

| Request or setup | Expected |
| --- | --- |
| GET / | 200 text body without account lookup |
| Anonymous GET /orders?sort=date | Redirect to /auth with encoded original target |
| Authenticated insufficient role | 403 before the page handler |
| Authenticated MEMBER or greater | 200 orders text |
| Anonymous GET /js/orders.js or protected wildcard resource | 404, private no-store |
| Authorized request for the actual declared file | 200, private no-store |
| Case alias request without authorization | Still gated; successful file serving depends on resource handler case behavior |
| GET /orders/ | 308 to /orders with query preserved |
| POST without matching CSRF header | 403 from inherited Security, unless explicitly excluded |
| No robots-tag setting | No X-Robots-Tag |
| Duplicate route or duplicate asset declaration | Context refresh fails with PageRegistrationException |

Check authentication-cookie names/attributes and separate browser sessions as described in [Security verification](../Spring-Security/INSTALLATION.md#verification). The tests cover two independent cookie jars reaching a protected Page and logout affecting one only, plus real HTTP asset gating.

```shell
mvn -pl Spring/Spring-Pages -am test
```

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Login redirect loops | Correct login-path, make login page public, and install authenticated provider wiring |
| Asset always 404 | Viewer lacks role, declaration is broader than intended, or the downstream file does not exist |
| Protected file is public through another URL | Declare that URL or gate its separate hosting path; Pages discovers no files automatically |
| Text looks like a missing template | Use @ResponseBody for text or install a view engine and matching template |
| Duplicate path startup failure | One owner per canonical declaration; share assets through AssetProvider |
| Unexpected access after enum edit | Recheck ordinal privilege order or override hasRole |
| Anonymous provider called only once | Expected negative-result request cache; next request resolves again |
| Private data found in shared cache | Remove public route override, preserve private/no-store headers in proxy and handlers |
| Sessions survive password update | Application must call Rotation.revoke(account), as in Security setup |
| POST rejected before page code | Inherited CSRF requires matching cookie/header and source hostname |

Return to the [Spring guide](../README.md) or [Foundation](../../README.md).