<h1 align="center">Spring-Pages</h1>

<p align="center">Page-as-a-class framework with role-based access, asset gating, cache control, navigation, and pagination for Spring Boot.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet-0769AD?logo=jakartaee&logoColor=white"></a>
</p>

Spring-Pages models each page as a class rather than a controller method. A page declares its own route, the role needed to see it, the assets it owns, and how it caches, all in one place. The route string appears once. Routes are registered at boot through `ContextRefreshedEvent`, role enforcement runs as an interceptor, and four filters handle cache control, trailing-slash redirects, robots tags, and static resource gating. Depends on [Spring-Security](../Spring-Security/README.md), which brings Spring-Common transitively.

Follow [Installation](INSTALLATION.md) for complete compiling setup examples, settings and troubleshooting.

## Contents

<details>
<summary>On this page</summary>

- [Installation](INSTALLATION.md)
- [At a glance](#at-a-glance)
- [The role contract](#the-role-contract)
- [Writing a page](#writing-a-page)
- [Assets and shared assets](#assets-and-shared-assets)
- [Cache control](#cache-control)
- [Navigation](#navigation)
- [Pagination](#pagination)
- [Path canonicalization and trailing slashes](#path-canonicalization-and-trailing-slashes)
- [Login redirects and open redirect prevention](#login-redirects-and-open-redirect-prevention)
- [Filter and interceptor order](#filter-and-interceptor-order)
- [Configuration](#configuration)
- [Auto-configuration](#auto-configuration)
- [Security model](SECURITY.md)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Purpose |
| --- | --- |
| `Page` | Base class every page extends; holds the route, required role, and asset/cache settings |
| `@Render` | Marks the one method that renders the page, used to register a GET handler, with a required String return type |
| `AccountRoleDefinition` | Shared interface your role enum implements |
| `PageAccessResolver` | Resolves page authentication and role access |
| `PageRegistry` | Routes, assets, roles, and cache policies, built once at boot |
| `PageAccessInterceptor` | Enforces the required role before the page renders |
| `StaticResourceFilter` | Gates assets belonging to role-protected pages |
| `PageCacheControlFilter` | Applies each route's and asset's cache policy |
| `PageTrailingSlashFilter` | 308 redirects non-canonical paths to the canonical form |
| `RobotsTagFilter` | Blanket `X-Robots-Tag` header, off by default |
| `AssetProvider` / `PageAsset` | Shared assets across pages |
| `Navbar` / `NavbarEntry` / `NavbarService` | Navigation built from the pages themselves |
| `Pagination` / `PaginationService` | Page numbers, offsets, and the window to render |
| `UtilRedirect` | Open redirect guard for login bounce targets |

See the [security model](SECURITY.md) for authorization boundaries and cache behavior.

## The role contract

Use your application's role enum implementing `AccountRoleDefinition` when constructing pages and assets.

Register a `SecurityAccountProvider<Account>` bean with your account implementing `AccountIdentity<AccountRole>` and your role enum implementing `AccountRoleDefinition`. The default `AccountPageAccessResolver` uses the provider. Custom `PageAccessResolver` beans remain supported. Account data lookup by UUID remains in Common; authenticated request lookup belongs to Security.

Pages passes the actual response through request resolution so JWT refresh can write cookies. The default `AccountPageAccessResolver` resolves the provider once per request, caching authenticated and anonymous results for later checks.

Pages calls `PageAccessResolver.isAuthenticated(request, response)` and `hasRole(request, response, requiredRole)`. The default resolver looks up the account through `SecurityAccountProvider.getAccountByRequest(request, response)` and delegates to its `hasRole` method. Anonymous page requests redirect to login; unauthorized authenticated viewers receive `403`. Protected assets return `404` without authorization, and unauthorized navbar entries are omitted.

`AccountIdentity.hasRole` compares the current and required ordinals by default. Declare enum values in ascending privilege order, such as `MEMBER, MODERATOR, ADMIN, OWNER`, or override the account method for custom authorization. The default `EmptySecurityAccountProvider` returns no account.

See the [complete Security provider implementation and bean example](../Spring-Security/README.md#creating-a-security-account-provider), plus the [Common lookup provider examples](../Spring-Common/README.md#creating-a-common-account-provider). Register these providers once in your application; Pages and Ratelimit share the Security provider.

## Writing a page

See the [complete application files](INSTALLATION.md#minimal-working-setup).

`super(route, requiredRole)` with a `null` role makes the page public. The `@Render` method takes anything a normal `@GetMapping` method takes (`@RequestParam`, `@PathVariable`, `Model`, `HttpServletRequest`) and returns the view name as a `String`.

**Path variables** go in the route:

See the [complete application files](INSTALLATION.md#minimal-working-setup).

`getBaseRoute()` cuts at the first brace, so `/order/{id}` contributes `/order` to navbar matching. Assets are registered by their explicit canonical paths, not by that base route.

**Validation at boot:** a page with no `@Render` method, more than one, or one that does not return `String` throws `PageRegistrationException`. A duplicate route or an asset path claimed by two owners also throws at startup.

## Assets and shared assets

`getAssetPaths()` declares the files a page owns. Two things follow:

1. Those paths inherit the page's cache policy.
2. If the page requires a role, so do its declared assets. An unauthorized request for a gated asset gets `404` rather than `403`, to avoid explicitly distinguishing a forbidden asset from an absent one.

Canonical asset declarations must be unique across the application; exact and wildcard declarations remain distinct. A collision throws at boot and names both owners, because two pages claiming the same asset means one of their role requirements silently loses.

Files used by more than one page go through `AssetProvider`:

See the [complete application files](INSTALLATION.md#minimal-working-setup).

The one-argument `PageAsset` constructor creates a public asset cached for five minutes (`SHARED_SHORT`, no role).

### Whole asset directories

Use a terminal `/**` to declare a directory and every descendant at the page's own role and asset cache policy:

See the [complete application files](INSTALLATION.md#minimal-working-setup).

`/assets/admin/**` covers `/assets/admin`, `/assets/admin/x.js` and `/assets/admin/deep/y.css`, but not `/assets/administrator.js`. `/**` covers the root and all descendants. Matching is segment-aware, not a plain string prefix. Declarations are canonicalized, including dot segments.

Asset role gating is case-insensitive: `/js/account.js` also gates `/js/Account.JS`, and `/assets/admin/**` also gates `/ASSETS/Admin/x.js`. Duplicate declarations, including case aliases, fail registration. Exact and wildcard declarations of the same base path can coexist: an exact protected entry wins for that path, otherwise the longest matching protected prefix supplies the role. Cache lookup likewise checks exact entries before the longest matching prefix, using the declared cache-key case. The original exact-map getters remain exact indexes; prefix entries have separate map getters and the registry lookup methods combine them.

This does not broaden page routes. Page route keys remain exact and case-sensitive, so `/orders` and `/Orders` can both register. Existing MVC route templates still work. Asset declarations do not create files or MVC handlers; an authorized request still needs a downstream resource to serve.

## Cache control

`CacheControlData` carries five presets:

| Constant | Header value | Intended for |
| --- | --- | --- |
| `NO_STORE` | `private, no-store, no-cache, must-revalidate` | API and stream endpoints |
| `IMMUTABLE` | `public, max-age=31536000, immutable` | Fingerprinted assets |
| `SHARED_SHORT` | `public, max-age=300, must-revalidate` | Identical output for everyone |
| `PRIVATE_REVALIDATE` | `private, no-cache, must-revalidate` | Per-account HTML (the default for routes) |
| `PRIVATE_NO_STORE` | `private, no-store` | Anything behind a role |

Override `getRouteCacheControlData()` on a page to change its policy. Asset policy follows the page's role automatically: public assets get `SHARED_SHORT`, protected assets get `PRIVATE_NO_STORE`. Override `getAssetCacheControlData()` when needed.

System prefixes select `NO_STORE` before other cache lookup. The later asset gate forces `PRIVATE_NO_STORE` for protected assets. Preserve private policies in downstream handlers and infrastructure.

## Navigation

Optional. Implement Navbar on a Page bean to make it appear in navigation; the installation example includes all methods. Pages that do not implement `Navbar` are excluded.

See the [complete application files](INSTALLATION.md#minimal-working-setup).

`NavbarService.getNavbarEntryList(request, response)` returns entries the current viewer may actually reach, sorted by position, with entries whose base route matches the current path marked active. Each `NavbarEntry` carries your required role enum, the route, the name, the description list, and whether it is active.

The service calls `PageAccessResolver.hasRole` once per protected navbar page. The default `AccountPageAccessResolver` caches the account or anonymous result on the request, so authentication, role and navbar checks share one provider resolution. Custom resolvers must manage their own lookup costs.

## Pagination

`PaginationService` reads the default page size and window size from configuration:

See the [complete application files](INSTALLATION.md#minimal-working-setup).

`Pagination` clamps the requested page into range: `?page=999` shows the last page and `?page=-1` the first. It exposes:

| Method | Purpose |
| --- | --- |
| `getPage()` | The clamped one-based page number |
| `getSize()` | Items per page |
| `getTotalPages()` | Computed from total count and size |
| `getTotalCount()` | The total number of items |
| `offset()` | Zero-based offset for repository queries |
| `hasPrevious()` / `hasNext()` | Navigation availability |
| `isEmpty()` | Whether the total count is zero |
| `getWindowStart()` / `getWindowEnd()` | The page number range for rendering a number strip |

`offset()` and `getSize()` feed a repository directly, which is the point: the subtraction between one-based page numbers and zero-based offsets happens once, here.

An overloaded `getPagination(page, totalCount, size)` accepts a custom page size when the configured default does not apply.

## Path canonicalization and trailing slashes

Path comparisons use `UtilRequestPath` from [Spring-Common](../Spring-Common/README.md): slash runs collapse, `.` and `..` segments resolve with traversal clamped at root, trailing slash is stripped and a leading slash is guaranteed. Canonicalization preserves case; asset role lookup separately ignores it. `PageTrailingSlashFilter` then `308` redirects anything non-canonical to its canonical form, preserving the query string, so `/orders/` and `//orders` resolve to one URL.

This only holds while every component uses canonical paths. One filter reading `getRequestURI()` directly reopens the mismatch.

The filter is controlled by `foundation.pages.trailing-slash-redirect` (default `true`). `UtilRequestPath` computes the canonical path; `PageTrailingSlashFilter` sends the redirect. Disabling the filter leaves the shared path comparisons in place without redirecting requests.

## Login redirects and open redirect prevention

When an unauthenticated viewer hits a gated page, `PageAccessInterceptor` redirects to `foundation.pages.login-path` with the original path in `foundation.pages.redirect-parameter`.

The login page must run that parameter through `UtilRedirect` before using it, or the application has an open redirect:

See the [complete application files](INSTALLATION.md#minimal-working-setup).

`sanitise` returns the value when it passes its local leading-slash path predicate and `null` otherwise. It rejects anything not starting with a single slash, which covers `//evil.example.com` and `/\evil.example.com`, plus literal CR/LF and opening braces. It does not reject every embedded backslash or decode percent-encoded input. Validate the value actually redirected, without decoding again afterwards.

`isSafe(redirect)` performs the same check and returns a boolean.

## Filter and interceptor order

Filters are registered through `FilterRegistrationBean` with explicit order and dispatch types:

| Component | Type | Order | Dispatch types |
| --- | --- | --- | --- |
| `RobotsTagFilter` | Filter | 1 | `REQUEST`, `ERROR` |
| `PageCacheControlFilter` | Filter | 3 | `REQUEST`, `ERROR` |
| `PageTrailingSlashFilter` | Filter | 4 | `REQUEST` |
| `StaticResourceFilter` | Filter | 5 | `REQUEST` |
| `PageAccessInterceptor` | Interceptor | After filters | Mapped requests only |

`StaticResourceFilter` runs last on purpose: it forces `PRIVATE_NO_STORE` on gated assets, which has to win over whatever `PageCacheControlFilter` set earlier.

`PageAccessInterceptor` is an interceptor, not a filter, so it runs after the entire filter chain and only for requests that reach a mapped page handler.

Order values come from `FilterOrderConstants` in Spring-Common. See the [Spring-Security guide](../Spring-Security/README.md) for the security filters at positions 0 and 2.

## Configuration

```yaml
foundation:
  pages:
    login-path: /auth
    redirect-parameter: redirect
    trailing-slash-redirect: true
    robots-tag: ""
    page-size: 10
    pagination-window-size: 10
    system-path-list:
      - /api
      - /stream
    robots-tag-excluded-path-list: []
```

| Property | Default | Purpose |
| --- | --- | --- |
| `login-path` | `/auth` | Redirect target for unauthenticated viewers |
| `redirect-parameter` | `redirect` | Query parameter carrying the original path |
| `trailing-slash-redirect` | `true` | Enable 308 redirects for non-canonical paths |
| `robots-tag` | `""` (disabled) | `X-Robots-Tag` header value; empty sends no header |
| `page-size` | `10` | Default items per page for `PaginationService` |
| `pagination-window-size` | `10` | Number strip width |
| `system-path-list` | `/api`, `/stream` | Paths receiving `NO_STORE` cache control |
| `robots-tag-excluded-path-list` | Empty | Paths excluded from the robots tag header |

## Auto-configuration

`PagesAutoConfiguration` is conditional on a servlet web application and runs after `SecurityAutoConfiguration`. It registers:

- `PageRegistry`, `NavbarService`, `PaginationService`
- `PageAccessResolver` (default: `AccountPageAccessResolver`, replaceable with an application bean)
- A `ContextRefreshedEvent` listener that scans `Page` beans, validates `@Render` methods, builds the registry, and registers routes through `RequestMappingHandlerMapping`
- A `WebMvcConfigurer` adding `PageAccessInterceptor`
- Four `FilterRegistrationBean` instances for the page filters

There is no `@Configuration` class, `@ComponentScan` entry, or `@Enable` annotation to add. Pages are discovered as beans through the application's own component scan. Registration is guarded against a repeated context refresh, which would otherwise fail with an ambiguous mapping.

Adding the JAR to a non-web or reactive application registers nothing.

**Setup checklist:**

1. Register your `SecurityAccountProvider<Account>` bean and pass role enum values implementing `AccountRoleDefinition` to pages. Without application wiring, gated page requests redirect to login and gated assets return `404`.
2. Write pages as `@Component` classes extending `Page`, each with exactly one `@Render` method returning a view name.
3. Point `foundation.pages.login-path` at your login page and run its redirect parameter through `UtilRedirect.sanitise`.

Optional: implement `Navbar` on pages for navigation, and declare an `AssetProvider` bean for shared assets.

## Security model

Read the [security model](SECURITY.md) for the controls, design trade-offs, configuration risks and application responsibilities. Vulnerability reporting guidance is included there.

## Installation and build

```xml
<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Pages</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Spring-Security and Spring-Common come in transitively. From the repository root with JDK 25 and Maven:

```shell
mvn -pl Spring/Spring-Pages -am install
```

Return to the [Spring guide](../README.md) or [Foundation](../../README.md).