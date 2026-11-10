# Spring-Common Security Model

![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) ![Infrastructure](https://img.shields.io/badge/infrastructure-0969DA?style=flat-square)

Spring-Common supplies the environment, cookie, client-address and canonical-path primitives used by the other Spring modules.

## Scope

For setup and complete property tables, use [Installation](INSTALLATION.md).

This describes the `me.trae.foundation.spring:Spring-Common` artifact, including `FoundationProperties`, `CookieHelper`, `IpAddressResolver` and `UtilRequestPath`. Authentication, request budgets and page authorization are enforced by its consumers. Neighbouring boundaries are documented in [Spring-Security](../Spring-Security/SECURITY.md), [Spring-Ratelimit](../Spring-Ratelimit/SECURITY.md), [Spring-Pages](../Spring-Pages/SECURITY.md).

Status legend: ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) module enforcement without configuration; ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) correct configuration required; ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) application implementation required; ![Infrastructure](https://img.shields.io/badge/infrastructure-0969DA?style=flat-square) external infrastructure required; ![Not covered](https://img.shields.io/badge/not%20covered-CF222E?style=flat-square) outside this module.

## Threat model

| Surface | Attacker attempts | Control | Residual risk and status |
| --- | --- | --- | --- |
| Environment policy | Exploit development behavior on an internet deployment | [Production flag](#production-flag) | Deployment must set it correctly ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| Cookies | Read browser secrets or plant a parent cookie from a subdomain | [Cookie handling](#cookie-handling) | Caller chooses HttpOnly; HTTPS required ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| Client identity | Forge forwarding headers to cycle request budgets | [Client address resolution](#client-address-resolution) | Proxy CIDRs and origin firewall ![Infrastructure](https://img.shields.io/badge/infrastructure-0969DA?style=flat-square) |
| Path matching | Spell one resource differently to obtain a different policy | [Path canonicalisation](#path-canonicalisation) | Every consumer must use the same path ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |

## Production flag

`FoundationProperties.production`, bound as `foundation.production`, defaults to `false`. Consumers use it as one environment switch rather than making independent guesses about deployment.

| Consumer | `true` | `false` | Status |
| --- | --- | --- | --- |
| `CookieHelper` and CSRF service | Secure, Strict, `__Host-` names | Non-Secure, Lax, plain names | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| Security headers | HSTS enabled if nonblank | No HSTS | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| IP resolver | Production header list and public-address checks | Development header list, private literals accepted | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `JwtKeys` construction | Missing key seed throws | Missing seed generates ephemeral pair | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |

An attacker benefits from a deployment left in development mode: transport-bound cookies and subdomain injection defenses disappear, forwarded identity rules relax, and missing JWT seeds can silently become restart-local keys. A single flag makes these decisions consistent; separate switches would offer flexibility but allow contradictory environments. The cost is that a single wrong value affects every consumer.

> [!WARNING]
> A wrong production flag in either direction is a security event. `false` in production silently downgrades these controls. `true` on a development HTTPS hostname sends HSTS, which a browser can retain and enforce on later visits, including subdomains. Browsers do not learn HSTS from ordinary HTTP.

The flag neither detects deployment nor supplies TLS. The application must set it explicitly and review the resulting browser policy. JWT startup refusal occurs when the application constructs `JwtSecurity` and therefore `JwtKeys`, not merely when this module is loaded.

## Cookie handling

`CookieHelper.setCookie` builds a `ResponseCookie` and adds a separate `Set-Cookie` header; deletion uses the same naming and attributes with an empty value and zero max-age. Lifetime and whether to serialize are caller choices.

| Attribute or operation | Mechanism and purpose | Status |
| --- | --- | --- |
| `HttpOnly` | Set to the caller's boolean; prevents direct script reads when true | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| `Secure` | Production only; restricts browser transmission to secure transport | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `SameSite` | Strict in production, Lax in development | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `Path=/`, no `Domain` | Origin-host cookie with a root path | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |
| `__Host-` name | Production prefix, with Secure, root path and no Domain | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `Max-Age` | Caller-supplied duration; zero on deletion | ![Application](https://img.shields.io/badge/application-8957E5?style=flat-square) |
| Optional Base64 | UTF-8 serialization through `UtilBase64` | ![Enforced](https://img.shields.io/badge/enforced-2EA043?style=flat-square) |

Attackers try to steal readable cookies with injected script, cause cross-site requests to carry credentials, or plant a parent-domain cookie from a compromised subdomain. Strict reduces cross-site cookie attachment, including legitimate navigation cases. Lax makes local cross-site top-level navigation easier while still restricting many cross-site submissions; it is a development convenience, not equivalent protection.

The browser enforces `__Host-` requirements. A hostile subdomain cannot set a Domain-scoped cookie with that prefix for the parent origin, which closes the subdomain cookie injection avenue for consumers such as double-submit CSRF. Merely omitting Domain on the parent's cookie would not stop a subdomain from planting a separate Domain cookie with the same ordinary name. The prefix depends on browser support and secure transport. It is not a server-side signature.

Optional Base64 represents arbitrary string data without providing confidentiality or integrity. It expands UTF-8 bytes to roughly four characters for every three bytes, leaving less space under the browser's per-cookie size ceiling, commonly around 4 KiB including naming and attribute overhead. The helper enforces no size limit. Keep payloads small, particularly serialized JWTs, and do not treat encoding as validation.

`getCookie` reads the environment-specific name and returns null for an absent cookie or a value whose deserialization throws, rather than allowing malformed input to fail the request. It does not authenticate a successfully decoded value. Callers must validate content and choose HttpOnly appropriately; CSRF deliberately needs a readable cookie while authentication cookies do not.

## Client address resolution

`IpAddressResolver` snapshots configured header lists and parsed `IpAddressRange` peer ranges at construction. With a nonempty `trustedProxyList`, it checks `getRemoteAddr()` first: an untrusted peer's forwarded headers are ignored and its acceptable remote address is used. A trusted peer supplies the first acceptable comma-separated literal from the configured headers. If none is found, resolution is empty, without falling back to the proxy address. Invalid CIDR configuration fails construction.

A header identity is only as trustworthy as the peer that sent it. An attacker who can supply a plausible address can claim a new identity for every login attempt. Raw `getRemoteAddr()` avoids that forgery for direct callers but groups all clients behind a reverse proxy into one budget. Explicit CIDRs retain proxy-aware identity while identifying who may assert it. This implementation does not validate a forwarded hop chain or parse structured RFC `Forwarded` syntax; entries must be IP literals.

> [!WARNING]
> The default empty `foundation.ip-address.trusted-proxy-list` trusts headers from anyone. With an internet-reachable origin, a client can choose its own rate limit identity. Configure actual proxy CIDRs and an origin firewall that matches, and require the proxy to overwrite the selected client header.

Production selects `proxyHeaderList` and requires an address to pass the public-address test. It rejects unspecified, loopback, site-local, link-local, multicast and IPv6 unique-local addresses. IPv4-mapped IPv6 is unmapped before those tests so a mapped loopback cannot evade them. This is not an exhaustive routability registry: carrier-grade NAT addresses, for example, are not rejected. Development accepts every parseable literal.

Unmapping is for classification, not output normalization: the resolver returns the supplied literal spelling. This matters for string-keyed budgets, especially if a trusted header source accepts multiple spellings of one IPv6 address. The proxy must provide consistent identity data. Public-address validation does not prove the assertion came from a proxy.

Empty resolution is explicit through `Optional.empty()`. Spring-Ratelimit returns 429 when it cannot obtain either an account identifier or an acceptable IP, rather than granting unlimited requests. Account scope can still proceed with an authenticated UUID. Other consumers must handle absence deliberately.

## Path canonicalisation

`UtilRequestPath.getCanonicalPath` takes Spring's application-relative path and passes it through `canonicalise`. That function supplies a leading slash, collapses repeated slashes, removes `.` segments, resolves `..` by removing the preceding segment while clamping at root, and removes a trailing slash except for root. Null or blank becomes `/`. Case and dots inside ordinary segment names remain unchanged.

Attackers try `/assets/../private` or slash aliases to make one component authorize a different resource from the one another serves. CSRF exclusions, asset gating and cache policy all match paths, so two spellings of one path can otherwise produce two security answers. Shared canonicalisation removes those known differences rather than leaving each consumer to implement partial cleanup.

`matchesPrefix` checks segment boundaries: `/assets` and `/assets/app.js` match `/assets`, while `/assetsx` does not. A raw string prefix would cover unrelated segments and could misapply exclusions or cache policy. Root matches every absolute path.

This utility is not a general URL sanitizer, filesystem sandbox or object authorization check. Its contract only holds when routing, filters, proxies and application additions agree about decoding and path interpretation. Spring-Pages also redirects noncanonical request URIs, but that enforcement belongs there.

## Configuration that changes the security posture

| Property | Default | Cost of a wrong value | Status |
| --- | --- | --- | --- |
| `foundation.production` | `false` | Development policy in production, or persistent HSTS in development | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |
| `foundation.ip-address.trusted-proxy-list` | Empty | Anyone may assert forwarded identities | ![Infrastructure](https://img.shields.io/badge/infrastructure-0969DA?style=flat-square) |
| `foundation.ip-address.proxy-header-list` | `[CF-Connecting-IP]` | Missing data rejects clients; pass-through data allows forgery | ![Infrastructure](https://img.shields.io/badge/infrastructure-0969DA?style=flat-square) |
| `foundation.ip-address.header-list` | `[X-Forwarded-For, X-Real-IP, X-Client-IP, Forwarded]` | Development identities depend on client-controlled data | ![Configuration](https://img.shields.io/badge/configuration-D29922?style=flat-square) |

## How far this goes

This module decides no request's authorization on its own. It is the shared vocabulary the other three enforce with, so a wrong value here is wrong everywhere. Applications own deployment policy, proxy trust, TLS, cookie payload validation and consistent path handling. The default empty account provider resolves no account; identity storage and authentication belong to application providers and Spring-Security. `AccountIdentity.hasRole` supplies an ordinal comparison whose authorization implications are documented in Spring-Pages.

## Reporting a vulnerability

Use this repository's private security advisory reporting flow when enabled. Coordinate disclosure with the maintainers and keep exploit details out of public issues until a fix and disclosure plan are agreed.