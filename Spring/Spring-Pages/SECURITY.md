# Spring-Pages Security Model

![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square)

Spring-Pages enforces declared page roles and asset roles, and supplies canonical-path, redirect and cache policies around those declarations.

## Scope

For setup and complete property tables, use [Installation](INSTALLATION.md).

This describes `me.trae.foundation.spring:Spring-Pages`, including its page interceptor, asset registry and filters, redirect utility and navigation filtering. Common supplies path and role primitives; Security supplies authenticated accounts; Ratelimit budgets requests. Neighbouring boundaries are documented in [Spring-Common](../Spring-Common/SECURITY.md), [Spring-Security](../Spring-Security/SECURITY.md), [Spring-Ratelimit](../Spring-Ratelimit/SECURITY.md).

Status legend: ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) module enforcement without configuration; ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) correct configuration required; ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) application implementation required; ![Infrastructure](https://img.shields.io/badge/infrastructure-0969DA?style=flat-square) external infrastructure required; ![Not covered](https://img.shields.io/badge/not%20covered-CF222E?style=flat-square) outside this module.

## Threat model

| Surface | Attacker attempts | Control | Residual risk and status |
| --- | --- | --- | --- |
| Protected page | Invoke a page without its required privilege | [Page authorization](#page-authorization) | Role order and object permissions belong to application ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| Login target | Send a victim through login to an attacker destination | [Login redirects](#login-redirects) | Login handler must call sanitizer ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| Static assets | Fetch protected scripts or files directly or through aliases | [Asset protection](#asset-protection) | Declarations must cover every served URL ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| Navigation | Discover and request a hidden privileged route | [Navigation](#navigation) | Hiding entries is presentation only ![Not covered](https://img.shields.io/badge/not%20covered-CF222E?style=flat-square) |
| Cached content | Receive another user's private response from a shared cache | [Cache control](#cache-control) | Route overrides can weaken privacy ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| Path aliases | Obtain a weaker policy using another spelling of a route | [Canonical paths and registration](#canonical-paths-and-registration) | Routing and decoding must agree ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| Indexing | Find a sensitive URL through search indexing | [Robots directives](#robots-directives) | Crawlers may ignore directives ![Not covered](https://img.shields.io/badge/not%20covered-CF222E?style=flat-square) |

## Page authorization

A `Page` declares `requiredRole`; null means public. `PageAccessInterceptor` examines a handler whose bean is a `Page` before it runs. Protected pages ask `PageAccessResolver` for authentication and the role. The default `AccountPageAccessResolver` delegates to `SecurityAccountProvider`, caching either the account or an anonymous sentinel in a request attribute. One resolution serves authentication, role checks and other consumers of that resolver during the same request, avoiding repeated lookups and refresh side effects. This is not a cross-request identity cache. With JWT-backed providers, each new resolution requires an active session; revoking a device affects its next request, while an already cached account can remain usable within the current request.

An attacker can type a privileged page URL regardless of what the UI displays. Interceptor enforcement puts the check before handler work instead of relying on every rendering method to remember it. Public pages bypass the gate without an account lookup. A central role declaration is simpler than per-handler logic, but cannot express whether this particular invoice belongs to this particular account.

| Caller | Result for a protected page | Status |
| --- | --- | --- |
| Anonymous | Redirect to configured login path with original URI and query encoded as one parameter | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| Authenticated, insufficient role | 403 before the handler runs | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| Authenticated, sufficient role | Handler runs; object checks still required | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |

The default `AccountIdentity.hasRole` compares `this.getRole().ordinal() >= requiredRole.ordinal()`. Enum declaration order is a security decision: lower privilege must come first. Inserting a low-privilege value after an administrative value gives it that administrative privilege under this comparison; inserting a privileged value too early can both deny intended access and allow later, weaker values through its threshold. Use one coherent role enum throughout the application or override `hasRole` with an explicit policy. Cross-enum comparisons are not validated.

> [!IMPORTANT]
> A declared role is a threshold, not an ownership check. Enforce in the handler whether the resolved account may read the requested invoice, even when its role is sufficient to enter the page.

The default empty security provider authenticates nobody. Replacing `PageAccessResolver` transfers its enforcement contract to the application. Ordinary controllers that are not `Page` beans are outside this interceptor's protection.

## Login redirects

`PageAccessInterceptor` URL-encodes the original request URI and query into `redirectParameter` on `loginPath`. This preserves it as one query value. `UtilRedirect.isSafe` separately validates a proposed bounce target, and `sanitise` returns either that value or null.

Attackers send a login link whose bounce URL leads to a phishing site after successful authentication. Encoding the original parameter is not validation of a parameter later supplied to the login controller. Allowing arbitrary absolute URLs would create an open redirect; restricting bounce targets to local leading-slash paths avoids that destination freedom.

| Input form | Actual check | Status |
| --- | --- | --- |
| Null, blank, relative path or absolute URL | Must be nonblank and start with `/` | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| `//host/path` | Second character cannot be `/` | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| Slash followed by backslash, or leading backslash forms | Second character cannot be backslash; first must be slash | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| Opening brace | Any `{` is rejected | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| Literal CR or LF | Rejected anywhere | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |

The CRLF rejection prevents literal response-splitting characters from reaching a redirect through this utility. The brace check also avoids handing an opening template marker to redirect handling. It is a deliberately small path predicate, not a URL decoder or canonicalizer: it does not reject every embedded backslash, every control character or every percent-encoded representation. Validate the decoded value actually used by the redirect API and do not decode it again after validation.

The login controller must call the utility and choose a local fallback when it returns null. Pages does not implement login or sanitize arbitrary application redirects automatically. The configured login path and parameter name are trusted application configuration.

## Asset protection

`PageRegistryBuilder` turns `Page.getAssetPaths()` declarations into assets with the page's role and asset cache policy, without a separate role setting. `AssetProvider` can contribute shared `PageAsset` declarations with explicit roles. Exact declarations and terminal `/**` declarations have separate indexes. `StaticResourceFilter` canonicalizes the incoming application path, resolves its role through `PageRegistryData` and checks `PageAccessResolver` before the resource handler.

An attacker can request a script, document or download directly without loading its owning page. Page-only authorization would leave those URLs accessible. Tying declarations to the page avoids maintaining a second role list that can drift. It still requires declarations; the module does not inspect rendered HTML or discover directories.

| Lookup or outcome | Behavior | Status |
| --- | --- | --- |
| Exact protected match | Takes precedence | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| Otherwise protected `/**` match | Longest segment-aware prefix wins; includes base and descendants | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| Case | Role indexes and lookup use `Locale.ROOT` lowercase | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| Insufficient role or anonymous | 404 | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| Every gated asset | `private, no-store`, irrespective of declared cache policy | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |

Case-insensitive role matching covers case aliases even on a case-sensitive resource handler. For a given protected declaration it can cover more spellings than the resource server serves, rather than leaving a case variant unguarded. Prefix matching does not include `/assetsx` under `/assets`. Only protected declarations populate role indexes: a public exact asset does not exempt it from a protected wildcard, while an exact protected role can take precedence over a different wildcard role. Review overlaps deliberately.

A 404 avoids explicitly distinguishing a forbidden resource from an absent one as a 403 would. It is not a promise that existence cannot be inferred by timing or another endpoint. Successful authorization still delegates to the resource server, which decides whether a file exists. An undeclared alternate URL, a different download controller or an asset served directly by a proxy bypasses this mapping and needs equivalent protection.

## Navigation

`NavbarService` lists registered pages implementing `Navbar`, omits protected entries whose role check fails, and sorts entries by position. It uses the same resolver as page access, so a request can reuse its account resolution.

This avoids presenting links the viewer cannot use. An attacker does not need a navigation entry to invoke a route, so omitting an entry is presentation, not a security control. Making the navigation itself an access list would leave direct requests unchecked. `PageAccessInterceptor` remains the page enforcement point, and applications must not infer authorization from whether a link was rendered.

## Cache control

`PageCacheControlFilter` sets a policy before the chain on REQUEST and ERROR dispatches, using the original error URI when available. It checks canonical system path prefixes first, then registry exact and longest-prefix cache declarations, and otherwise uses private revalidation. Protected assets also receive the unconditional private no-store policy from `StaticResourceFilter`.

An attacker or unrelated user can receive private content if a shared cache stores a role-protected response and serves it without repeating authorization. Cache instructions must accompany redirects and errors too, not only successful templates. Central defaults reduce accidental shared storage without making every page declare a policy.

| Surface | Default policy | Status |
| --- | --- | --- |
| Page route, including public pages | `private, no-cache, must-revalidate` | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| Unknown path or unmatched dynamic route | `private, no-cache, must-revalidate` | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| Public declared asset | `public, max-age=300, must-revalidate` | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| Protected asset | `private, no-store`, forced by the gate | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| System prefixes, initially `/api` and `/stream` | `private, no-store, no-cache, must-revalidate` | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |

Private revalidation permits private storage with revalidation; it is not no-store. A blanket no-store policy would sacrifice useful private caching, while public caching can improve static resource delivery. Applications can override `getRouteCacheControlData` and asset policies to make those trade-offs explicitly. The page filter does not detect the role and reject a public route override. Never use a shared-cache policy for a protected page or personalized response.

The system list forces its no-store policy during filter selection with segment-aware prefixes, rather than raw string prefixes. Downstream handlers and infrastructure must preserve appropriate cache semantics; headers alone cannot control a misconfigured cache that ignores them. For particularly sensitive page data, choose private no-store explicitly.

## Canonical paths and registration

Common's `UtilRequestPath` collapses slash runs, resolves dot segments clamped at root, ensures a leading slash and removes non-root trailing slashes. Registry and asset lookups use canonical forms. `PageTrailingSlashFilter`, enabled by default, compares the raw request URI to its canonical form and returns 308 with the query preserved when they differ. Despite its name, it handles these other canonical differences as well.

Attackers seek a URL spelling for which routing reaches a resource but an access or cache rule selects another answer. A shared canonical form and a visible redirect reduce that divergence. A 308 preserves the HTTP method, unlike redirect choices that can turn writes into reads. Redirecting every difference adds a round trip but makes the canonical URL explicit; normalization only inside policy matching would leave multiple browser and cache keys in use.

`PageRegistryBuilder` rejects duplicate canonical page-route keys and duplicate canonical asset declarations during startup. Asset duplicate detection is case-insensitive and distinguishes exact from wildcard declarations. Failing startup avoids silently taking whichever declaration happened to register last. This is not a general proof that every possible overlapping route pattern is unambiguous: MVC also validates handler mappings, and applications must review intentional exact/wildcard overlaps.

Disabling the redirect does not disable the canonicalization used by policy lookups, but exposes more URL aliases. These helpers do not enforce filesystem containment or reconcile all proxy and servlet decoding differences. Keep infrastructure routing and application path interpretation consistent.

## Robots directives

`RobotsTagFilter` writes configured `X-Robots-Tag` on REQUEST and ERROR dispatches outside canonical segment-aware `robotsTagExcludedPathList` prefixes. The empty default disables the header.

A search engine can make an otherwise obscure URL easy to discover. Directives can ask cooperative crawlers not to index it, but an attacker can ignore them entirely. It is off by default because public indexing policy belongs to the application; a global noindex default would unexpectedly hide intended public pages. Use explicit configuration for staging or private indexing policy, while retaining real page and asset authorization. Exclusions affect the crawler header, not access permissions.

## Configuration that changes the security posture

Properties are under `foundation.pages` unless shown otherwise.

| Property or declaration | Default | Cost of a wrong value | Status |
| --- | --- | --- | --- |
| Page required role | Supplied by page; null is public | Missing role exposes the page | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| Role enum order / `hasRole` | Ordinal threshold | Misordered roles grant excessive privilege | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| Asset declarations | Empty on `Page` | Unlisted served URLs are not gated | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| Route cache override | Private revalidation | Public override can leak protected content | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| `login-path` | `/auth` | Wrong destination or redirect loop | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `redirect-parameter` | `redirect` | Login flow cannot recover intended target | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `trailing-slash-redirect` | `true` | Disabling retains duplicate URL forms | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `system-path-list` | `[/api, /stream]` | Missing prefixes lose forced no-store selection | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `robots-tag` | Empty, disabled | Indexing policy differs from intent | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `robots-tag-excluded-path-list` | Empty | Excluded paths omit crawler directives | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `PageAccessResolver` bean | `AccountPageAccessResolver` | Replacement can weaken identity or role checks | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |

## How far this goes

A page is protected by its declared role only. Ordinary controllers that are not `Page` beans need independent authorization. Asset gating is only as good as the declarations and covers requests passing through this application's filter, not alternate hosting paths. Navbar filtering and robots directives are not access controls. Cache overrides and infrastructure can undermine safe defaults.

Object-level authorization inside a handler, such as whether this account may read this invoice, remains the application's responsibility. Missing that check is a common way an attacker walks through an application like this: a sufficient page role must not permit selecting another account's object by changing an identifier. Authentication, password recovery, credential throttling and TLS remain with the application and neighbouring modules or infrastructure.

## Reporting a vulnerability

Use this repository's private security advisory reporting flow when enabled. Coordinate disclosure with the maintainers and keep exploit details out of public issues until a fix and disclosure plan are agreed.