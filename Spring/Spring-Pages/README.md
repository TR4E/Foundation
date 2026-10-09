# Spring-Pages

A page layer for Spring Boot. A page is a class, not a controller method: it declares its own route, the role needed to see it, the assets it owns and how it caches, and registers itself at boot. The route string appears once.

Depends on Spring-Common only. It does not depend on Spring-Security, so the two can be used independently.

## Contents

| Type | Purpose |
| --- | --- |
| `Page` | The base class every page extends |
| `@Render` | Marks the one method that renders the page |
| `PageRole` | Marker your role enum implements |
| `PageAccessResolver` | How the module asks your application who is logged in |
| `PageRegistry` | Routes, assets, roles and cache control, built once at boot |
| `PageAccessInterceptor` | Enforces the required role before the page renders |
| `StaticResourceFilter` | Gates assets belonging to role-protected pages |
| `PageCacheControlFilter` | Applies each route's and asset's cache policy |
| `PageTrailingSlashFilter` | Redirects non canonical paths to the canonical one |
| `RobotsTagFilter` | Blanket `X-Robots-Tag` header, off by default |
| `AssetProvider` / `PageAsset` | Assets shared across pages |
| `Navbar` / `NavbarEntry` / `NavbarService` | Optional navigation built from the pages themselves |
| `Pagination` / `PaginationService` | Page numbers, offsets and the window to render |
| `UtilRedirect` | Open redirect guard for login bounce targets |

## The role contract

The library never learns what a role means. Your enum implements `PageRole`:

```java
public enum AccountRole implements PageRole {

    STANDARD,
    ADMIN,
    OWNER;

    public boolean hasRole(final AccountRole accountRole) {
        return this.ordinal() >= accountRole.ordinal();
    }
}
```

An enum satisfies `PageRole` with no implementation at all, since `Enum.name()` already provides it. Note that `name()` is `final` on `Enum`, so what a template reads off a role is the constant name in caps. Add your own display method if you want `Owner` rather than `OWNER`.

and one bean answers both questions the module needs:

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
        return this.jwtService.getAccountByRequest(httpServletRequest, httpServletResponse).map(account -> account.getRole().hasRole(AccountRole.class.cast(pageRole))).orElse(false);
    }
}
```

Two methods rather than one, because the answers differ: not authenticated redirects to the login page, authenticated but underprivileged gets a `403` rather than a pointless login bounce.

Without this bean the default `DenyPageAccessResolver` refuses everything. That is deliberate, a missing resolver fails closed, but it does mean every role-gated page returns `403` until you provide one.

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

`super(route, requiredRole)` with a `null` role makes the page public. The `@Render` method takes anything a normal `@GetMapping` method takes (`@RequestParam`, `@PathVariable`, `Model`, `HttpServletRequest`) and returns the view name.

Path variables go in the route itself:

```java
    public OrderPage(final OrderManager orderManager) {
        super("/order/{id}", AccountRole.STANDARD);
    }

    @Render
    public String render(@PathVariable("id") final UUID id, final Model model) {
        ...
    }
```

`getBaseRoute()` cuts at the first brace, so `/order/{id}` contributes `/order` to navbar matching.

## Assets

`getAssetPaths()` declares the files a page owns. Two things follow from that: those paths inherit the page's cache policy, and if the page requires a role, so do its assets. An unauthorised request for a gated asset gets `404` rather than `403`, so nobody learns what exists.

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

The one argument constructor is public and uncached-but-shareable (`SHARED_SHORT`, no role).

## Cache control

`CacheControlData` carries five presets:

| Constant | Value | For |
| --- | --- | --- |
| `NO_STORE` | `private, no-store, no-cache, must-revalidate` | API and stream endpoints |
| `IMMUTABLE` | `public, max-age=31536000, immutable` | Fingerprinted assets |
| `SHARED_SHORT` | `public, max-age=300, must-revalidate` | Identical output for everyone |
| `PRIVATE_REVALIDATE` | `private, no-cache, must-revalidate` | Per account HTML, the default for routes |
| `PRIVATE_NO_STORE` | `private, no-store` | Anything behind a role |

Override `getRouteCacheControlData()` on a page to change its own policy. Asset policy follows the page's role automatically, so you rarely touch `getAssetCacheControlData()`.

Anything under `foundation.pages.system-path-list` gets `NO_STORE` regardless.

## Navbar

Optional. A page implements `Navbar` and appears in the bar; pages that don't, don't.

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

`NavbarService.getNavbarEntryList(request, response)` returns the entries the current viewer may actually reach, sorted by position, with the one matching the current path marked active. Each entry carries the `PageRole` itself, so a template reading it gets the constant name in caps unless your enum exposes a display method of its own. It calls `hasRole` once per navbar page, so if your resolver hits a database make sure it caches the resolved account on the request.

## Pagination

`PaginationService` reads the defaults from configuration, so page size lives in one place:

```java
final Pagination pagination = this.paginationService.getPagination(page, totalCount);
```

`Pagination` clamps the requested page into range, so `?page=999` shows the last page and `?page=-1` the first, and exposes `getPage()`, `getSize()`, `getTotalPages()`, `offset()`, `hasPrevious()`, `hasNext()`, `isEmpty()`, plus `getWindowStart()` and `getWindowEnd()` for rendering the number strip.

`offset()` and `getSize()` feed a repository directly, which is the whole point: the subtraction between one based page numbers and zero based offsets happens once, here.

## Path canonicalisation

Every component compares canonical paths, via `UtilRequestPath` in Spring-Common: slash runs collapsed, trailing slash stripped, leading slash guaranteed, case untouched. `PageTrailingSlashFilter` then `308`s anything non canonical to its canonical form, preserving the query string, so `/orders/` and `//orders` resolve to one URL rather than three.

