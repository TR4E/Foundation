<h1 align="center">Utilities</h1>

<p align="center">Static Java helpers for strings, numbers, reflection, HTTP, files, encoding, and cryptography.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://github.com/phxql/argon2-jvm"><img alt="Argon2id" src="https://img.shields.io/badge/Argon2id-Password%20hashing-4B5563?logo=lock&logoColor=white"></a>
</p>

Utilities is a zero-framework dependency library of focused, thread-safe static helpers. Every class is a Lombok `@UtilityClass`: non-instantiable, all static methods. The module exists so that Foundation's other modules and consumer applications share one well-tested set of common operations rather than reimplementing string formatting, password hashing, HTTP calls, or reflection patterns in each project. Password hashing uses the `argon2-jvm` library; everything else uses the Java standard library.

## Contents

<details>
<summary>On this page</summary>

- [Modules and why they exist](#modules-and-why-they-exist)
- [Strings and English articles](#strings-and-english-articles)
- [Numbers, types, and input](#numbers-types-and-input)
- [Secure random codes](#secure-random-codes)
- [Hashing and password storage](#hashing-and-password-storage)
- [Generic type resolution](#generic-type-resolution)
- [Collection and map helpers](#collection-and-map-helpers)
- [Reflection helpers](#reflection-helpers)
- [HTTP requests](#http-requests)
- [File reads and caching](#file-reads-and-caching)
- [Base64 encoding](#base64-encoding)
- [Functional interfaces](#functional-interfaces)
- [Installation and build](#installation-and-build)

</details>

## Modules and why they exist

| Area | Classes | Why it exists |
| --- | --- | --- |
| Strings | [UtilString](src/main/java/me/trae/foundation/utilities/UtilString.java), [IndefiniteArticle](src/main/java/me/trae/foundation/utilities/IndefiniteArticle.java) | Consistent name formatting and English article selection across user-facing output |
| Numbers and input | [UtilNumber](src/main/java/me/trae/foundation/utilities/UtilNumber.java), [UtilType](src/main/java/me/trae/foundation/utilities/UtilType.java), [UtilInput](src/main/java/me/trae/foundation/utilities/UtilInput.java) | Safe parsing, clamping, and validation without scattered `try/catch` blocks |
| Codes | [UtilCode](src/main/java/me/trae/foundation/utilities/UtilCode.java) | Cryptographically secure random code generation using `SecureRandom` |
| Hashing and encoding | [UtilHash](src/main/java/me/trae/foundation/utilities/UtilHash.java), [UtilArgon](src/main/java/me/trae/foundation/utilities/UtilArgon.java), [UtilBase64](src/main/java/me/trae/foundation/utilities/UtilBase64.java) | General digests, HMACs, Argon2id password storage, and Base64 conversions |
| Reflection | [UtilGeneric](src/main/java/me/trae/foundation/utilities/UtilGeneric.java), [UtilClass](src/main/java/me/trae/foundation/utilities/UtilClass.java), [UtilField](src/main/java/me/trae/foundation/utilities/UtilField.java), [UtilMethod](src/main/java/me/trae/foundation/utilities/UtilMethod.java) | Generic type parameter resolution for frameworks like Injector, and reflective access wrappers |
| Collections | [UtilJava](src/main/java/me/trae/foundation/utilities/UtilJava.java) | Inline collection and map construction, safe casting |
| HTTP | [UtilHttp](src/main/java/me/trae/foundation/utilities/UtilHttp.java) | Synchronous and asynchronous HTTP with response classification, built on Java's `HttpClient` |
| Files | [UtilFile](src/main/java/me/trae/foundation/utilities/UtilFile.java) | Cached file reads with size and timestamp freshness checks |
| Functional | [TriConsumer](src/main/java/me/trae/foundation/utilities/functional/consumer/TriConsumer.java), [QuadConsumer](src/main/java/me/trae/foundation/utilities/functional/consumer/QuadConsumer.java), [TriFunction](src/main/java/me/trae/foundation/utilities/functional/function/TriFunction.java), [QuadFunction](src/main/java/me/trae/foundation/utilities/functional/function/QuadFunction.java) | Three- and four-argument functional interfaces that the standard library does not provide |

All utility classes use `ConcurrentHashMap` or thread-safe structures where they cache results. None of them carry mutable instance state; all shared state is static and thread-safe.

## Strings and English articles

### `UtilString`

String formatting helpers with deterministic output. Results for `clean`, `slice`, and `unSlice` are cached in bounded `ConcurrentHashMap` instances (up to 4,096 entries each) so repeated lookups for the same input do not recompute.

| Method | What it does | Example input | Example output |
| --- | --- | --- | --- |
| `clean(input)` | Lowercases with `Locale.ROOT`, replaces underscores with spaces, collapses whitespace, capitalizes each word | `"  HELLO_foundation "` | `"Hello Foundation"` |
| `slice(input)` | Removes spaces, underscores, and periods | `"Hello_World."` | `"HelloWorld"` |
| `unSlice(input)` | Replaces underscores with spaces and inserts spaces at camel-case boundaries | `"HTTPServer"` | `"HTTP Server"` |
| `isEmpty(input)` | Returns `true` for null or blank strings | `null` | `true` |
| `pair(key, value)` | Formats as `"key: value"` | `"Status", "Active"` | `"Status: Active"` |
| `formatToDollarByInteger(input)` | Grouped dollar format | `1234` | `"$1,234"` |
| `formatToDollarByDouble(input)` | Two decimal places | `1234.5` | `"$1,234.50"` |
| `withIndefiniteArticle(input)` | Prefixes with `a ` or `an ` | `"hour"` | `"an hour"` |
| `getIndefiniteArticlePrefix(input)` | Returns only the article prefix | `"hour"` | `"an "` |

Null or blank input returns `null` from `clean`, `slice`, and `unSlice`.

```java
final String displayName = UtilString.clean("HELLO_foundation");

final String identifier = UtilString.slice("Hello_World.");

final String readableName = UtilString.unSlice("HTTPServer");

final String description = UtilString.withIndefiniteArticle("hour");
```

### `IndefiniteArticle`

`IndefiniteArticle.get(input)` returns `"a "` or `"an "`. `format(input)` returns the article plus the original input. The algorithm checks word and prefix exception sets for common pronunciation cases (`"an hour"`, `"a university"`, `"a European"`), then falls back to a vowel-initial heuristic. It is a pronunciation approximation, not a phonetic engine. `format` preserves null and blank input; `get` returns an empty string for it.

`UtilString.withIndefiniteArticle` and `getIndefiniteArticlePrefix` delegate to this class.

## Numbers, types, and input

### `UtilNumber`

| Method | Behavior |
| --- | --- |
| `format(pattern, number)` | `DecimalFormat` with `Locale.ROOT` symbols and `HALF_UP` rounding |
| `clamp(minimum, maximum, value)` | Returns the value bounded by the endpoints, using `Comparable` |
| `getRandomNumber(type, minimum, maximum)` | Random value for `Integer`, `Long`, `Double`, or `Float`. The upper bound is exclusive unless both endpoints are equal, in which case that value is returned |

```java
final String amount = UtilNumber.format("#,##0.00", 1234.567); // "1,234.57"

final int percentage = UtilNumber.clamp(0, 100, 125); // 100

final Integer randomValue = UtilNumber.getRandomNumber(Integer.class, 1, 7); // 1 through 6
```

### `UtilType`

Character-level string validation. `isAllMatch` requires every character to satisfy the predicate; empty input returns `false`. `isAnyMatch` requires at least one.

| Method | Checks for |
| --- | --- |
| `isAlphabetic(input)` | Every character is a letter |
| `isNumeric(input)` | Every character is a digit |
| `isInteger(input)` | Parseable as `Integer` |
| `isDouble(input)` | Parseable as a finite `Double` |
| `isFloat(input)` | Parseable as a finite `Float` |
| `isLong(input)` | Parseable as `Long` |

### `UtilInput`

Safe parsing that returns `Optional` instead of throwing. `getInput(type, input)` finds a public static `valueOf(String)` method on the target type, or falls back to a public `String` constructor. Invalid input, unsupported types, and non-finite float/double values produce an empty optional. Boolean parsing accepts `"true"` or `"false"`, case-insensitively.

The parser lookup uses a `ClassValue` cache, so the reflection cost is paid once per type.

`getNumber` adds inclusive minimum and maximum bounds:

```java
final Optional<Integer> port = UtilInput.getNumber(Integer.class, 1, 65535, "8080");

final Optional<Boolean> enabled = UtilInput.getInput(Boolean.class, "TRUE");

final boolean validName = UtilType.isAlphabetic("Foundation");
```

## Secure random codes

`UtilCode` generates codes using `SecureRandom` and a caller-supplied character set. This is the right generator for verification codes, invite tokens, and anything where predictability would be a security problem.

| Method | Character set |
| --- | --- |
| `generate(length, characters)` | Any supplied `char[]` |
| `generateUpperCase(length)` | `A-Z` and `0-9` |
| `generateRandom(length)` | `A-Z`, `a-z`, and `0-9` |

```java
final String verificationCode = UtilCode.generateUpperCase(6); // e.g. "K4T9MX"

final String sessionToken = UtilCode.generateRandom(32);
```

## Hashing and password storage

### `UtilHash` for digests and HMACs

Wraps Java's `MessageDigest` and `Mac` with cloned instances from a thread-safe cache, so the algorithm lookup cost is paid once.

```java
final String digest = UtilHash.hashToString("SHA-256", "message");

final boolean matches = UtilHash.verify("SHA-256", "message", digest);

final String signature = UtilHash.hmac("HmacSHA256", secretKey, "message");
```

| Method | Returns |
| --- | --- |
| `hashToBytes(algorithm, input)` | Raw digest bytes |
| `hashToString(algorithm, input)` | Lowercase hexadecimal digest |
| `verify(algorithm, plain, stored)` | Constant-time comparison using `MessageDigest.isEqual` |
| `hmac(algorithm, key, input)` | Base64-encoded MAC |
| `toHex(bytes)` / `fromHex(hex)` | Byte array and hex string conversion |

Both `hashToBytes` and `hashToString` accept `byte[]` or `String` input.

`UtilHash` is for general digests and message authentication. It does not accept a password salt, pepper, or work factor. Use `UtilArgon` for password storage; a fast digest such as SHA-256 alone is not a password-hashing substitute. See the [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

### `UtilArgon` for passwords

`UtilArgon` uses Argon2id, the recommended password-hashing algorithm from [RFC 9106](https://www.rfc-editor.org/rfc/rfc9106/). It combines the password, an application-wide pepper, and a per-account salt with length prefixes into one input before calling Argon2id. This design means the pepper adds a secret that never touches the database, and the salt ensures two accounts with the same password produce different hashes.

```java
final String storedHash = UtilArgon.hash(password, pepper, accountSalt);

final boolean verified = UtilArgon.verify(password, pepper, accountSalt, storedHash);
```

The `password`, `pepper`, and `accountSalt` values come from the application: `UtilArgon` does not create them. Generate a distinct random salt per password, store that salt with the account record, and keep the pepper in a secret store separate from the password database. The returned encoded hash is what the application stores.

**Process-wide parameters:**

| Setting | Default | Accessors |
| --- | ---: | --- |
| Iterations | `3` | `getIterations()` / `setIterations(int)` |
| Memory | `65536` KiB (64 MiB) | `getMemoryKb()` / `setMemoryKb(int)` |
| Parallelism | `2` | `getParallelism()` / `setParallelism(int)` |
| Concurrent Argon operations | At least `1`, initially about half the available processors | `getMaximumConcurrency()` / `setMaximumConcurrency(int)` |

The concurrency semaphore prevents Argon2 from consuming all available memory when many requests hash simultaneously. Benchmark parameter changes on the deployment hardware.

**Rehashing:** the Argon2 encoded hash records the parameters it was hashed with. `tryReHash` checks whether the stored hash matches the current process-wide settings and returns a replacement hash when they differ, or `null` when the hash is already current:

```java
final String replacementHash = UtilArgon.tryReHash(password, pepper, accountSalt, storedHash);

if (replacementHash != null) {
    // Persist replacementHash for the account.
}
```

Call this after a successful login to transparently upgrade stored hashes when you increase the work factor.

**Verification caching:** successful verifications are cached in-process by stored hash and a keyed HMAC digest of the inputs. This avoids repeating the expensive Argon2 computation for the same credentials within a short window, which matters for applications that verify on every request. The default cache lifetime is 15 minutes and the default maximum is 4,096 entries; failed verifications are never cached.

| Setting | Default | Setter |
| --- | --- | --- |
| Cache TTL | 15 minutes | `setCacheTtlMs(long)` |
| Maximum entries | 4,096 | `setMaximumCacheEntries(int)` |
| Invalidate one entry | | `invalidateVerifyCache(storedHash)` |

The cache is swept lazily during verification calls. External cache and invalidation functions can be supplied through `setVerifyGetter` and `setVerifySetter` for applications that need distributed caching.

**Salt generation example** using 16 random bytes:

```java
final byte[] saltBytes = new byte[16];

new SecureRandom().nextBytes(saltBytes);

final String accountSalt = UtilBase64.encodeToString(saltBytes);
```

The 16-byte value is an application input to `UtilArgon`; store it with the account so the same value is available during verification.

## Generic type resolution

### `UtilGeneric`

`getGenericParameter(sourceClass, targetClass, typeIndex)` resolves a concrete class's generic type argument for a target interface or superclass. It walks the inheritance chain, substituting type variables along the way. The `typeIndex` is zero-based. Results are cached in a `ConcurrentHashMap`.

This is the mechanism that lets Foundation's Injector resolve parameterized types at runtime, and it is useful for any framework that needs to know the concrete type argument a class provides.

```java
interface Repository<T> {
}

abstract class RepositoryBase<T> implements Repository<T> {
}

final class StringRepository extends RepositoryBase<String> {
}

// Resolves String.class by walking StringRepository -> RepositoryBase<String> -> Repository<T>
final Class<?> entityType = UtilGeneric.getGenericParameter(StringRepository.class, Repository.class, 0);
```

The result is the raw `Class<?>`. For a parameterized argument such as `List<String>`, the result is its raw class (`List.class`). Unresolvable arguments return `null`.

## Collection and map helpers

### `UtilJava`

Inline construction helpers that avoid the `var x = new ArrayList<>(); x.add(...); return x;` pattern:

```java
final List<String> nameList = UtilJava.createCollection(new ArrayList<>(), names -> names.add("Foundation"));

UtilJava.updateCollection(nameList, names -> names.add("Utilities"));

final Map<String, Integer> countMap = UtilJava.createMap(new HashMap<>(), counts -> counts.put("modules", 5));

UtilJava.updateMap(countMap, counts -> counts.put("utilities", 1));
```

| Method | Purpose |
| --- | --- |
| `createCollection(collection, consumer)` | Run the consumer against a new collection and return it |
| `createMap(map, consumer)` | Run the consumer against a new map and return it |
| `updateCollection(collection, consumer)` | Run the consumer against an existing collection |
| `updateMap(map, consumer)` | Run the consumer against an existing map |
| `cast(type, value)` | Returns the value when it is an instance of the type, `null` otherwise |

`createCollection` and `createMap` do not allocate or copy; they return the same instance that was passed in.

## Reflection helpers

| Class | Methods | Purpose |
| --- | --- | --- |
| `UtilClass` | `create(type, args...)`, `create(type)` | Construct an instance through a matching constructor; declared reflection exceptions propagate |
| `UtilClass` | `formatName(basePackage, type)`, `formatName(type)` | Format a class name relative to a package when possible |
| `UtilField` | `get(type, instance, field)`, `set(instance, field, value)` | Read and write a `Field` on an instance with reflective access |
| `UtilMethod` | `invoke(instance, method, args...)`, `invoke(instance, method)` | Invoke a `Method` with reflective access |

These methods take reflection objects explicitly. They do not search for fields, constructors, or methods by name; the caller supplies the `Field`, `Method`, or `Class` directly.

## HTTP requests

### `UtilHttp`

A static HTTP client built on Java's `HttpClient`. The default client follows normal redirects with a 10-second request timeout. Both are configurable process-wide through `setHttpClient` and `setDefaultRequestTimeout`.

**Synchronous requests:**

```java
final HttpResponse<String> response = UtilHttp.supply(
        HttpMethod.GET,
        "https://example.com/status",
        HttpResponse.BodyHandlers.ofString()
);

if (UtilHttp.isSuccess(response)) {
    final String body = response.body();
}
```

**With body, content type, and headers:**

```java
final HttpResponse<String> response = UtilHttp.supply(
        HttpMethod.POST,
        endpoint,
        requestBody,
        "application/json",
        Map.of("Authorization", authorizationHeader),
        HttpResponse.BodyHandlers.ofString()
);
```

**Asynchronous requests:**

```java
final CompletableFuture<HttpResponse<String>> future = UtilHttp.supplyAsynchronous(
        HttpMethod.POST,
        endpoint,
        requestBody,
        "application/json",
        Map.of("Authorization", authorizationHeader),
        HttpResponse.BodyHandlers.ofString()
);
```

**Callback-based dispatch:**

```java
UtilHttp.dispatch(
        HttpMethod.GET,
        "https://example.com/status",
        HttpResponse.BodyHandlers.ofString(),
        response -> handleSuccess(response),
        error -> handleError(error)
);

UtilHttp.dispatchAsynchronous(
        HttpMethod.GET,
        "https://example.com/status",
        HttpResponse.BodyHandlers.ofString(),
        response -> handleSuccess(response),
        error -> handleError(error)
);
```

**Response classification:**

| Method | Status range |
| --- | --- |
| `isInformational(response)` | 100–199 |
| `isSuccess(response)` | 200–299 |
| `isRedirect(response)` | 300–399 |
| `isClientError(response)` | 400–499 |
| `isServerError(response)` | 500–599 |
| `isError(response)` | 400–599 |
| `isStatus(response, code)` | Exact match |
| `isStatusBetween(response, min, max)` | Inclusive range |
| `isStatusClass(response, class)` | Hundreds digit, for example `2` for 2xx |

Supported methods are `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `HEAD`, and `OPTIONS`. A non-2xx response is still an `HttpResponse`; it is not automatically an exception. `supply` wraps request failures in `HttpException`; `supplyAsynchronous` reports failures through the future.

## File reads and caching

### `UtilFile`

Reads a file's lines into a `List<String>` with automatic in-process caching. The cache is keyed by normalized absolute path, and a cached entry is reused only while both the file size and last-modified timestamp match. A change to either causes the file to be read again.

```java
final List<String> lineList = UtilFile.read(Path.of("config", "settings.yml"));
```

Accepts `Path`, `File`, or a path `String`. Read failures throw `UncheckedIOException`; null paths throw `IllegalArgumentException`.

| Setting | Default | Setter |
| --- | --- | --- |
| Maximum cacheable file size | 512 MiB | `setMaxCacheableInMegaBytes(long)` |

Files above the threshold are read without being retained. This is a per-file cutoff, not a total memory limit: cached entries are kept by path in a static map with no automatic eviction. Applications that read many distinct files should account for that retained memory. Since freshness uses size and last-modified metadata, rewriting a file while preserving both values cannot be detected by this cache.

## Base64 encoding

### `UtilBase64`

Wraps Java's standard Base64 encoder and decoder. Every method accepts and returns both `byte[]` and `String`:

| Method | Input | Output |
| --- | --- | --- |
| `encodeToBytes(input)` | `byte[]` or `String` | `byte[]` |
| `encodeToString(input)` | `byte[]` or `String` | `String` |
| `decodeToBytes(input)` | `byte[]` or `String` | `byte[]` |
| `decodeToString(input)` | `byte[]` or `String` | `String` |

Invalid Base64 input throws `IllegalArgumentException`.

## Functional interfaces

Java provides `Consumer`, `BiConsumer`, `Function`, and `BiFunction`. Utilities adds the three- and four-argument versions:

| Interface | Shape |
| --- | --- |
| `TriConsumer<A, B, C>` | `void accept(A, B, C)` |
| `QuadConsumer<A, B, C, D>` | `void accept(A, B, C, D)` |
| `TriFunction<A, B, C, R>` | `R apply(A, B, C)` |
| `QuadFunction<A, B, C, D, R>` | `R apply(A, B, C, D)` |

All four are `@FunctionalInterface` annotated.

## Installation and build

Requires JDK 25 and Maven. Build and install from the repository root:

```shell
mvn -pl Utilities -am install
```

Then add Utilities to a Maven project:

```xml
<dependency>
    <groupId>me.trae.foundation</groupId>
    <artifactId>Utilities</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

The artifact brings in `de.mkammerer:argon2-jvm` transitively for `UtilArgon`. To run the tests:

```shell
mvn -pl Utilities -am test
```

No external services are needed. Return to the [Foundation README](../README.md).
