<h1 align="center">Spring-Pages</h1>

<p align="center">Page-as-a-class framework with role-based access, asset gating, cache control, navigation, and pagination for Spring Boot.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet-0769AD?logo=jakartaee&logoColor=white"></a>
</p>

Spring-Pages models each page as a class rather than a controller method. A page declares its own route, the role needed to see it, the assets it owns, and how it caches, all in one place. The route string appears once. Routes are registered at boot through `ContextRefreshedEvent`, role enforcement runs as an interceptor, and four filters handle cache control, trailing-slash redirects, robots tags, and static resource gating. Depends on [Spring-Common](../Spring-Common/README.md) only.

## Contents

<details>
<summary>On this page</summary>

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
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Purpose |
| --- | --- |
| `Page` | Base class every page extends; holds the route, required role, and asset/cache settings |
| `@Render` | Marks the one method that renders the page, equivalent to `@GetMapping` |
| `PageRole` | Marker interface your role enum implements |
| `PageAccessResolver` | How the module asks your application who is logged in and what they can see |
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

## The role contract

The library never learns what a role means. Your enum implements `PageRole`:

```java
public enum AccountRole implements PageRole {

    STANDARD,
    MODERATOR,
    ADMIN,
    OWNER;

    public boolean hasRole(final AccountRole accountRole) {
        return this.ordinal() >= accountRole.ordinal();
    }
}
```

An enum satisfies `PageRole` with no implementation, since `Enum.name()` already provides the `name()` method. Note that `name()` is `final` on `Enum`, so what a template reads from a role is the constant name in caps. Add your own display method if you want `Owner` rather than `OWNER`.

Then one bean answers both questions the module needs:

```java
@Component
public final class MyPageAccessResolver implements PageAccessResolver {

    private final MyJwtService jwtService;

    public MyPageAccessResolver(final MyJwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean isAuthenticated(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
        return this.jwtService.isAuthenticated(httpServletRequest, httpServletResponse);
    }

    @Override
    public boolean hasRole(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse, final PageRole pageRole) {
        return this.jwtService.getAccountByRequest(httpServletRequest, httpServletResponse)
                .map(account -> account.getRole().hasRole(AccountRole.class.cast(pageRole)))
                .orElse(false);
    }
}
```

Two methods rather than one, because the answers differ: not authenticated redirects to the login page; authenticated but underprivileged gets `403` rather than a pointless login bounce.

Without this bean, the default `DenyPageAccessResolver` refuses everything. That is deliberate: a missing resolver fails closed. Every role-gated page returns `403` until you provide one.

## Writing a page

```java
@Component
public final class OrdersPage extends Page {

    private final OrderManager orderManager;
    private final PaginationService paginationService;

    public OrdersPage(final OrderManager orderManager, final PaginationService paginationService) {
        super("/orders", AccountRole.STANDARD);

        this.orderManager = orderManager;
        this.paginationService = paginationService;
    }

    @Override
    public Set<String> getAssetPaths() {
        return Set.of("/js/orders.js", "/css/orders.css");
    }

    @Render
    public String render(@RequestParam(name = "page", defaultValue = "1") final int page, final Model model) {
        final long totalCount = this.orderManager.getTotalCount();

        final Pagination pagination = this.paginationService.getPagination(page, totalCount);

        model.addAttribute("pagination", pagination);
        model.addAttribute("orderList", this.orderManager.getPage(pagination.getPage(), pagination.getSize()));

        return "orders";
    }
}
```

`super(route, requiredRole)` with a `null` role makes the page public. The `@Render` method takes anything a normal `@GetMapping` method takes (`@RequestParam`, `@PathVariable`, `Model`, `HttpServletRequest`) and returns the view name as a `String`.

**Path variables** go in the route:

```java
public OrderPage(final OrderManager orderManager) {
    super("/order/{id}", AccountRole.STANDARD);
}

@Render
public String render(@PathVariable("id") final UUID id, final Model model) {
    return "order";
}
```

`getBaseRoute()` cuts at the first brace, so `/order/{id}` contributes `/order` to navbar matching and asset prefix resolution.

**Validation at boot:** a page with no `@Render` method, more than one, or one that does not return `String` throws `PageRegistrationException`. A duplicate route or an asset path claimed by two owners also throws at startup.

## Assets and shared assets

`getAssetPaths()` declares the files a page owns. Two things follow:

1. Those paths inherit the page's cache policy.
2. If the page requires a role, so do its assets. An unauthorized request for a gated asset gets `404` rather than `403`, so nobody learns what exists.

Paths must be unique across the whole application. A collision throws at boot and names both owners, because two pages claiming the same asset means one of their role requirements silently loses.