This only holds while every component plays along. One filter reading `getRequestURI()` directly reopens the mismatch it closes.

## Login redirects

When an unauthenticated viewer hits a gated page, the interceptor sends them to `foundation.pages.login-path` with the original path in `foundation.pages.redirect-parameter`.

Your login page must run that parameter through `UtilRedirect` before using it, or you have an open redirect:

```java
    @Render
    public String render(@RequestParam(name = "redirect", required = false) final String redirect, final Model model) {
        model.addAttribute("redirect", UtilRedirect.sanitise(redirect));

        return "auth";
    }
```

`sanitise` returns the value when it is a safe same site path and `null` otherwise. It rejects anything not starting with a single slash, which covers `//evil.example.com` and `/\evil.example.com`, plus header injection attempts and unexpanded path variables.

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

`robots-tag` is empty by default, so no header is sent. Set it to `noindex, nofollow, noarchive, nosnippet` for a site that should never be indexed, and list any public paths under `robots-tag-excluded-path-list`.

## Filter order

Order constants live in `FilterOrderConstants` in Spring-Common so the chain is declared in one place across modules:

| Filter | Order | Module |
| --- | --- | --- |
| `SecurityHeadersFilter` | 0 | Spring-Security |
| `RobotsTagFilter` | 1 | Spring-Pages |
| `CsrfFilter` | 2 | Spring-Security |
| `PageCacheControlFilter` | 3 | Spring-Pages |
| `PageTrailingSlashFilter` | 4 | Spring-Pages |
| `StaticResourceFilter` | 5 | Spring-Pages |

`StaticResourceFilter` runs last on purpose: it forces `PRIVATE_NO_STORE` on gated assets, which has to win over whatever `PageCacheControlFilter` set earlier.

`PageAccessInterceptor` is an interceptor, not a filter, so it runs after the entire chain and only for requests that reach a mapped page.

## How to Wire into a Spring Boot Application

Add the dependency:

```xml
<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Pages</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

That is the whole wiring step. `PagesAutoConfiguration` registers the registry, the registrar, the interceptor, all four filters and the navbar and pagination services on the classpath alone, so there is no `@Configuration` class, no `@ComponentScan` entry and no `@Enable` annotation to add in the consuming application. Spring-Common comes in transitively.

Then do these three, in this order, because nothing works until the first one is done:

1. Have your role enum implement `PageRole`, and declare a `PageAccessResolver` bean. Until then every gated page returns `403`.
2. Write your pages as `@Component` classes extending `Page`, each with exactly one `@Render` method returning a view name.
3. Point `foundation.pages.login-path` at your login page, and run its redirect parameter through `UtilRedirect.sanitise`.

Optional after that: implement `Navbar` on the pages that belong in navigation, and declare an `AssetProvider` bean for files shared across pages.

Pages are discovered as beans, so they are ordinary `@Component` classes picked up by your own component scan. The registry is built and the routes registered on `ContextRefreshedEvent` rather than during bean wiring, so the handler mapping cannot be forced to initialise early and end up half built. Registration is guarded against a repeated refresh, which would otherwise fail with an ambiguous mapping.

Failures are loud and at boot: a page with no `@Render` method, more than one, or one that does not return `String` throws, as does a duplicate route or an asset path claimed by two owners.

The auto configuration is conditional on a servlet web application, so adding the jar to a non web or reactive application registers nothing rather than failing at startup.
