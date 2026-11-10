<h1 align="center">Spring-Security</h1>

<p align="center">Ed25519 JWT authentication, security response headers, Content Security Policy and double-submit CSRF protection for Spring Boot.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet-0769AD?logo=jakartaee&logoColor=white"></a>
  <a href="https://www.rfc-editor.org/rfc/rfc8037"><img alt="JWT Ed25519" src="https://img.shields.io/badge/JWT-Ed25519-6DB33F?logo=jsonwebtokens&logoColor=white"></a>
</p>

Spring-Security provides cookie-backed Ed25519 JWT authentication with multiple device sessions, per-session refresh rotation and fingerprint binding. It also installs security headers, CSP and double-submit CSRF protection. It depends on Spring-Common; Pages and Ratelimit consume its authenticated account provider.

Follow [Installation](INSTALLATION.md) for complete setup, configuration and troubleshooting. Read the [security model](SECURITY.md) for threats, trade-offs and trust boundaries.

## Contents

<details>
<summary>On this page</summary>

- [Installation](INSTALLATION.md)
- [Security model](SECURITY.md)
- [At a glance](#at-a-glance)
- [Security headers](#security-headers)
- [Content Security Policy](#content-security-policy)
- [CSRF protection](#csrf-protection)
- [Excluding endpoints from CSRF](#excluding-endpoints-from-csrf)
- [JWT component layout](#jwt-component-layout)
- [Token model](#token-model)
- [JWT cookies](#jwt-cookies)
- [How a request is authenticated](#how-a-request-is-authenticated)
- [Refresh rotation and reuse detection](#refresh-rotation-and-reuse-detection)
- [Global invalidation](#global-invalidation)
- [Session model](#session-model)
- [Key seeds](#key-seeds)
- [Auto-configuration](#auto-configuration)
- [Account providers](#account-providers)
- [Wiring JWT end to end](#wiring-jwt-end-to-end)
- [JWT configuration reference](#jwt-configuration-reference)
- [Security model](#security-model)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Type | Responsibility |
| --- | --- |
| `JwtSecurity<Account>` | Composes authentication, validation, cookie and rotation operations |
| `JwtKeys` | Ed25519 seed derivation and public-key parsers |
| `JwtProperties` | Names, seeds, lifetimes, issuer, audience, skew, grace and session cap |
| `RefreshSession` | Immutable session UUID, current/previous refresh hashes, expiry and rotation time |
| `SecurityAccountProvider<Account>` | Request resolution and account-scoped session persistence |
| `SecurityHeadersFilter`, `ContentSecurityPolicyBuilder` | Browser policy headers and startup-built CSP |
| `CsrfFilter`, `CsrfTokenService`, `CsrfRegistry` | Double-submit write checks and explicit path exemptions |

## Security headers

The filter sets nonblank configured values before the chain on REQUEST, ERROR and ASYNC dispatches, at order 0.

| Header | Default |
| --- | --- |
| `X-Content-Type-Options` | `nosniff` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` |
| `X-Frame-Options` | `DENY` |
| `Content-Security-Policy` | Directive lists below |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload`, production only |

## Content Security Policy

Defaults permit self for default, base, form, script, style, font and connect sources, self plus data for images, and none for objects, frame ancestors, frames and children. These are CSP quoted keywords. Each directive is a separate list under `foundation.security.headers`. Lists replace defaults; null or empty omits a directive. `content-security-policy-enabled` defaults true. CSP is built once when the filter is constructed and limits injection impact without preventing injection.

## CSRF protection

`CsrfTokenService` issues 32 SecureRandom bytes in a URL-safe Base64 cookie. Page script echoes the cookie in `X-XSRF-TOKEN`; `CsrfFilter` compares nonblank values with `MessageDigest.isEqual`. It protects POST, PUT, PATCH and DELETE on initial REQUEST dispatches, at order 2. Keep other methods free of state changes. Missing tokens or failed source checks return 403.

The default cookie is `XSRF-TOKEN`, or `__Host-XSRF-TOKEN` in production. It is deliberately readable by script, root-path and without Domain. Production uses Secure and Strict; development uses non-Secure and Lax. The default lifetime is seven days. With `require-same-origin=true`, Origin hostname, falling back to Referer hostname, must equal the request server name ignoring case; scheme and port are not compared.

## Excluding endpoints from CSRF

`@CsrfExclude` applies to a controller type or method. It creates canonical path-pattern exemptions, independent of incoming HTTP method. Use only when another authentication mechanism, such as verified webhook signatures or an exclusively non-cookie bearer credential, makes the exemption appropriate. The annotation does not implement that authentication.

## JWT component layout

| Component | Public operations |
| --- | --- |
| `Generator` | No declared public methods |
| `Validator` | `validate(request, tokenType)` |
| `Authenticator` | `getAccountByRequest(request, response)`, `isAuthenticated(request, response)`, `hasRole(request, response, role)` |
| `Cookie` | `getToken(request, tokenType)`, `getFingerprint(request)`, boolean `apply(response, account)`, `remove(request, response, account)` |
| `Rotation` | `resolve(request)`, `revoke(account)`, `revokeSession(account, sessionId)`, `getSessionList(account)` |

`Validator` checks signed claims, required subject/identifier/session claim, token type and fingerprint. Account existence, UUID parsing and active session existence are checked by `Authenticator` and `Rotation`, not by `Validator` alone. Use the authenticator for application access decisions. JWT authentication is invoked where needed; it is not a filter.

## Token model

| Claim | Meaning |
| --- | --- |
| `jti` | Fresh token UUID; refresh identifiers are SHA-512 hashed in storage |
| `iss`, `aud` | Required configured issuer and audience |
| `sub` | Account UUID |
| `sid` | Session UUID, scoped to the account in storage |
| `token_type` | `access_token` or `refresh_token` |
| `fingerprint` | SHA-256 of a random 32-byte value represented as hex |
| `iat`, `nbf`, `exp` | JWT issue, not-before and expiry times |

Tokens are signed with EdDSA using separate Ed25519 access and refresh pairs. Parsers use public keys, issuer, audience and whole-second clock skew. Configure distinct seeds; equal seeds are not rejected.

## JWT cookies

| Base name | Contents | Default lifetime |
| --- | --- | --- |
| `ACCESS-TOKEN` | Access JWT | 15 minutes |
| `REFRESH-TOKEN` | Refresh JWT | Seven days |
| `FINGERPRINT` | Raw fingerprint | Seven days |

All are HttpOnly, Path=/ and without Domain, Base64-serialized by CookieHelper. Production adds `__Host-`, Secure and SameSite Strict; development uses plain names, non-Secure and Lax. Base64 is not encryption and consumes cookie capacity. The separate CSRF cookie remains script-readable.

## How a request is authenticated

1. Validate access token claims, type and fingerprint.
2. Resolve subject UUID through CommonAccountProvider and session UUID through `getRefreshSession(account, sessionId)`.
3. Require an unexpired stored session. Deletion rejects a still-signed, unexpired access token on its next resolution.
4. If access fails, attempt the refresh cookie against that account and session. A current hash attempts rotation; the immediately previous hash within grace returns the account without issuing cookies.

Pass the actual response through the provider because refresh may write cookies. Successful Authenticator resolution caches the account UUID on the request. A second call reloads that account without repeating token/session validation or rotation. Direct Rotation.resolve of the old refresh still reaches per-session grace. Pages caches both positive and negative resolutions per request.

## Refresh rotation and reuse detection

`RefreshSession` constructor order is session UUID, current hash, previous hash, expiry, rotation time. The current and previous hashes are SHA-512 of refresh identifiers. Rotation changes only that session and refreshes its fingerprint. Grace is per session and matches exactly the previous hash, not any unexpired token. Reuse outside that allowance deletes every session for the account.

`updateRefreshSession(account, refreshSession, expectedTokenHash)` must be a conditional write that reports whether a row changed. Null means insert only if no row exists for this account and session UUID. An unconditional implementation silently breaks reuse detection. Losing writers emit no cookies and do not mutate the account. The already validated request can still return its account after a failed write.

See the [session table and conditional upsert](INSTALLATION.md#storage-and-environment) for a complete PostgreSQL/JDBC example.

## Global invalidation

`Rotation.revoke(account)` deletes every session. `revokeSession(account, sessionId)` deletes exactly one. Access resolution checks session existence, so no token blocklist or waiting for access expiry is needed, provided reads are current.

Password change, forced logout and any other security event requiring account-wide invalidation must call `Rotation.revoke(account)` explicitly. Nothing automatically connects a password update to session deletion. Omitting the call leaves sessions alive after the change.

## Session model

Logout does not require a fingerprint cookie, including when that cookie is missing or mangled. It parses a server-signed refresh token with access-token fallback, checking signature, temporal claims, issuer, audience, type and required claims. It resolves the token subject through CommonAccountProvider, checks any supplied account against that subject, and deletes only that account/session pair. Passing null for the account still permits subject-based resolution. A mismatched supplied account cannot select another account for deletion. Without valid tokens, it only expires the three authentication cookies and deletes no session. Other devices remain active.

Logins create separate sessions and preserve other devices. `max-sessions` defaults to 10 and uses values below 1 for unlimited sessions. Each login prunes expired rows and evicts the least recently rotated live sessions if necessary. Set it to 1 for single-device behavior. Logout takes request, response and account, reads the session from a signature/type-checked refresh cookie with access fallback, deletes only that session and expires the three browser cookies. When the account argument is null, logout resolves the signed token subject to find the account before deleting its session, if possible.

Session lookup and cap management are application persistence operations. Concurrent logins can exceed the cap without external account-level coordination. The cap is not an atomic storage invariant.

### Request cache and concurrent refreshes

Successful Authenticator resolution stores the account UUID under `foundation.security.jwt.resolved-account`. Later Authenticator calls in that request call CommonAccountProvider.getAccountById again, without parsing cookies or checking the session again. Rotation therefore occurs at most once per request. Failed authentication stores nothing. A session revoked part way through a request remains visible to later calls in that request while the account still exists, and is rejected on the next request.

`rotation-grace`, default 5s, now accommodates genuinely concurrent HTTP requests carrying the immediately previous refresh token. It is not needed for repeated Authenticator calls within one request. Setting it to 0 disables previous-token acceptance between requests: a fresh refresh-only request replaying the previous token triggers reuse detection and revokes all account sessions. It does not disable the request cache or make a legitimate second resolution within the same request rotate or revoke. A still-valid access token can avoid refresh processing entirely.

### Session storage boundaries

`Rotation.getSessionList(account)` returns RefreshSession objects containing stored current and previous token hashes. Map them to an application response DTO containing only intended device metadata, such as sessionId, expireAt and rotatedAt, before exposing a devices endpoint. Do not serialize the returned objects directly.

Session eviction and insertion are not atomic. Two simultaneous logins at the cap can briefly leave one session over the limit; the library does not repair that excess immediately. Coordinate by account in application storage if the cap must be strict. Failed insertion can also follow an eviction.

Every provider implementation must scope both `getRefreshSession(account, sessionId)` and `deleteRefreshSession(account, sessionId)` by account UUID as well as session UUID. Knowing another account's session UUID must not confer access or deletion rights.

## Key seeds

Each Base64 seed must decode to 32 bytes. Stable distinct seeds preserve verification across restarts and instances. Missing development seeds log a warning and generate ephemeral pairs; production throws when JwtKeys is constructed. Successful construction clears the properties bean and wipes decoded arrays, but Spring Environment retains configured strings. Use environment variables and close actuator configuration endpoints. No `kid` or old-key ring exists; replacing a seed invalidates signatures using it.

## Auto-configuration

`SecurityAutoConfiguration` runs after Common in servlet applications. It binds header, CSRF and JWT properties; registers the conditional empty SecurityAccountProvider and CsrfTokenService; registers CsrfRegistry, its context-refresh listener and the two filter registrations. No module enable annotation or scan is needed. The application declares `JwtSecurity<Account>` because of its account type parameter.

## Account providers

The Common provider maps UUIDs to accounts. The Security provider has six methods: request authentication plus session lookup, list, conditional write, delete-one and delete-all. Empty request resolution returns no account; the empty provider's session operations throw UnsupportedOperationException.

<a id="creating-a-security-account-provider"></a>

Use the [complete provider and ObjectProvider cycle break](INSTALLATION.md#application-contracts). Register each provider once so Pages and Ratelimit share it.

## Wiring JWT end to end

Follow [Installation](INSTALLATION.md#minimal-working-setup) for complete compiling files, session storage, bean wiring and HTTP verification. It includes the `ObjectProvider<JwtSecurity<Account>>` cycle break and credential boundary. Never accept an account identifier as proof of authentication.

## JWT configuration reference

The [settings tables](INSTALLATION.md#settings) list every property and initializer default. JWT properties use prefix `foundation.security.jwt`.

| Setting | Default | Meaning |
| --- | --- | --- |
| Issuer / audience | `foundation` / `foundation` | Issued and required claims |
| Access / refresh / fingerprint cookie names | `ACCESS-TOKEN` / `REFRESH-TOKEN` / `FINGERPRINT` | Base names before environment prefix |
| Access / refresh key seeds | Unset | Base64 of 32 bytes each |
| Access / refresh durations | `15m` / `7d` | JWT and cookie lifetimes |
| Clock skew / rotation grace | `5s` / `5s` | Parser tolerance / concurrent-request previous-token allowance |
| Maximum sessions | `10` | Zero or negative means unlimited; expired pruning still runs |

## Security model

Read [SECURITY.md](SECURITY.md) for design reasoning, residual risks and vulnerability reporting.

## Installation and build

Use `me.trae.foundation.spring:Spring-Security:1.0-SNAPSHOT`. Install from the repository root:

```shell
mvn -pl Spring/Spring-Security -am install
```

Return to the [Spring guide](../README.md) or [Foundation](../../README.md).