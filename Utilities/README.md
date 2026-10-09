<h1 align="center">Utilities</h1>

<p align="center">Reusable Java helpers for Foundation projects and other applications.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://github.com/phxql/argon2-jvm"><img alt="Argon2id" src="https://img.shields.io/badge/Argon2id-Password%20hashing-4B5563?logo=lock&logoColor=white"></a>
</p>

Utilities is a collection of focused static helpers for strings, numbers, reflection, HTTP, files, encoding, and cryptography. It has no application-framework dependency. Password hashing uses the `argon2-jvm` library; most other helpers use the Java standard library.

## Contents

<details>
<summary>On this page</summary>

- [At a glance](#at-a-glance)
- [Strings and English articles](#strings-and-english-articles)
- [Numbers, types, and input](#numbers-types-and-input)
- [Hashing and password storage](#hashing-and-password-storage)
- [Generic type lookup and Java helpers](#generic-type-lookup-and-java-helpers)
- [HTTP requests](#http-requests)
- [File reads and caching](#file-reads-and-caching)
- [Other utilities](#other-utilities)
- [Installation and build](#installation-and-build)

</details>

## At a glance

| Area | Utilities | Main use |
| --- | --- | --- |
| Strings | [UtilString](src/main/java/me/trae/foundation/utilities/UtilString.java), [IndefiniteArticle](src/main/java/me/trae/foundation/utilities/IndefiniteArticle.java) | Normalizing names, formatting text, and choosing English indefinite articles |
| Numbers and input | [UtilNumber](src/main/java/me/trae/foundation/utilities/UtilNumber.java), [UtilType](src/main/java/me/trae/foundation/utilities/UtilType.java), [UtilInput](src/main/java/me/trae/foundation/utilities/UtilInput.java), [UtilCode](src/main/java/me/trae/foundation/utilities/UtilCode.java) | Formatting, clamping, random values, validation, parsing, and secure random codes |
| Hashing and encoding | [UtilHash](src/main/java/me/trae/foundation/utilities/UtilHash.java), [UtilArgon](src/main/java/me/trae/foundation/utilities/UtilArgon.java), [UtilBase64](src/main/java/me/trae/foundation/utilities/UtilBase64.java) | General digests and HMACs, Argon2id passwords, and Base64 |
| Reflection and collections | [UtilGeneric](src/main/java/me/trae/foundation/utilities/UtilGeneric.java), [UtilJava](src/main/java/me/trae/foundation/utilities/UtilJava.java), [UtilClass](src/main/java/me/trae/foundation/utilities/UtilClass.java), [UtilField](src/main/java/me/trae/foundation/utilities/UtilField.java), [UtilMethod](src/main/java/me/trae/foundation/utilities/UtilMethod.java) | Generic parameter lookup, collection/map callbacks, construction, and reflective access |
| HTTP and files | [UtilHttp](src/main/java/me/trae/foundation/utilities/UtilHttp.java), [UtilFile](src/main/java/me/trae/foundation/utilities/UtilFile.java) | Synchronous/asynchronous HTTP and cached text-file reads |
| Functional interfaces | [TriConsumer](src/main/java/me/trae/foundation/utilities/functional/consumer/TriConsumer.java), [QuadConsumer](src/main/java/me/trae/foundation/utilities/functional/consumer/QuadConsumer.java), [TriFunction](src/main/java/me/trae/foundation/utilities/functional/function/TriFunction.java), [QuadFunction](src/main/java/me/trae/foundation/utilities/functional/function/QuadFunction.java) | Three- and four-argument consumers and functions |

## Strings and English articles

### `UtilString`

`UtilString` offers small, deterministic formatting and name-conversion helpers. Null or blank input is considered empty by `isEmpty`; `clean`, `slice`, and `unSlice` return `null` for that input.

| Method | Behavior |
| --- | --- |
| `clean(input)` | Lowercases with `Locale.ROOT`, changes underscores to word separators, collapses whitespace, and capitalizes each word. `"  HELLO_foundation "` becomes `"Hello Foundation"`. |
| `slice(input)` | Removes spaces, underscores, and periods. `"Hello_World."` becomes `"HelloWorld"`. |
| `unSlice(input)` | Changes underscores to spaces and separates camel-case boundaries. `"HTTPServer"` becomes `"HTTP Server"`. |
| `isEmpty(input)` | Returns true for null or blank strings. |
| `pair(key, value)` | Formats a pair as `"key: value"`. |
| `formatToDollarByInteger(input)` | Formats an integer as a grouped dollar amount, such as `"$1,234"`. |
| `formatToDollarByDouble(input)` | Formats a double with two decimal places, such as `"$1,234.50"`. |
| `withIndefiniteArticle(input)` | Prefixes the input with `a` or `an` and a space. |
| `getIndefiniteArticlePrefix(input)` | Returns the article prefix, including its trailing space. |

```java
final String displayName = UtilString.clean("HELLO_foundation");

final String identifier = UtilString.slice("Hello_World.");

final String readableName = UtilString.unSlice("HTTPServer");

final String description = UtilString.withIndefiniteArticle("hour");
```

### `IndefiniteArticle`

`IndefiniteArticle.get(input)` returns `"a "` or `"an "`; `format(input)` returns the article plus the original input. It uses word and prefix exceptions for common pronunciation cases, then applies a simple English-language fallback. For example, it distinguishes `"an hour"`, `"a university"`, and `"a European"`. It is a pronunciation heuristic, not a general-purpose language or phonetic engine. `format` preserves null and blank input; `get` returns an empty prefix for it. `UtilString` exposes the same behavior through `withIndefiniteArticle` and `getIndefiniteArticlePrefix`.

## Numbers, types, and input

### `UtilNumber`

- `format(pattern, number)` uses `DecimalFormat` with `Locale.ROOT` symbols and `HALF_UP` rounding.
- `clamp(minimum, maximum, value)` returns the value bounded by the supplied endpoints.
- `getRandomNumber(type, minimum, maximum)` supports `Integer`, `Long`, `Double`, and `Float`. The upper endpoint is exclusive unless the endpoints are equal, in which case that endpoint is returned. Other number classes throw `IllegalArgumentException`.

```java
final String amount = UtilNumber.format("#,##0.00", 1234.567); // 1,234.57

final int percentage = UtilNumber.clamp(0, 100, 125); // 100

final Integer randomValue = UtilNumber.getRandomNumber(Integer.class, 1, 7); // 1 through 6
```

### `UtilType` and `UtilInput`

`UtilType` checks whether text is alphabetic, numeric, an integer, a finite float/double, or a long. `isAllMatch` and `isAnyMatch` accept a `Predicate<Character>` for custom checks. Empty input does not count as an all-match.

`UtilInput.getInput(type, input)` returns an `Optional<T>`. It parses through a compatible public static `valueOf(String)` method, or falls back to a public constructor that accepts a `String`. Invalid input, unsupported parsers, and non-finite float/double values produce an empty optional. Boolean parsing accepts `true` or `false`, case-insensitively.

`getNumber` also checks inclusive minimum and maximum bounds:

```java
final Optional<Integer> port = UtilInput.getNumber(Integer.class, 1, 65535, "8080");

final Optional<Boolean> enabled = UtilInput.getInput(Boolean.class, "TRUE");

final boolean validName = UtilType.isAlphabetic("Foundation");
```

### `UtilCode`

`generate(length, characters)` creates a code using the supplied character array and `SecureRandom`. `generateUpperCase` uses uppercase letters and digits; `generateRandom` uses upper- and lowercase letters and digits.

## Hashing and password storage

### `UtilHash` for digests and HMACs

`UtilHash` wraps Java's digest and MAC algorithms:

```java
final String digest = UtilHash.hashToString("SHA-256", "message");

final boolean matches = UtilHash.verify("SHA-256", "message", digest);

final String signature = UtilHash.hmac("HmacSHA256", secretKey, "message");
```

`hashToBytes` returns raw digest bytes; `hashToString` returns hexadecimal. `verify` accepts raw bytes or a hexadecimal digest and compares the computed digest with `MessageDigest.isEqual`. `hmac` returns Base64 text. `toHex` and `fromHex` convert byte arrays and hexadecimal strings.

`UtilHash` is for general digests and message authentication. Its API does not accept a password salt, pepper, or work factor. Use `UtilArgon` for password storage; a fast digest such as SHA-256 alone is not a password-hashing substitute. See the [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

### `UtilArgon` for passwords

`UtilArgon` uses Argon2id. Hash and verify with the same password, application pepper, and caller-provided salt:

```java
final String storedHash = UtilArgon.hash(password, pepper, accountSalt);

final boolean verified = UtilArgon.verify(password, pepper, accountSalt, storedHash);
```

The `password`, `pepper`, and `accountSalt` values above come from the application: `UtilArgon` does not create them. It combines their UTF-8 bytes with length prefixes before calling Argon2id. Generate a distinct random salt per password, store that salt with the account record, and keep the pepper in a secret store separate from the password database. The returned encoded hash is what the application stores as `storedHash`. See [RFC 9106](https://www.rfc-editor.org/rfc/rfc9106/) for the Argon2 specification and the OWASP password-storage guidance linked above.

The current process-wide parameters are:

| Setting | Default | Accessors |
| --- | ---: | --- |
| Iterations | `3` | `getIterations()` / `setIterations(int)` |
| Memory | `65536` KiB (64 MiB) | `getMemoryKb()` / `setMemoryKb(int)` |
| Parallelism | `2` | `getParallelism()` / `setParallelism(int)` |
| Concurrent Argon operations | At least `1`, initially about half the available processors | `getMaximumConcurrency()` / `setMaximumConcurrency(int)` |

These settings are static and apply process-wide. Benchmark parameter changes on the deployment hardware and make sure verification and new hashes use the same pepper and caller-provided salt. The Argon2 encoded hash records the hash's own parameters; `tryReHash` checks it against the current iteration, memory, and parallelism settings. It returns `null` when the stored hash is already current, or a replacement hash when it needs updating:

```java
final String replacementHash = UtilArgon.tryReHash(password, pepper, accountSalt, storedHash);

if (replacementHash != null) {
    // Persist replacementHash for the account.
}
```

Successful verifications are cached in-process by stored hash and a keyed digest of the supplied inputs. The default cache lifetime is 15 minutes and the default maximum is 4,096 entries; failed verifications are not cached. `setCacheTtlMs` and `setMaximumCacheEntries` configure those bounds. Call `invalidateVerifyCache(storedHash)` when an entry must be removed before expiry.

Example salt generation using 16 random bytes:

```java
final byte[] saltBytes = new byte[16];

new SecureRandom().nextBytes(saltBytes);

final String accountSalt = UtilBase64.encodeToString(saltBytes);
```

The 16-byte value is an application input to `UtilArgon`; keep it per account and store it so the same value is available during verification. The method also returns a string representation produced by the Argon2 library.

## Generic type lookup and Java helpers

### `UtilGeneric`

`getGenericParameter(sourceClass, targetClass, typeIndex)` follows generic interfaces and superclass chains, substituting type variables along the way. `typeIndex` is zero-based. It returns the resolved argument as a `Class<?>` (for a parameterized argument, its raw class); it returns `null` when that argument cannot be resolved to a class.

```java
interface Repository<T> {
}

abstract class RepositoryBase<T> implements Repository<T> {
}

final class StringRepository extends RepositoryBase<String> {
}

final class Example {

    void inspectRepositoryType() {
        final Class<?> entityType = UtilGeneric.getGenericParameter(StringRepository.class, Repository.class, 0);
    }
}
```

The lookup follows `StringRepository` through `RepositoryBase<String>` to `Repository<T>`, then substitutes `T` with `String`. The type index is zero-based. The result is `String.class`; unresolved arguments return `null`. For a parameterized argument such as `List<String>`, the result is its raw class (`List.class`), not a complete reflective `Type` tree.

### `UtilJava` collection and map helpers

`createCollection` and `createMap` run a `Consumer` against a supplied collection or map and return that same instance. `updateCollection` and `updateMap` run the consumer against an existing value and return nothing. They do not allocate or copy the collection/map.

```java
final List<String> nameList = UtilJava.createCollection(new ArrayList<>(), names -> names.add("Foundation"));

UtilJava.updateCollection(nameList, names -> names.add("Utilities"));


final Map<String, Integer> countMap = UtilJava.createMap(new HashMap<>(), counts -> counts.put("modules", 5));

UtilJava.updateMap(countMap, counts -> counts.put("utilities", 1));
```

`UtilJava.cast(type, value)` returns the value when it is an instance of the requested class and `null` for a null or incompatible value.

### Reflection helpers

- `UtilClass.create` constructs an instance through a matching constructor; reflection failures are declared by the method.
- `UtilClass.formatName` formats a class name relative to a package when possible.
- `UtilField.get` and `set` read and write a supplied `Field` on an instance.
- `UtilMethod.invoke` invokes a supplied `Method`, with or without arguments.

These methods take reflection objects explicitly; they do not search for fields, constructors, or methods by themselves.

## HTTP requests

`UtilHttp` uses Java's `HttpClient`. `supply` performs a synchronous request and returns the typed `HttpResponse<T>`; `supplyAsynchronous` returns a `CompletableFuture<HttpResponse<T>>`. Both accept an `HttpResponse.BodyHandler<T>`. Convenience overloads omit the request body, content type, and headers.

```java
final HttpResponse<String> httpResponse = UtilHttp.supply(
        HttpMethod.GET,
        "https://example.com/status",
        HttpResponse.BodyHandlers.ofString()
);

if (UtilHttp.isSuccess(response)) {
    final String responseBody = httpResponse.body();
}
```

Use the full overload to provide a body, content type, and headers:

```java
final HttpResponse<String> httpResponse = UtilHttp.supply(
        HttpMethod.POST,
        endpoint,
        requestBody,
        "application/json",
        Map.of("Authorization", authorizationHeader),
        HttpResponse.BodyHandlers.ofString()
);
```

The supported methods are `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `HEAD`, and `OPTIONS`. `isInformational`, `isSuccess`, `isRedirect`, `isClientError`, `isServerError`, `isError`, `isStatus`, `isStatusBetween`, and `isStatusClass` classify responses. A non-2xx response is still an `HttpResponse`; it is not automatically an exception.

For callbacks, `dispatch` calls a success consumer or an error consumer. `dispatchAsynchronous` does the same for an asynchronous request. `supply` wraps request failures in `HttpException`; `supplyAsynchronous` reports asynchronous failures through its future. The default request timeout is 10 seconds and the default client follows normal redirects. Configure these process-wide with `setDefaultRequestTimeout` and `setHttpClient`.

## File reads and caching

`UtilFile.read` accepts a `Path`, `File`, or path string and returns the file's lines as a `List<String>`:

```java
final List<String> lineList = UtilFile.read(Path.of("config", "settings.yml"));
```

Paths are normalized to absolute paths for the cache. For cacheable files, a cached value is reused only while both the file size and last-modified timestamp match. A change to either metadata value causes the file to be read again. Read failures are thrown as `UncheckedIOException`; a null `Path` throws `IllegalArgumentException`.

The default per-file caching threshold is 512 MiB. Files above it are read without being retained in the cache. Change the threshold in mebibytes with `UtilFile.setMaxCacheableInMegaBytes`; for example, `1024L` permits caching files up to 1 GiB. This is a per-file cutoff, not a total cache-memory limit: cached entries are kept by normalized path in a static map, and the module has no public clear-all or eviction method. Applications that read many distinct files should account for that retained memory. Since freshness uses size and last-modified metadata, rewriting a file while preserving both values cannot be detected by this cache check.

## Other utilities

### `UtilBase64`

`encodeToBytes` and `encodeToString` accept UTF-8 strings or byte arrays. `decodeToBytes` and `decodeToString` provide the inverse operations. Invalid Base64 input throws `IllegalArgumentException`.

### HTTP types and functional interfaces

`HttpMethod` lists the HTTP verbs supported by `UtilHttp`; `HttpException` is its unchecked exception for synchronous request failures. `TriConsumer`, `QuadConsumer`, `TriFunction`, and `QuadFunction` provide standard functional interface shapes for three or four arguments.

## Installation and build

Requires JDK 25 and Maven. Build and install Foundation from the repository root:

```shell
mvn clean install
```

Then add Utilities to a Maven project:

```xml
<dependency>
    <groupId>me.trae.foundation</groupId>
    <artifactId>Utilities</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

The Utilities artifact brings in `de.mkammerer:argon2-jvm` for `UtilArgon`. To run its tests with reactor dependencies from the Foundation root:

```shell
mvn -pl Utilities -am test
```

See the [Foundation README](../README.md) for the other module families.
