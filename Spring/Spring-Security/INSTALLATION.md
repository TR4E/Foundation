<h1 align="center">Spring-Security</h1>

<p align="center">Ed25519 JWT authentication, security response headers, Content Security Policy and double-submit CSRF protection for Spring Boot.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet-0769AD?logo=jakartaee&logoColor=white"></a>
  <a href="https://www.rfc-editor.org/rfc/rfc8037"><img alt="JWT Ed25519" src="https://img.shields.io/badge/JWT-Ed25519-6DB33F?logo=jsonwebtokens&logoColor=white"></a>
</p>

Install Spring-Security into a servlet Spring Boot application using the steps below. The [README](README.md) describes the module and [SECURITY.md](SECURITY.md) explains its security boundaries.

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
mvn -pl Spring/Spring-Security -am install
```

In your application's Maven project, use Java 25 and the Boot 4.1.1 parent or equivalent imported BOM. Add normal compile dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Security</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Keep the Foundation dependency on the runtime classpath. Its provided `jakarta.servlet-api` dependency is supplied by your application server. Lombok is provided and not a transitive runtime dependency; the optional configuration processor is not required at runtime. Include Lombok as a provided dependency and annotation processor when copying these examples. Spring-Common and Utilities arrive transitively, along with spring-webmvc. `jjwt-api` and `bcprov-jdk18on` are compile dependencies; `jjwt-impl` and `jjwt-jackson` are runtime dependencies. Keep all on the runtime classpath. BouncyCastle supplies deterministic Ed25519 seed derivation, not the servlet runtime.
The JDBC example additionally needs these dependencies. PostgreSQL is an example persistence choice, not a requirement of the library:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

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
            <artifactId>Spring-Security</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.46</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
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

Security auto-configures after Common in servlet applications. No module enable annotation, added component scan or import is required.

| Registration | Default behavior |
| --- | --- |
| `SecurityHeadersProperties`, `CsrfProperties`, `JwtProperties` | Bind `foundation.security.headers`, `.csrf`, `.jwt` |
| `SecurityAccountProvider<?>` | Empty request result; session methods throw UnsupportedOperationException |
| `CsrfTokenService` | Random script-readable token cookie on unprotected requests when absent |
| `CsrfRegistry` and context-refresh registrar | Scan controller/method CsrfExclude path patterns |
| `SecurityHeadersFilter` | Order 0, REQUEST/ERROR/ASYNC |
| `CsrfFilter` | Order 2, REQUEST; missing token/source on protected writes gives 403 |

No JWT bean or JWT authentication filter is auto-created. The application declares `JwtSecurity<Account>` because Foundation cannot infer Account. This is the generic-bean exception to the no-annotation module wiring guarantee. Properties binding alone does not derive keys or require seeds. With the JAR alone, headers and CSRF apply, accounts remain anonymous, and ordinary routes receive no automatic JWT authorization.

## Application contracts

Use an Account implementing Common's UUID and role interfaces. It carries no token state. The examples below provide every method of both provider interfaces.

| Method | Return/effect | Persistence contract |
| --- | --- | --- |
| `CommonAccountProvider.getAccountById(final UUID id)` | Optional Account | Current account or empty |
| `getAccountByRequest(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse)` | `Optional<? extends Account>` | Delegate to authenticator with the actual response |
| `getRefreshSession(final Account account, final UUID sessionId)` | `Optional<RefreshSession>` | Lookup by account AND session, not session alone; current read |
| `getRefreshSessionList(final Account account)` | `List<RefreshSession>` | All that account's rows, including expired rows for login pruning; empty list if none |
| `updateRefreshSession(final Account account, final RefreshSession refreshSession, final String expectedTokenHash)` | boolean | Atomic expected-current-hash update; null means no row exists; true only if one row changed |
| `deleteRefreshSession(final Account account, final UUID sessionId)` | void | Delete only that account's session; absence is harmless |
| `deleteRefreshSessionList(final Account account)` | void | Delete all that account's sessions |

`updateRefreshSession` must be a conditional write that reports whether a row changed. An unconditional implementation silently breaks reuse detection. Keep persisted rows distinct from proposed values and avoid stale session caches. Read from authoritative storage across all instances.

Password change, forced logout and every other security event requiring account-wide invalidation must call `Rotation.revoke(account)` explicitly. The module does not observe password updates. Omitting this call leaves sessions alive. Call `revokeSession(account, sessionId)` for a deliberate single-device action; authorize session-management endpoints against the authenticated account.

Successful Authenticator resolution caches an account UUID on the request; subsequent Authenticator calls reload the account without repeating session validation or rotation. A new request validates again. Logout uses signed claims without requiring the fingerprint and can resolve the token subject when passed a null account, allowing session deletion even when fingerprint-based authentication fails.

Logout does not require a fingerprint cookie, including when that cookie is missing or mangled. It parses a server-signed refresh token with access-token fallback, checking signature, temporal claims, issuer, audience, type and required claims. It resolves the token subject through CommonAccountProvider, checks any supplied account against that subject, and deletes only that account/session pair. Passing null for the account still permits subject-based resolution. A mismatched supplied account cannot select another account for deletion. Without valid tokens, it only expires the three authentication cookies and deletes no session. Other devices remain active.

The dependency cycle is provider -> JwtSecurity -> provider. Inject `ObjectProvider<JwtSecurity<Account>>` into the provider and call `getObject()` only during request resolution. Do not resolve it in the constructor or a bean factory method. The provider below demonstrates the cycle break.

### Request cache and concurrent refreshes

Successful Authenticator resolution stores the account UUID under `foundation.security.jwt.resolved-account`. Later Authenticator calls in that request call CommonAccountProvider.getAccountById again, without parsing cookies or checking the session again. Rotation therefore occurs at most once per request. Failed authentication stores nothing. A session revoked part way through a request remains visible to later calls in that request while the account still exists, and is rejected on the next request.

`rotation-grace`, default 5s, now accommodates genuinely concurrent HTTP requests carrying the immediately previous refresh token. It is not needed for repeated Authenticator calls within one request. Setting it to 0 disables previous-token acceptance between requests: a fresh refresh-only request replaying the previous token triggers reuse detection and revokes all account sessions. It does not disable the request cache or make a legitimate second resolution within the same request rotate or revoke. A still-valid access token can avoid refresh processing entirely.

### Session storage boundaries

`Rotation.getSessionList(account)` returns RefreshSession objects containing stored current and previous token hashes. Map them to an application response DTO containing only intended device metadata, such as sessionId, expireAt and rotatedAt, before exposing a devices endpoint. Do not serialize the returned objects directly.

Session eviction and insertion are not atomic. Two simultaneous logins at the cap can briefly leave one session over the limit; the library does not repair that excess immediately. Coordinate by account in application storage if the cap must be strict. Failed insertion can also follow an eviction.

Every provider implementation must scope both `getRefreshSession(account, sessionId)` and `deleteRefreshSession(account, sessionId)` by account UUID as well as session UUID. Knowing another account's session UUID must not confer access or deletion rights.

## Storage and environment

Create a migration with this PostgreSQL schema. Millisecond timestamps are stored as bigint. Hashes are hex SHA-512 strings, so current and previous hashes have 128 characters. The composite primary key scopes session IDs to accounts; its leading account key supports listing and deletion. The expiry index supports maintenance outside login. No schema is created automatically by Foundation.

```sql
CREATE TABLE application_account (
    id uuid PRIMARY KEY,
    username text NOT NULL UNIQUE,
    role text NOT NULL,
    password_salt text NOT NULL,
    password_hash text NOT NULL,
    password_iterations integer NOT NULL CHECK (password_iterations > 0)
);
CREATE TABLE refresh_session (
    account_id uuid NOT NULL REFERENCES application_account(id) ON DELETE CASCADE,
    session_id uuid NOT NULL,
    token_hash varchar(128) NOT NULL,
    previous_token_hash varchar(128),
    expire_at bigint NOT NULL,
    rotated_at bigint NOT NULL,
    PRIMARY KEY (account_id, session_id)
);
CREATE INDEX refresh_session_expire_at_idx ON refresh_session (expire_at);
```

`RefreshSession` is constructed as `(sessionId, tokenHash, previousTokenHash, expireAt, rotatedAt)`. The conditional upsert is the `UPSERT_SQL` in the complete provider below: its update branch requires a non-null matching hash, its insert branch requires null and uses ON CONFLICT DO NOTHING. It returns a count of changed rows. Unlike a generic unconditional upsert, it does not recreate a deleted session on a non-null expected hash or overwrite an existing session on null. Keep account-level login pruning/eviction coordinated externally if a strict concurrent session cap is required. A login insertion failure can occur after eviction; the library does not roll deleted rows back.

Generate two independent seeds with PowerShell, including Windows PowerShell compatibility:

```powershell
$seedBytes = New-Object byte[] 32
$seedGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$seedGenerator.GetBytes($seedBytes)
[Convert]::ToBase64String($seedBytes)
$seedGenerator.Dispose()
```

Run twice, set `JWT_ACCESS_KEY_SEED` and `JWT_REFRESH_KEY_SEED` in your deployment environment, and reference them in YAML. Never commit seed values. Use the same distinct pair across instances. Development without a seed logs `No Ed25519 key seed configured for ..., generating an ephemeral key pair`; restart then loses token verification. Production missing either seed throws `IllegalStateException` during JWT construction. Invalid Base64 or a decoded size other than 32 fails in either mode. Spring Environment retains strings after the properties bean is cleared; keep actuator env/configprops endpoints private. Changing seeds invalidates old signatures; there is no key ring or kid rollover.

## Settings

Lists replace defaults rather than appending. Durations bind Spring duration syntax such as `15m`, `7d`, `5s`. Set properties before constructing JWT parsers; later changing issuer, audience, skew or seeds does not rebuild parsers. Nonpositive max-sessions disables live-session eviction, while expired-session pruning still runs. The library does not validate all duration or naming combinations; choose positive lifetimes, nonnegative grace/skew and distinct cookie names.

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
| `rotation-grace` | `Duration` | `5s` | Allow exactly the previous session hash briefly; 0 disables it | Genuinely concurrent HTTP requests, not repeat resolution in one request |
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

| Bean or annotation | Replace or apply | Effect |
| --- | --- | --- |
| CommonAccountProvider, CookieHelper, IpAddressResolver | Common conditional beans | See Common guide; helper and resolver are final |
| SecurityAccountProvider | Conditional on missing bean | Supplies request identity and all session persistence |
| CsrfTokenService | Conditional on missing bean; final class | Explicit construction with selected Foundation/CSRF properties; not subclassing |
| JwtSecurity<Account> | Application-owned bean | Choose account type and providers; no auto-created default |
| `@CsrfExclude` | Type or method | Exempts canonical path patterns from CSRF, irrespective of incoming method |

Header/CSRF filters, registries and listeners are not conditional replacement hooks. Configure them through their properties rather than registering a duplicate bean with the same name. Protect signed webhooks or exclusive bearer APIs with their own verification before applying CsrfExclude. It does not verify signatures itself. Password hashing, credential throttling and recovery flows remain application concerns.

## Minimal working setup

1. Start an empty Boot MVC application with Java 25, Lombok processing and the dependencies above. Create PostgreSQL and apply the migration.
2. Create each file below in package `example`. If you already used Common's demo, retain Application, AccountRole and Account, remove the empty CommonConfiguration and add SecurityConfiguration instead. Register each provider once.
3. Set JDBC credentials, both seed environment variables and the YAML below. Use production false only for deliberate local HTTP testing.
4. Provision an `alice` account using a password verifier compatible with AccountRepository below. The optional standard-library Python command after the files creates a salted PBKDF2 verifier; bind its output and a chosen account UUID into the shown INSERT using your database tooling.
5. Start the application, fetch a CSRF cookie, log in and call `/account` with the same cookie jar. Follow Verification below.
6. Before deployment, add Spring-Ratelimit for login, implement reset and password-change flows with explicit revoke, review password verification and unknown-user timing, and deploy HTTPS with trusted proxy configuration. The example is runnable wiring, not a complete account product.

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

`src/main/java/example/Account.java`:

```java
package example;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import me.trae.foundation.spring.common.account.AccountIdentity;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@Getter
public final class Account implements AccountIdentity<AccountRole> {

