<h1 align="center">Spring-Common</h1>

<p align="center">Shared configuration, IP resolution, path canonicalization, and filter ordering for Foundation's Spring modules.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://spring.io/projects/spring-boot"><img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-Servlet%20web-6DB33F?logo=springboot&logoColor=white"></a>
  <a href="https://jakarta.ee/specifications/servlet/"><img alt="Jakarta Servlet" src="https://img.shields.io/badge/Jakarta-Servlet-0769AD?logo=jakartaee&logoColor=white"></a>
</p>

Install Spring-Common into a servlet Spring Boot application using the steps below. The [README](README.md) describes the module and [SECURITY.md](SECURITY.md) explains its security boundaries.

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
mvn -pl Spring/Spring-Common -am install
```

In your application's Maven project, use Java 25 and the Boot 4.1.1 parent or equivalent imported BOM. Add normal compile dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
<dependency>
    <groupId>me.trae.foundation.spring</groupId>
    <artifactId>Spring-Common</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Keep the Foundation dependency on the runtime classpath. Its provided `jakarta.servlet-api` dependency is supplied by your application server. Lombok is provided and not a transitive runtime dependency; the optional configuration processor is not required at runtime. Include Lombok as a provided dependency and annotation processor when copying these examples. Utilities, spring-web, spring-boot and spring-boot-autoconfigure arrive transitively. Common itself installs no filters or MVC handlers.
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
            <artifactId>Spring-Common</artifactId>
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

`CommonAutoConfiguration` activates automatically in a servlet application, with no module `@Enable` annotation, added component scan or imported configuration class. The normal Spring Boot application bootstrap is still required.

| Bean | Default behavior | Replaceable |
| --- | --- | --- |
| `FoundationProperties` | production false | Configure through properties |
| `IpAddressProperties` | Environment-specific headers; trust list empty | Configure through properties |
| `CommonAccountProvider<?>` | EmptyCommonAccountProvider returns empty | Yes, conditional on missing bean |
| `IpAddressResolver` | Uses configured peer/header policy | Yes, conditional on missing bean |
| `CookieHelper` | Environment-aware attributes, optional Base64 | Yes, conditional on missing bean |

No filters or interceptors register here. Common only publishes the order constants consumed elsewhere: security headers 0, robots 1, CSRF 2, page cache 3, canonical redirect 4, asset gate 5. JWT is the one generic security component that its application declares explicitly in [Security installation](../Spring-Security/INSTALLATION.md); Common does not create it.

## Application contracts

| Interface | Every required method | Contract |
| --- | --- | --- |
| `AccountRoleDefinition` | `String name()`, `int ordinal()` | An enum supplies both; order from least to greatest privilege |
| `AccountIdentity<AccountRole>` | `UUID getId()`, `AccountRole getRole()` | Stable account UUID; default `hasRole(role)` is ordinal threshold |
| `CommonAccountProvider<Account>` | `Optional<Account> getAccountById(final UUID id)` | Return current account or empty; never infer authentication from an unverified UUID |

No account provider is required to use cookies or paths alone. Implement Common lookup when Security needs accounts. There is no token state on Account. Store accounts in your own repository; the example below deliberately starts with an empty map and demonstrates wiring, not authentication.

## Storage and environment

Common has no database schema. In production set `foundation.production=true`, configure actual trusted proxy CIDRs and selected overwritten headers, and firewall the origin accordingly. Empty trust lists accept headers from anyone. An untrusted peer with a configured list is identified by its socket address; a trusted proxy without acceptable forwarded data resolves empty. Production rejects local address classes; development accepts them.

CookieHelper's set and delete methods take the actual servlet response. `setCookie(response, name, value, httpOnly, maxAge, serialize)` sets Path=/ and no Domain, adding Secure, Strict and `__Host-` in production or Lax and plain names in development. `getCookie(request, name, deserialize)` returns null when absent or deserialization fails. Base64 is not encryption. Choose HttpOnly and payload validation at the call site.

## Settings

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

## Extension points

Register one bean per replacement type. CommonAccountProvider is an interface and accepts your implementation. CookieHelper and IpAddressResolver are final: replace them only by explicit construction of the same class with chosen properties, not subclasses. Changing their behavior more generally requires an application integration design beyond these replacement hooks. Properties beans are enabled configuration properties, not conditional defaults to shadow casually.

Common offers no handler annotations. `AccountIdentity.hasRole` can be overridden on your account to replace ordinal authorization.

## Minimal working setup

1. Create a Boot MVC application with Java 25, the dependency above and Lombok processing.
2. Create the files below under `src/main/java/example`. Keep them under the application's component scan.
3. Add `src/main/resources/application.yaml` as shown. For this direct local demo, loopback is explicitly untrusted so the socket peer is used.
4. Start with `mvn spring-boot:run`, then request `/request`. Replace the empty account map with persistent lookup before using JWT authentication.

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

`src/main/java/example/CommonConfiguration.java`:

```java
package example;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import me.trae.foundation.spring.common.account.CommonAccountProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class CommonConfiguration {

    @Bean
    public CommonAccountProvider<Account> commonAccountProvider() {
        final Map<UUID, Account> accountMap = Collections.unmodifiableMap(new HashMap<>());

        return id -> Optional.ofNullable(accountMap.get(id));
    }
}
```

`src/main/java/example/RequestController.java`:

```java
package example;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import me.trae.foundation.spring.common.address.IpAddressResolver;
import me.trae.foundation.spring.common.utility.UtilRequestPath;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
public final class RequestController {

    private final IpAddressResolver ipAddressResolver;

    @GetMapping("/request")
    public String request(final HttpServletRequest httpServletRequest) {
        return "%s %s".formatted(UtilRequestPath.getCanonicalPath(httpServletRequest), this.ipAddressResolver.getIpAddressByRequest(httpServletRequest).orElse("unresolved"));
    }
}
```

`src/main/resources/application.yaml`:

```yaml
foundation:
  production: false
  ip-address:
    trusted-proxy-list: [192.0.2.0/24]
```

The CIDR is a syntax example, not a real proxy range. Replace it for any proxy deployment.

## Verification

Request `http://localhost:8080/request`: the response contains `/request` and your local peer address. A forged forwarding header from a peer outside the configured list must not change it. Test cookie callers separately: production names are host-prefixed and Secure/Strict, development names are plain and Lax. Common alone issues no CSRF or authentication cookies and does not return CSRF 403 or rate-limit 429 responses.

```shell
mvn -pl Spring/Spring-Common -am test
```

## Troubleshooting

| Symptom | Check |
| --- | --- |
| No beans in reactive application | This auto-configuration requires servlet MVC |
| Empty account lookup | Default provider and sample map contain no accounts; install your repository-backed bean |
| Empty IP despite a peer | Trusted peers, including all peers under an empty list, require a configured acceptable header |
| Every client shares proxy IP | Nonempty trust list does not match actual proxy peer, so headers are ignored and socket address is used |
| Production host-prefixed cookie appears missing | With production false the cookie has a plain name, without Secure; correct the flag rather than looking only for `__Host-` |
| Secure cookie not retained locally | Production true requires HTTPS for host-prefixed cookies; use deliberate development policy locally |
| Invalid CIDR prevents startup | Correct literal or prefix; construction rejects invalid ranges |
| Want a custom resolver subclass | IpAddressResolver is final; the bean hook accepts another constructed instance, not inheritance |

Return to the [Spring guide](../README.md) or [Foundation](../../README.md).