Files used by more than one page go through `AssetProvider`:

```java
@Component
public final class SharedAssetProvider implements AssetProvider {

    @Override
    public Collection<PageAsset> getAssets() {
        return List.of(
                new PageAsset("/css/root.css"),
                new PageAsset("/js/root.js"),
                new PageAsset("/js/dashboard-widget.js", AccountRole.STANDARD, CacheControlData.PRIVATE_NO_STORE)
        );
    }
}
```

The one-argument `PageAsset` constructor creates a public, uncached-but-shareable asset (`SHARED_SHORT`, no role).

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

Anything under `foundation.pages.system-path-list` gets `NO_STORE` regardless.

## Navigation

Optional. A page implements `Navbar` and appears in the navigation bar; pages that do not implement it are excluded.

```java
@Component
public final class OrdersPage extends Page implements Navbar {

    @Override
    public int getNavbarPosition() {
        return 2;
    }

    @Override
    public String getNavbarName() {
        return "Orders";
    }

    @Override
    public List<String> getNavbarDescription() {
        return List.of("Every order and its current status.", "Create orders and change where they are up to.");
    }
}
```

`NavbarService.getNavbarEntryList(request, response)` returns entries the current viewer may actually reach, sorted by position, with the one matching the current path marked active. Each `NavbarEntry` carries the `PageRole`, the route, the name, the description list, and whether it is active.

The service calls `hasRole` once per navbar page. If your resolver queries a database, cache the resolved account on the request.

## Pagination

`PaginationService` reads the default page size and window size from configuration:

```java
final Pagination pagination = this.paginationService.getPagination(page, totalCount);
```

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

Every component compares canonical paths, via `UtilRequestPath` from [Spring-Common](../Spring-Common/README.md): slash runs collapsed, trailing slash stripped, leading slash guaranteed, case untouched. `PageTrailingSlashFilter` then `308` redirects anything non-canonical to its canonical form, preserving the query string, so `/orders/` and `//orders` resolve to one URL.

This only holds while every component uses canonical paths. One filter reading `getRequestURI()` directly reopens the mismatch.

The filter is controlled by `foundation.pages.trailing-slash-redirect` (default `true`). It does not redirect; it only canonicalizes. Serving `/orders/` and `/orders` as one URL is a redirect, which this filter handles.

## Login redirects and open redirect prevention

When an unauthenticated viewer hits a gated page, `PageAccessInterceptor` redirects to `foundation.pages.login-path` with the original path in `foundation.pages.redirect-parameter`.

The login page must run that parameter through `UtilRedirect` before using it, or the application has an open redirect:

```java
@Render
public String render(@RequestParam(name = "redirect", required = false) final String redirect, final Model model) {
    model.addAttribute("redirect", UtilRedirect.sanitise(redirect));

    return "auth";
}
```

`sanitise` returns the value when it is a safe same-site path and `null` otherwise. It rejects anything not starting with a single slash, which covers `//evil.example.com` and `/\evil.example.com`, plus header injection attempts and unexpanded path variables (strings containing `{`).

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

`PagesAutoConfiguration` is conditional on a servlet web application and runs after `CommonAutoConfiguration`. It registers:

- `PageRegistry`, `NavbarService`, `PaginationService`
- `PageAccessResolver` (default: `DenyPageAccessResolver`, replaceable with `@ConditionalOnMissingBean`)
- A `ContextRefreshedEvent` listener that scans `Page` beans, validates `@Render` methods, builds the registry, and registers routes through `RequestMappingHandlerMapping`
- A `WebMvcConfigurer` adding `PageAccessInterceptor`
- Four `FilterRegistrationBean` instances for the page filters

There is no `@Configuration` class, `@ComponentScan` entry, or `@Enable` annotation to add. Pages are discovered as beans through the application's own component scan. Registration is guarded against a repeated context refresh, which would otherwise fail with an ambiguous mapping.

Adding the JAR to a non-web or reactive application registers nothing.

**Setup checklist:**

1. Have your role enum implement `PageRole` and declare a `PageAccessResolver` bean. Until then, every gated page returns `403`.
2. Write pages as `@Component` classes extending `Page`, each with exactly one `@Render` method returning a view name.
3. Point `foundation.pages.login-path` at your login page and run its redirect parameter through `UtilRedirect.sanitise`.

Optional: implement `Navbar` on pages for navigation, and declare an `AssetProvider` bean for shared assets.

## Installation and build

```xml
<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Pages</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Spring-Common comes in transitively. From the repository root with JDK 25 and Maven:

```shell
mvn -pl Spring/Spring-Pages -am install
```

Return to the [Spring guide](../README.md) or [Foundation](../../README.md).