    @EqualsAndHashCode.Include
    private final UUID id;

    private final AccountRole role;
}
```

`src/main/java/example/AccountRepository.java`:

```java
package example;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@AllArgsConstructor
@Repository
public final class AccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<Account> getAccountById(final UUID id) {
        return this.jdbcTemplate.query("SELECT id, role FROM application_account WHERE id = ?", (resultSet, _) -> new Account(resultSet.getObject("id", UUID.class), AccountRole.valueOf(resultSet.getString("role"))), id).stream().findFirst();
    }

    public Optional<Account> authenticate(final String username, final String password) {
        return this.jdbcTemplate.query("SELECT * FROM application_account WHERE username = ?", (resultSet, _) -> new Credential(new Account(resultSet.getObject("id", UUID.class), AccountRole.valueOf(resultSet.getString("role"))), resultSet.getString("password_salt"), resultSet.getString("password_hash"), resultSet.getInt("password_iterations")), username).stream()
                .filter(credential -> this.matchesPassword(credential, password))
                .map(Credential::getAccount)
                .findFirst();
    }

    private boolean matchesPassword(final Credential credential, final String password) {
        final char[] passwordChars = password.toCharArray();
        final PBEKeySpec pbeKeySpec = new PBEKeySpec(passwordChars, Base64.getDecoder().decode(credential.getSalt()), credential.getIterations(), 256);

        try {
            final byte[] passwordHash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(pbeKeySpec).getEncoded();

            try {
                return MessageDigest.isEqual(passwordHash, Base64.getDecoder().decode(credential.getHash()));
            } finally {
                Arrays.fill(passwordHash, (byte) 0);
            }
        } catch (final GeneralSecurityException exception) {
            throw new IllegalStateException("Password verification algorithm unavailable", exception);
        } finally {
            pbeKeySpec.clearPassword();
            Arrays.fill(passwordChars, '\0');
        }
    }

    @AllArgsConstructor
    @Getter
    private static final class Credential {

        private final Account account;

        private final String salt, hash;

        private final int iterations;
    }
}
```

`src/main/java/example/ApplicationSecurityAccountProvider.java`:

```java
package example;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import me.trae.foundation.spring.security.account.SecurityAccountProvider;
import me.trae.foundation.spring.security.jwt.JwtSecurity;
import me.trae.foundation.spring.security.jwt.refresh.RefreshSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public final class ApplicationSecurityAccountProvider implements SecurityAccountProvider<Account> {

    private static final String UPSERT_SQL = """
            WITH updated AS (
                UPDATE refresh_session
                SET token_hash = :token_hash, previous_token_hash = :previous_token_hash,
                    expire_at = :expire_at, rotated_at = :rotated_at
                WHERE account_id = :account_id AND session_id = :session_id
                  AND :expected_hash IS NOT NULL AND token_hash = :expected_hash
                RETURNING 1
            ), inserted AS (
                INSERT INTO refresh_session (account_id, session_id, token_hash, previous_token_hash, expire_at, rotated_at)
                SELECT :account_id, :session_id, :token_hash, :previous_token_hash, :expire_at, :rotated_at
                WHERE :expected_hash IS NULL
                ON CONFLICT (account_id, session_id) DO NOTHING
                RETURNING 1
            )
            SELECT COUNT(*) FROM (SELECT * FROM updated UNION ALL SELECT * FROM inserted) AS changed
            """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final ObjectProvider<JwtSecurity<Account>> jwtSecurityObjectProvider;

    @Override
    public Optional<Account> getAccountByRequest(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
        return this.jwtSecurityObjectProvider.getObject().getAuthenticator().getAccountByRequest(httpServletRequest, httpServletResponse);
    }

    @Override
    public Optional<RefreshSession> getRefreshSession(final Account account, final UUID sessionId) {
        return this.namedParameterJdbcTemplate.query("SELECT * FROM refresh_session WHERE account_id = :account_id AND session_id = :session_id", new MapSqlParameterSource("account_id", account.getId()).addValue("session_id", sessionId), this::mapSession).stream().findFirst();
    }

    @Override
    public List<RefreshSession> getRefreshSessionList(final Account account) {
        return this.namedParameterJdbcTemplate.query("SELECT * FROM refresh_session WHERE account_id = :account_id", new MapSqlParameterSource("account_id", account.getId()), this::mapSession);
    }

    @Override
    public boolean updateRefreshSession(final Account account, final RefreshSession refreshSession, final String expectedTokenHash) {
        final MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource("account_id", account.getId())
                .addValue("session_id", refreshSession.getSessionId())
                .addValue("token_hash", refreshSession.getTokenHash())
                .addValue("previous_token_hash", refreshSession.getPreviousTokenHash(), Types.VARCHAR)
                .addValue("expire_at", refreshSession.getExpireAt())
                .addValue("rotated_at", refreshSession.getRotatedAt())
                .addValue("expected_hash", expectedTokenHash, Types.VARCHAR);

        return this.namedParameterJdbcTemplate.queryForObject(UPSERT_SQL, mapSqlParameterSource, Integer.class) == 1;
    }

    @Override
    public void deleteRefreshSession(final Account account, final UUID sessionId) {
        this.namedParameterJdbcTemplate.update("DELETE FROM refresh_session WHERE account_id = :account_id AND session_id = :session_id", new MapSqlParameterSource("account_id", account.getId()).addValue("session_id", sessionId));
    }

    @Override
    public void deleteRefreshSessionList(final Account account) {
        this.namedParameterJdbcTemplate.update("DELETE FROM refresh_session WHERE account_id = :account_id", new MapSqlParameterSource("account_id", account.getId()));
    }

    private RefreshSession mapSession(final ResultSet resultSet, final int rowNumber) throws SQLException {
        return new RefreshSession(resultSet.getObject("session_id", UUID.class), resultSet.getString("token_hash"), resultSet.getString("previous_token_hash"), resultSet.getLong("expire_at"), resultSet.getLong("rotated_at"));
    }
}
```

`src/main/java/example/SecurityConfiguration.java`:

```java
package example;

