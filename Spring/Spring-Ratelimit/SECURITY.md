# Spring-Ratelimit Security Model

![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) ![Infrastructure](https://img.shields.io/badge/infrastructure-0969DA?style=flat-square)

Spring-Ratelimit budgets annotated controller requests before execution to mitigate automated abuse and expensive endpoint exhaustion.

## Scope

For setup and complete property tables, use [Installation](INSTALLATION.md).

This describes `me.trae.foundation.spring:Spring-Ratelimit`, its annotation registry, interceptor and default fixed-window store. Client address trust comes from Common, authenticated accounts from Security, and page authorization from Pages. Neighbouring boundaries are documented in [Spring-Common](../Spring-Common/SECURITY.md), [Spring-Security](../Spring-Security/SECURITY.md), [Spring-Pages](../Spring-Pages/SECURITY.md).

Status legend: ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) module enforcement without configuration; ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) correct configuration required; ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) application implementation required; ![Infrastructure](https://img.shields.io/badge/infrastructure-0969DA?style=flat-square) external infrastructure required; ![Not covered](https://img.shields.io/badge/not%20covered-CF222E?style=flat-square) outside this module.

## Threat model

| Surface | Attacker attempts | Control | Residual risk and status |
| --- | --- | --- | --- |
| Annotated endpoints | Stuff credentials, enumerate accounts or invoices, scrape, or exhaust expensive handlers | [Accounting and scope](#accounting-and-scope) | Application must choose budgets and coverage ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| Request identity | Forge client headers or avoid an identifier to escape accounting | [Identity](#identity) | Proxy configuration and firewall ![Infrastructure](https://img.shields.io/badge/infrastructure-0969DA?style=flat-square) |
| Rejected requests | Keep invoking the handler or reuse cached rejection responses | [Rejection response](#rejection-response) | Retry timing is advisory ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| Limiter state | Create unlimited distinct identities to consume memory or displace budgets | [Bounded storage](#bounded-storage) | New legitimate identities can be refused ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |

## Accounting and scope

`RateLimitRegistry` maps reflected handler methods to `RateLimitData`. `RateLimitInterceptor` checks registered `HandlerMethod` requests before the controller runs and calls `RateLimitStore.tryConsume`. The default `MemoryRateLimitStore` keeps an attempt count and expiry per full key. A window begins on first consumption, admits up to the configured attempts and resets on the first request after expiry.

Attackers automate login guesses with stolen credential lists, enumerate accounts or resources, scrape data and repeatedly invoke handlers whose database or computation cost can deny service by itself. Restricting work before the handler reduces that opportunity. It does not count password failures specifically: allowed requests consume budget irrespective of eventual outcome.

| Declaration or scope | Key or budget effect | Status |
| --- | --- | --- |
| Method `@RateLimit` | Default reflected method key; nonblank name overrides; method settings replace shared settings | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| `@RateLimitShared(target = TYPE)` | Default class key, one controller budget; nonblank name overrides | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| `@RateLimitShared(target = METHOD)` | Default reflected method keys; a nonblank shared name makes them share | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| `IP_ADDRESS` | Append `:ip:<address>` | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| `ACCOUNT` | Append `:account:<UUID>` when resolved, otherwise IP | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |

The full store key is the registry key, a colon and that identifier. A nonblank annotation `name` replaces the reflected default key. Reusing a name shares its identity budget, even with shared METHOD target; keep scope and budget settings consistent across declarations that share a name. Account scope shares a budget across all sessions and networks of an authenticated account; IP scope shares it across users behind one address, including a NAT or office. Anonymous account-scoped requests fall back to IP and therefore do not acquire a per-target-account login budget merely by submitting a username.

Fixed windows offer bounded per-key state and a simple atomic consume operation. Sliding request histories can smooth boundary bursts but store or process more history; token buckets express continuous refill but require different timing semantics. Here a burst at the end of a window followed by another at the start of the next is permitted. Choose attempts and duration with that behavior in mind. Limits count requests, not their actual backend cost.

Unannotated handlers and non-handler resources pass through. Configure positive attempts and meaningful durations in application annotations. Registry construction at context refresh fails with IllegalStateException naming the method for attempts below 1 or a duration that converts to fewer than 1 millisecond, but supplies no universal safe budget. A limiter mitigates abuse and never grants authorization.

## Identity

`RateLimitInterceptor` obtains IP identities from `IpAddressResolver`, rather than directly using `getRemoteAddr()`. For account scope it first asks `SecurityAccountProvider.getAccountByRequest`, passing both request and response so authentication can refresh cookies. If neither a usable account UUID nor an IP resolves, it rejects with 429 instead of allowing an unlimited request or placing every unknown client into one shared fallback bucket.

Behind a reverse proxy, the socket peer is usually the proxy. Using it for every budget lets one attacker exhaust all clients' shared allowance. Forwarded client identities avoid that collapse but allow an attacker to choose identities if the source is not trusted. The resolver uses the first acceptable literal in configured headers, not a verified chain of proxy hops.

With a configured nonempty `foundation.ip-address.trusted-proxy-list`, untrusted peers' headers are ignored and their real peer address is validated. Trusted peers without acceptable forwarded data resolve empty, with no peer fallback. Production selects `proxy-header-list` and applies public-address checks; development selects `header-list` and accepts private literals. The resolver's public test rejects local classes, not every non-routable range, and returns original literal spelling rather than a canonical address string.

> [!WARNING]
> An empty trusted proxy list trusts forwarded headers from anyone. An internet-reachable origin then lets a client choose its own rate limit identity. Configure actual proxy CIDRs, require the proxy to overwrite the selected header, and restrict the origin firewall to those peers.

This boundary cannot be established merely by naming a familiar header. Application-supplied account providers must also return verified identities, not UUIDs from untrusted input. String-keyed IP limits depend on consistent address spelling from the trusted source. Broader network filtering and identity verification remain external to the store.

## Rejection response

`RateLimitInterceptor.reject` returns status 429, UTF-8 JSON with `message` and `retryAfter`, plus `Retry-After` and `Cache-Control: no-store`. The controller is not called. Remaining milliseconds are rounded up to seconds, with a minimum of one second. Missing identity takes the same path with that minimum; capacity refusal uses the configured duration as its retry interval.

Attackers must not be allowed to continue expensive work after a budget fails. Clients also need a clear signal to back off, while a cached 429 could deny later legitimate requests even after the budget recovers. A rejection response avoids redirects or ambiguous success codes and lets the client schedule retries; no-store discourages intermediaries from turning a transient refusal into a shared cached failure.

The response is not proof that the next request will succeed. Capacity may still be full, identity configuration may still be broken, and another request may consume the allowance. An attacker can ignore Retry-After. Network and reverse-proxy request handling already occurred before this MVC interceptor.

## Bounded storage

`MemoryRateLimitStore` caps its map at 250,000 windows. New-key admission and sweeping share a lock; attempt accounting for an existing key uses `ConcurrentHashMap.computeIfPresent` atomically per key. Each window stores expiry and count. A daemon sweep runs every 60 seconds, and the capacity path can request an additional sweep at most once per second. Expired tracked windows can reopen on consumption without waiting for the sweep.

An attacker cycling identities could otherwise grow memory without bound. At capacity, existing keys continue to be accounted and new keys are refused if sweeping cannot free space. Refusing new identities preserves protection for already tracked login budgets instead of evicting live counters and handing fresh attempts to an attacker. Unlimited admission would risk memory exhaustion; evicting old or least-used live keys would undermine budget enforcement. The chosen trade-off can deny a legitimate first-time caller during saturation.

> [!NOTE]
> Capacity refusal is deliberate fail-closed behavior for a limiter that may protect login. It preserves tracked budgets at the cost of availability for new keys; it is not evidence that each rejected caller exhausted an individual budget.

The cap bounds entry count, not total application memory. Every instance has independent counters, and restart loses them. Multiple instances can admit multiple independent budgets for the same identity. The module ships no distributed store and no cluster-wide cap.

An application can supply a `RateLimitStore` bean in place of the conditional default. A shared implementation must atomically check expiry, check allowance and consume one allowed attempt across all instances for the full key. It must preserve configured attempts and duration, return a nonpositive number when allowing or remaining milliseconds when rejecting, and avoid cleanup or TTLs that erase live budgets early. Independent read/check/write operations over-admit under concurrency. The application must choose outage behavior explicitly; the interface itself does not guarantee a fail-closed remote backend.

## Configuration that changes the security posture

There is no module-specific configuration-properties bean for budgets or capacity. Limits are application annotations; the store cap and sweep intervals below are implementation constants.

| Setting | Default | Cost of a wrong choice | Status |
| --- | --- | --- | --- |
| Handler annotation coverage | No annotation, no limit | Login or expensive routes remain unbudgeted | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| Annotation attempts, duration, unit and scope | Required | Excess allowance, denial of legitimate traffic or shared-user contention | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| Shared target | Required on `@RateLimitShared` | Unintended shared or separate budgets | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| `foundation.production` | `false` | Development IP validation and header selection | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `foundation.ip-address.trusted-proxy-list` | Empty | Client-controlled forwarded identities | ![Infrastructure](https://img.shields.io/badge/infrastructure-0969DA?style=flat-square) |
| `foundation.ip-address.proxy-header-list` | `[CF-Connecting-IP]` | Missing identity or untrusted assertions | ![Infrastructure](https://img.shields.io/badge/infrastructure-0969DA?style=flat-square) |
| `foundation.ip-address.header-list` | `[X-Forwarded-For, X-Real-IP, X-Client-IP, Forwarded]` | Broad development trust | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `RateLimitStore` bean | Process-local `MemoryRateLimitStore` | Cluster traffic multiplies budgets | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| Memory cap and sweep constants | 250,000; 60-second sweep; 1-second on-demand throttle | Saturation rejects new identities | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |

## How far this goes

A fixed window permits boundary bursts. IP scope does not stop a distributed attacker and can penalize unrelated users sharing a public address. Account scope helps authenticated identities but does not stop account creation or give anonymous login attempts a target-account budget. Counters are per instance unless the application supplies correct shared storage.

This is mitigation rather than authorization, credential validation, bot detection or network DDoS protection. It runs inside the servlet application after earlier infrastructure and filters have done work. Applications must select coverage and budgets, enforce authentication and object permissions, and secure their proxy identity boundary.

## Reporting a vulnerability

Use this repository's private security advisory reporting flow when enabled. Coordinate disclosure with the maintainers and keep exploit details out of public issues until a fix and disclosure plan are agreed.