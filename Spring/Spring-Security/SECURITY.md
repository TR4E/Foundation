# Spring-Security Security Model

![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square)

Spring-Security hardens browser responses, checks cookie-backed writes and provides application-invoked JWT authentication with rotation and revocation.

## Scope

This describes `me.trae.foundation.spring:Spring-Security`: response headers, CSP, CSRF and the `JwtSecurity` component. It does not provide credential storage, page authorization or request throttling. Neighbouring boundaries are documented in [Spring-Common](../Spring-Common/SECURITY.md), [Spring-Ratelimit](../Spring-Ratelimit/SECURITY.md), [Spring-Pages](../Spring-Pages/SECURITY.md).

Status legend: ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) module enforcement without configuration; ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) correct configuration required; ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) application implementation required; ![Infrastructure](https://img.shields.io/badge/infrastructure-0969DA?style=flat-square) external infrastructure required; ![Not covered](https://img.shields.io/badge/not%20covered-CF222E?style=flat-square) outside this module.

## Threat model

| Surface | Attacker attempts | Control | Residual risk and status |
| --- | --- | --- | --- |
| Browser responses | Frame a victim page, sniff content, or leak request context | [Response headers](#response-headers) | Browser enforcement and HTTPS deployment ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| Injected content | Load attacker scripts or send data to an external endpoint | [Content security policy](#content-security-policy) | Does not prevent injection ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| Cookie-backed writes | Submit a write from an attacker-controlled page | [CSRF](#csrf) | Same-host script can act as the user ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| Authentication tokens | Forge tokens, replay refreshes or race rotation | [JWT](#jwt) | Atomic application persistence required ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |

## Response headers

`SecurityHeadersFilter` sets nonblank configured headers before invoking the chain. `SecurityAutoConfiguration` registers it first, for REQUEST, ERROR and ASYNC; its `OncePerRequestFilter` overrides opt into error and async dispatches. Errors and resumed requests need the same policy as normal responses, since an attacker can deliberately trigger them. Restricting headers to successful handlers would leave those paths dependent on unrelated error handling.

| Header | Default and what an attacker attempts without it | Status |
| --- | --- | --- |
| `X-Content-Type-Options` | `nosniff`: discourage interpreting supplied content as executable content of another MIME type | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `Referrer-Policy` | `strict-origin-when-cross-origin`: limit cross-origin leakage of path and query details | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()`: deny those browser features to page code | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `X-Frame-Options` | `DENY`: prevent framing a victim page to trick clicks | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `Content-Security-Policy` | Built from directive lists, described below | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload`, production only: resist later HTTP downgrades | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |

Central filter enforcement covers responses that never reach a controller, unlike per-handler annotations. These are browser instructions, not request authorization, and downstream code or infrastructure can overwrite them. Correct content types and output handling remain necessary. Empty or blank header properties suppress that header.

HSTS is gated on `FoundationProperties.production` because a browser retains it across requests; enabling it on a development HTTPS host can pin that host and its subdomains to HTTPS. A learned HSTS policy resists network attempts to downgrade later connections, but the filter does not terminate TLS or independently secure the first visit. The `preload` token is not proof that a hostname is on a browser preload list. Production deployment must support HTTPS for the affected hostnames.

## Content security policy

`ContentSecurityPolicyBuilder` builds directive text from `SecurityHeadersProperties`. `SecurityHeadersFilter` assembles it once at construction, normally during startup, when `contentSecurityPolicyEnabled` is true. It never reflects request parameters into the policy.

| Directive | Default source list | Status |
| --- | --- | --- |
| `default-src`, `base-uri`, `form-action` | `'self'` each | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `script-src`, `style-src`, `font-src`, `connect-src` | `'self'` each | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `img-src` | `'self' data:` | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `object-src`, `frame-ancestors`, `frame-src`, `child-src` | `'none'` each | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |

An attacker who injects markup tries to run external or inline script, change the base URL, embed active objects, or exfiltrate to another origin. These defaults restrict those browser actions and framing. They do not grant inline script or inline styles. CSP limits the damage of injected script rather than preventing injection, and same-origin executable content can still be dangerous.

Per-directive lists let an application add one required resource origin without restating and accidentally weakening the entire policy. A single raw policy string would permit more directives but make every customization a complete policy review. Building once also avoids per-request assembly and policy changes driven by request input. A configured list replaces that directive's list; include the sources still needed. Null or empty lists omit directives, invoking CSP fallback semantics where applicable, rather than declaring `'none'`.

Application templates still need escaping, safe DOM handling and careful same-origin content hosting. Widening `script-src`, disabling CSP or adding permissive sources changes the protection substantially. This implementation supplies no per-request nonce generator or reporting endpoint.

## CSRF

`CsrfTokenService` issues 32 bytes from `SecureRandom`, encoded URL-safe Base64 without padding. `CsrfFilter` uses double-submit: a nonblank cookie token must equal the nonblank configured request header, compared as UTF-8 bytes through `MessageDigest.isEqual`. Unprotected methods issue a token if absent. Protected methods reject with 403 when required source validation or token comparison fails.

An attacker submits a form from another site and relies on the browser attaching the victim's authentication cookies. That site cannot normally read the victim's CSRF cookie to echo its unpredictable value in the required custom header. Random independent tokens avoid predictable account-derived values; double-submit avoids a server-side CSRF session store. A server-stored synchronizer token would bind state on the server but require that storage and its lifecycle.

| Detail | Design choice and trade-off | Status |
| --- | --- | --- |
| Cookie | `Path=/`, no Domain; caller-configured max-age | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| Production | `__Host-XSRF-TOKEN`, Secure, SameSite Strict by default | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| Development | `XSRF-TOKEN`, non-Secure, SameSite Lax | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| HttpOnly | Deliberately absent so script can read and echo the token | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| Source check | Origin hostname, falling back to Referer hostname | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| Methods | POST, PUT, PATCH, DELETE | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| Exemptions | Canonical path patterns from `@CsrfExclude` | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |

Plain double-submit is vulnerable to an attacker planting a chosen cookie through a hostile subdomain. In production the browser-enforced `__Host-` prefix prevents that subdomain from writing a Domain cookie the parent origin will read. The prefix is backed by Secure, root Path and no Domain. Development cookies give up this protection. Making the CSRF cookie HttpOnly would break the required read-and-echo step; authentication cookies remain HttpOnly instead.

By default, `requireSameOrigin` requires Origin, falling back to Referer only if Origin is absent or blank. Missing, malformed or foreign-host values fail. Despite the property's name, comparison is hostname-only, case-insensitively against `getServerName()`. Behind a proxy, the scheme and port seen by the origin server may not be those used by the browser; comparing them without first handling trusted forwarded metadata would reject legitimate traffic. This trades strict browser-origin equality for proxy compatibility. Same-host different schemes or ports pass this source test. Host and forwarding configuration therefore remain part of the trust boundary; the token check still applies.

Only POST, PUT, PATCH and DELETE are protected because they conventionally mutate state. Applying token requirements to reads would impede navigation and token bootstrapping. The application must keep GET, HEAD, OPTIONS and any other unprotected methods free of state-changing behavior.

Exclusion is a blocklist of exemptions, so a forgotten endpoint is over-protected rather than open. Signed webhooks and APIs authenticated exclusively by a non-cookie Authorization header are reasonable `@CsrfExclude` candidates, provided the application verifies that alternate authentication. The registry records path patterns, not method-specific exemptions: annotating one handler can exempt other protected methods at the same path. Narrow declarations matter. CSRF does not stop injected same-origin script, permissive cross-origin access that exposes the token, or an incorrectly exempted cookie-authenticated endpoint.

## JWT

`JwtSecurity` is an explicitly constructed generic component, not an authentication filter or an automatically created JWT bean. Its `Authenticator` first validates an access cookie and resolves the account, then tries refresh resolution if necessary. The application's `SecurityAccountProvider.getAccountByRequest` can delegate to it where authentication is needed. A public route need not perform authentication work. The default empty provider returns no account; adding the dependency alone does not authenticate an application.

### Signing and key material ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square)

`Generator` signs with EdDSA using Ed25519 private keys selected by token type. `JwtKeys` gives each parser the corresponding public key and requires issuer and audience; validation also requires subject, identifier, `sid` and the expected `token_type` claim. Parsing enforces signed-token validity and temporal claims with configured clock skew.

An attacker tries to forge a token or feed an access token into refresh processing. Asymmetric signing avoids a shared verification secret that a verifier could leak or use to mint tokens. A public Ed25519 key cannot be coerced into verifying an HMAC token as though its bytes were a shared HMAC secret. Separate access and refresh pairs reduce the consequences of one signing key being exposed, and explicit type claims add a second separation check.

This is not a claim that `JwtSecurity` holds only public material: it signs and holds private keys too. Configure distinct seeds for the two types. The code constructs separate pair objects but does not reject equal seeds, which derive equal key material and defeat cryptographic separation.

`JwtKeys` deterministically derives each pair from a Base64 seed decoding to exactly 32 bytes. This lets all instances and restarts verify the same cookies, unlike generating fresh keys each time. In production a missing or blank seed throws when `JwtKeys` is constructed; malformed or wrong-length configured seeds fail in either environment. In development missing seeds log a warning and generate ephemeral pairs: a restart invalidates existing cookies, and independently started instances cannot verify one another's cookies.

On successful construction seeds are nulled on the properties bean, and decoded seed arrays are zeroed after derivation, including wrong-length decoded input. These are residue reductions, not guaranteed erasure of all secret copies: Spring's `Environment` retains its own configured values, Java strings cannot be wiped, and private key material remains necessary for signing. Seeds belong in environment variables or equivalent secret injection with actuator environment and configuration endpoints closed to untrusted users. Clearing the bean does not secure those endpoints.

### Fingerprint binding

Issuance generates a fresh random 32-byte value as hexadecimal in a third HttpOnly cookie. Both tokens carry its SHA-256 hash in the `fingerprint` claim. `Validator` rejects absent or blank claims, missing cookies and mismatched hashes using constant-time hash comparison.

An attacker who obtains a token string, including through an application leak or injected script that finds another exposed copy, cannot replay it outside the browser without the fingerprint cookie. HttpOnly prevents direct JavaScript reads of these cookies, and hashing the fingerprint inside the tokens avoids revealing the raw binding secret there. Accepting older tokens without a fingerprint would create a bypass, so the validator rejects them rather than treating the claim as optional.

Binding to a random cookie avoids fragile IP or user-agent binding, which breaks when networks or clients change and is often guessable. It requires another browser secret instead. It does not stop script acting as the user inside the victim's page, browser compromise or theft of both token and fingerprint. The browser still attaches cookies to requests made by same-origin injected script.

### Session existence and revocation ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square)

Every generated token carries a `sid` claim. `Validator.parse` rejects missing session claims; authentication parses the account and session UUIDs and requires `getRefreshSession(account, sessionId)` to return an unexpired row. An attacker replaying a still-signed access token after session deletion is rejected on the next account resolution rather than at token expiry. The provider must scope every lookup to both account and session.

Session rows replace an account-wide time threshold with explicit device state. That gives precise device logout and immediate revocation on current reads, but adds a session existence lookup to access authentication and makes availability depend on that store. Signature-only access checks would be cheaper but retain access after logout until expiry. A per-token blocklist would require more entries than one row per active session.

| Event | Session effect | Status |
| --- | --- | --- |
| Login through boolean `Cookie.apply` | Prune expired sessions, make a slot, create a new UUID session; preserve other devices | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| `Cookie.remove(request, response, account)` | Delete the signed refresh cookie's session, with access-cookie fallback; expire three cookies | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| `Rotation.revokeSession(account, sessionId)` | Delete one session | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| `Rotation.revoke(account)` or detected refresh reuse | Delete every session for that account | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| Password change or forced logout | No automatic effect; application must call `Rotation.revoke(account)` explicitly | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |

`Rotation.getSessionList(account)` exposes the provider's list. `maxSessions` defaults to 10 and uses values below 1 for unlimited sessions. Each login prunes expired rows and evicts the least recently rotated live sessions to free a slot. A cap of 1 reproduces single-device behavior; the default permits multiple devices. The cap limits stored device state but makes a new login capable of evicting an older device. Unlimited sessions would avoid eviction at the cost of unbounded account state.

The cap's list, delete and insert sequence is not atomic. Applications requiring a strict cap under concurrent logins must coordinate by account. Failed login insertion can occur after pruning or eviction; deleted sessions are not restored. Current storage reads are required across instances: stale caches delay revocation, and a per-request cached account is not revalidated mid-request. Logout resolves the signed subject through CommonAccountProvider and rejects any mismatched supplied account. With no tokens it only expires cookies. Provider lookups and deletions must always include account and session UUIDs. It deliberately parses signatures/type/session claims without requiring fingerprint validation, so losing the fingerprint does not prevent server-side session deletion. Ordinary authentication still requires fingerprint binding.

### Refresh rotation and the persistence contract ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square)

`Rotation` checks the current token's SHA-512 identifier hash against the stored `RefreshSession` for that account and session UUID. Its constructor order is session UUID, current hash, previous hash, expiry and rotation time. The current and immediately previous hashes are scoped to that session. A normal refresh replaces the current hash, retains it as the previous hash, and issues new access, refresh and fingerprint cookies only after the conditional write succeeds.

An attacker replays a stolen refresh token after the user has rotated. A mismatched identifier outside grace triggers global revocation. The configured grace window admits only the immediately previous hash for a short time after rotation and marks the request so it does not rotate or issue cookies again. This accommodates genuinely concurrent HTTP requests, rather than repeat Authenticator calls within one request without treating every unexpired token as eligible: a token stolen days earlier cannot ride in during someone else's legitimate rotation. Strict single-use with no grace would revoke users during ordinary concurrency; a general time-based grace would admit unrelated stolen tokens.

The intended model is one normal rotation per current token, with this narrow previous-token exception. The application must implement `updateRefreshSession(account, refreshSession, expectedTokenHash)` as an atomic compare-and-set and return whether it won. An illustrative conditional SQL shape is:

```sql
UPDATE account_session
SET previous_token_hash = :expected_hash,
    token_hash = :new_hash,
    expire_at = :expire_at,
    rotated_at = :rotated_at
WHERE account_id = :account_id AND session_id = :session_id
  AND (token_hash = :expected_hash
       OR (token_hash IS NULL AND :expected_hash IS NULL));
```

Success means exactly one affected row. Null expected hash means no row may exist for that account and session UUID. Use an atomic insert-if-absent for that case; an existing row must not be overwritten. See the [complete conditional upsert](INSTALLATION.md#storage-and-environment). Do not implement it as independent read, comparison and unconditional write operations.

> [!IMPORTANT]
> `updateRefreshSession(account, refreshSession, expectedTokenHash)` must apply the write only when the stored hash still matches. The module cannot enforce this. An unconditional implementation reinstates the rotation race, can hand a browser a refresh token the server no longer knows, and leaves reuse detection unsound with no visible symptom.

On a lost conditional write, `Rotation.store` emits no new cookies and never assigns session state to the account. That prevents the loser from overwriting the winner's browser cookies. Correctness still depends on atomic per-session persistence and current record reads; an interface cannot provide those guarantees itself.

Successful Authenticator calls cache the account UUID for the request and repeat only the Common account lookup. The session is not rechecked within that request. Zero rotation grace is safe for those repeat calls but rejects an immediately previous refresh token presented on a fresh refresh-only request, triggering reuse revocation. Unauthenticated calls cache nothing.

`Rotation.getSessionList` returns stored token hashes as part of RefreshSession. Map the list to a response DTO before exposing a devices endpoint; raw session records are not a safe response model.

### Cookie lifetime and enforcement boundary

All three authentication cookies use `CookieHelper` with HttpOnly and Base64 serialization. Production adds Secure, Strict and `__Host-`; development uses non-Secure, Lax and plain names. Both environments use root Path, no Domain and configured max-age. The fingerprint lasts as long as the refresh cookie. Base64 adds size without encryption.

The default access lifetime is 15 minutes, which bounds acceptance of a stolen access token plus fingerprint unless revocation happens sooner, with configured clock skew extending time tolerance. A stolen refresh token plus fingerprint may sustain access until expiry, rotation or revocation. Short access lifetimes alone do not neutralize that pair. There is no mandatory authentication filter: every protected application path must actually invoke account resolution and enforce its authorization decision.

## Configuration that changes the security posture

Property names below are relative to the stated prefix. Lists are replacements, not additions.

| Property | Default | Cost of a wrong value | Status |
| --- | --- | --- | --- |
| `foundation.production` | `false` | Cookie downgrade, no HSTS, ephemeral seed fallback | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `foundation.security.headers.content-type-options` | `nosniff` | MIME sniffing protection removed | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `headers.referrer-policy` under `foundation.security` | `strict-origin-when-cross-origin` | URL context may leak cross-origin | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `headers.permissions-policy` under `foundation.security` | `camera=(), microphone=(), geolocation=()` | Unneeded device access enabled | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `headers.frame-options` under `foundation.security` | `DENY` | Framing protection weakened | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `headers.strict-transport-security` under `foundation.security` | `max-age=31536000; includeSubDomains; preload` | Downgrade exposure or unsuitable HTTPS pinning | ![Infrastructure](https://img.shields.io/badge/infrastructure-0969DA?style=flat-square) |
| `headers.content-security-policy-enabled` under `foundation.security` | `true` | Disabling removes CSP | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `headers.default-src`, `base-uri`, `form-action`, `script-src`, `style-src`, `font-src`, `connect-src` under `foundation.security` | Each `["'self'"]` | Broad sources expand injection impact | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `headers.img-src` under `foundation.security` | `["'self'", "data:"]` | Broad image destinations allow data leakage | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `headers.object-src`, `frame-ancestors`, `frame-src`, `child-src` under `foundation.security` | Each `["'none'"]` | Objects or embedding enabled | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `foundation.security.csrf.require-same-origin` | `true` | Disabling removes source-host check | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `csrf.cookie-name`, `header-name` under `foundation.security` | `XSRF-TOKEN`, `X-XSRF-TOKEN` | Client mismatch rejects writes | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `csrf.cookie-duration` under `foundation.security` | 7 days | Token availability and persistence change | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `foundation.security.jwt.access-token-key-seed`, `refresh-token-key-seed` | Unset | Production construction fails; development keys ephemeral; equal seeds remove key separation | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| `jwt.issuer`, `audience` under `foundation.security` | `foundation`, `foundation` | Mismatch rejects tokens; reuse widens token domain | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `jwt.access-token-cookie-name`, `refresh-token-cookie-name`, `fingerprint-cookie-name` under `foundation.security` | `ACCESS-TOKEN`, `REFRESH-TOKEN`, `FINGERPRINT` | Collisions or mismatches break binding and resolution | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `jwt.access-token-duration`, `refresh-token-duration` under `foundation.security` | 15 minutes, 7 days | Longer theft window or frequent expiry | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `jwt.clock-skew` under `foundation.security` | 5 seconds | Larger temporal tolerance accepts tokens longer | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `jwt.max-sessions` under `foundation.security` | 10 | Below 1 is unlimited; concurrent capped logins need account-level coordination | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| `jwt.rotation-grace` under `foundation.security` | 5 seconds | Longer previous-token replay allowance | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |

## How far this goes

| Boundary | Application responsibility | Status |
| --- | --- | --- |
| Credential abuse | Put login behind Spring-Ratelimit; this module does no credential throttling | ![Not covered](https://img.shields.io/badge/not%20covered-CF222E?style=flat-square) |
| Key rollover | No `kid` header or old-key verification ring; changing a seed invalidates tokens signed with it | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| Session topology | Multiple sessions per account, default cap 10, below 1 unlimited; logout targets one device, explicit revoke and detected reuse target all | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| Persistence | Atomic per-session writes, current account and session reads, explicit revocation on security events | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| Credentials and recovery | Password hashing, verification, account storage and reset flows | ![Not covered](https://img.shields.io/badge/not%20covered-CF222E?style=flat-square) |
| Transport | HTTPS termination, trusted forwarded metadata and protected secret delivery | ![Infrastructure](https://img.shields.io/badge/infrastructure-0969DA?style=flat-square) |
| Authorization | Resolve accounts and check route and object permissions where needed | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |

A password reset flow with a weak token bypasses the authentication design by handing the attacker control of the account. Injected script can operate inside the page despite CSRF and fingerprint binding. Header policies, cookie controls and token validation do not replace secure application inputs, recovery flows or authorization.

## Reporting a vulnerability

Use this repository's private security advisory reporting flow when enabled. Coordinate disclosure with the maintainers and keep exploit details out of public issues until a fix and disclosure plan are agreed.