import me.trae.foundation.spring.common.FoundationProperties;
import me.trae.foundation.spring.common.account.CommonAccountProvider;
import me.trae.foundation.spring.common.cookie.CookieHelper;
import me.trae.foundation.spring.security.account.SecurityAccountProvider;
import me.trae.foundation.spring.security.jwt.JwtProperties;
import me.trae.foundation.spring.security.jwt.JwtSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class SecurityConfiguration {

    @Bean
    public CommonAccountProvider<Account> commonAccountProvider(final AccountRepository accountRepository) {
        return accountRepository::getAccountById;
    }

    @Bean
    public JwtSecurity<Account> jwtSecurity(final JwtProperties jwtProperties, final FoundationProperties foundationProperties, final CommonAccountProvider<Account> commonAccountProvider, final SecurityAccountProvider<Account> securityAccountProvider, final CookieHelper cookieHelper) {
        return new JwtSecurity<>(jwtProperties, foundationProperties, commonAccountProvider, securityAccountProvider, cookieHelper);
    }
}
```

`src/main/java/example/LoginRequest.java`:

```java
package example;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public final class LoginRequest {

    private String username, password;
}
```

`src/main/java/example/AuthenticationController.java`:

```java
package example;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.AllArgsConstructor;
import me.trae.foundation.spring.security.jwt.JwtSecurity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@AllArgsConstructor
@RestController
public final class AuthenticationController {

    private final AccountRepository accountRepository;

    private final JwtSecurity<Account> jwtSecurity;

    @PostMapping("/auth/login")
    public void login(@RequestBody final LoginRequest loginRequest, final HttpServletResponse httpServletResponse) {
        if (loginRequest.getUsername() == null || loginRequest.getPassword() == null || loginRequest.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        final Account account = this.accountRepository.authenticate(loginRequest.getUsername(), loginRequest.getPassword()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!this.jwtSecurity.getCookie().apply(httpServletResponse, account)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
    }

    @PostMapping("/auth/logout")
    public void logout(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
        this.jwtSecurity.getCookie().remove(httpServletRequest, httpServletResponse, this.jwtSecurity.getAuthenticator().getAccountByRequest(httpServletRequest, httpServletResponse).orElse(null));
    }

    @PostMapping("/auth/logout-all")
    public void logoutAll(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
        this.jwtSecurity.getRotation().revoke(this.requireAccount(httpServletRequest, httpServletResponse));
    }

    @GetMapping("/account")
    public ResponseEntity<UUID> account(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
        return ResponseEntity.ok(this.requireAccount(httpServletRequest, httpServletResponse).getId());
    }

    private Account requireAccount(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
        return this.jwtSecurity.getAuthenticator().getAccountByRequest(httpServletRequest, httpServletResponse).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
```

Optional verifier provisioning, run locally with Python 3 and copy only the verifier outputs:

```python
import base64
import getpass
import hashlib
import secrets
salt = secrets.token_bytes(32)
iterations = 600000
password_hash = hashlib.pbkdf2_hmac("sha256", getpass.getpass().encode("utf-8"), salt, iterations, 32)
print(base64.b64encode(salt).decode("ascii"))
print(base64.b64encode(password_hash).decode("ascii"))
print(iterations)
```

Bind the generated salt/hash/iterations and your chosen UUID in a database client, rather than concatenating credentials into SQL:

```sql
INSERT INTO application_account (id, username, role, password_salt, password_hash, password_iterations)
VALUES (:account_id, 'alice', 'ADMIN', :salt, :password_hash, :iterations);
```

The PBKDF2 example is application code, not a library feature. Set work factors for your deployment and keep credential throttling in front of verification. Java request strings cannot be wiped even though the temporary character and derived hash arrays are cleared.

`src/main/resources/application.yaml` for local testing:

```yaml
spring:
  datasource:
    url: ${JDBC_URL}
    username: ${JDBC_USERNAME}
    password: ${JDBC_PASSWORD}
foundation:
  production: false
  ip-address:
    trusted-proxy-list: [192.0.2.0/24]
  security:
    jwt:
      access-token-key-seed: ${JWT_ACCESS_KEY_SEED}
      refresh-token-key-seed: ${JWT_REFRESH_KEY_SEED}
      max-sessions: 10
```

Set production true for real HTTPS. The CIDR is documentation syntax, not a real proxy trust configuration. Replace it and the selected header list with actual peer ranges and overwritten headers, and match the origin firewall.

## Verification

Fetch `/account` without cookies: it returns 401 and issues the CSRF cookie. In a browser on the same origin, perform login with the echoed value:

```javascript
const csrf = document.cookie.split("; ").find(value => value.startsWith("XSRF-TOKEN="))?.split("=")[1];
await fetch("/auth/login", {
    method: "POST",
    headers: {"Content-Type": "application/json", "X-XSRF-TOKEN": csrf},
    body: JSON.stringify({username: "alice", password: "your-local-test-password"})
});
await fetch("/account");
```

In production use `__Host-XSRF-TOKEN`, or expose `CsrfTokenService.getCookieName()` through your page model. The browser supplies Origin; command-line clients must supply a matching Origin or Referer too. Keep a separate cookie jar for each simulated browser.

| Check | Expected |
| --- | --- |
| Successful login | Three HttpOnly cookies: access, refresh and fingerprint; apply returns true |
| Development attributes | Plain names, Path=/, no Domain, SameSite Lax, no Secure |
| Production over HTTPS | `__Host-` names, Secure, SameSite Strict, HttpOnly on authentication cookies; CSRF remains readable |
| Missing CSRF header on POST | 403 before credential verification |
| Two browser logins | Distinct session rows; both `/account` requests work |
| Logout one jar | Its row deleted; its next request fails even using an old still-signed access token; other jar works |
| Explicit revoke or detected reuse | All account sessions removed; all devices fail next resolution |
| Missing seed | Warning plus ephemeral pair in development; construction failure in production |

For page redirect/403 and asset 404 checks, add [Pages](../Spring-Pages/INSTALLATION.md#verification). For account budget 429 across sessions, add [Ratelimit](../Spring-Ratelimit/INSTALLATION.md#verification).

```shell
mvn -pl Spring/Spring-Security -am test
```

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| Anonymous requests forever | Empty provider is active, or Common lookup returns no current account |
| UnsupportedOperationException: No security account provider configured | Session operations reached EmptySecurityAccountProvider; implement all six methods and register the bean |
| Circular bean definition | Provider injects JwtSecurity eagerly; use ObjectProvider and resolve it only at request time |
| Every POST gets 403 | Fetch the CSRF cookie, echo its exact value in the configured header and supply matching source hostname |
| Expected production cookies missing | production false produces plain names without Secure; it does not suppress cookies. Correct the flag and client name expectation |
| Secure cookies disappear over HTTP | Use HTTPS in production; the browser enforces Secure and host-prefix rules |
| Refresh randomly logs users out | Conditional-write contract or fresh-session reads are broken; check affected row count and expected hash |
| Sessions survive password change | Call Rotation.revoke(account) explicitly after the security event; no automatic password observer exists |
| Session list grows beyond intended cap | Values below 1 disable eviction; set a positive max-sessions and coordinate concurrent logins |
| Login evicts other devices | Cap reached; default is 10, and 1 deliberately behaves as single-device |
| Requests fail only on another instance | Align seeds, issuer, audience, clock policy and shared session storage |
| Authenticated request emits no new cookies after a lost write | Expected CAS-loser behavior; retain the winning browser cookie set |

Return to the [Spring guide](../README.md) or [Foundation](../../README.md).