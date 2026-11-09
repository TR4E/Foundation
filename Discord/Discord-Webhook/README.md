<h1 align="center">Discord-Webhook</h1>

<p align="center">Plain text and embed messages with synchronous and asynchronous webhook dispatch.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://discord.com/developers/docs/intro"><img alt="Discord" src="https://img.shields.io/badge/Discord-Webhooks-5865F2?logo=discord&logoColor=white"></a>
</p>

Discord-Webhook builds a message from text and embed objects, converts it to JSON, and posts it to a webhook URL. Dispatch uses Foundation's [UtilHttp](../../Utilities/README.md#http-requests), with optional callbacks for HTTP responses and request failures. The library runs in any Java application without a bot client or framework integration.

## Contents

<details>
<summary>On this page</summary>

- [Classes](#classes)
- [Quick start](#quick-start)
- [Message constructors](#message-constructors)
- [Embeds](#embeds)
- [Authors, footers, and fields](#authors-footers-and-fields)
- [Dispatch and callbacks](#dispatch-and-callbacks)
- [JSON serialization](#json-serialization)
- [Full example](#full-example)
- [Installation and build](#installation-and-build)

</details>

## Classes

| Class | Purpose |
| --- | --- |
| [DiscordWebhook](src/main/java/me/trae/foundation/discord/webhook/DiscordWebhook.java) | Webhook URL, message content, embed list, and HTTP dispatch |
| [DiscordEmbed](src/main/java/me/trae/foundation/discord/webhook/DiscordEmbed.java) | Title, description, link, color, timestamp, author, footer, and fields |
| [DiscordAuthor](src/main/java/me/trae/foundation/discord/webhook/data/DiscordAuthor.java) | Author name, link, and icon URL |
| [DiscordFooter](src/main/java/me/trae/foundation/discord/webhook/data/DiscordFooter.java) | Footer text and icon URL |
| [DiscordField](src/main/java/me/trae/foundation/discord/webhook/data/DiscordField.java) | Named value with an optional inline layout |

The message and embed classes are in `me.trae.foundation.discord.webhook`; the remaining types are in `me.trae.foundation.discord.webhook.data`. All five implement `JsonMapBuilder<String, Object>` and expose `toJsonMap()`.

## Quick start

The examples assume `webhookUrl` is supplied by your application.

```java
final DiscordWebhook discordWebhook = new DiscordWebhook(webhookUrl, "Deployment complete.");

discordWebhook.dispatchAsynchronous();
```

For a message with both content and an embed:

```java
final DiscordEmbed discordEmbed = new DiscordEmbed(
        "Deployment complete",
        "The application is ready to accept requests.",
        new Color(87, 242, 135)
);

discordEmbed.addField(new DiscordField("Environment", "Production", true));
discordEmbed.addField(new DiscordField("Version", "1.0.0", true));

discordEmbed.setFooter(new DiscordFooter("Deployment service"));

final DiscordWebhook discordWebhook = new DiscordWebhook(webhookUrl, "Release status", discordEmbed);

discordWebhook.dispatchAsynchronous();
```

`Color` is `java.awt.Color`. The embed timestamp defaults to its creation time.

## Message constructors

| Constructor arguments | Message |
| --- | --- |
| `url, content` | Text with an empty embed list |
| `url, embed` | One embed without text |
| `url, embeds` | A list of embeds without text |
| `url, content, embed` | Text with one embed |
| `url, content, embeds` | Text with a list of embeds |

`DiscordWebhook` exposes getters for `url`, `content`, and `embeds`, with no setters. Its fields are final, but a supplied embed list is retained directly and the embed objects remain mutable. Finish configuring the payload before dispatching it.

## Embeds

Create an empty `DiscordEmbed()`, or supply `title`, `title, description`, or `title, description, color`. The full constructor accepts `title, description, url, color, timestamp, fields, author, footer`. Setters configure or replace any of these properties.

| Property | Type | Default / serialization |
| --- | --- | --- |
| `title` | `String` | Omitted from JSON when null |
| `description` | `String` | Omitted when null |
| `url` | `String` | Omitted when null |
| `color` | `Color` | Omitted when null; serialized as `getRGB() & 0xFFFFFF` |
| `timestamp` | `Instant` | `Instant.now()` at creation; serialized using `toString()`, or omitted when null |
| `author` | `DiscordAuthor` | Omitted when null |
| `footer` | `DiscordFooter` | Omitted when null |
| `fields` | `List<DiscordField>` | Starts empty; omitted when empty |

```java
final DiscordEmbed discordEmbed = new DiscordEmbed("Service status");

discordEmbed.setDescription("All services are available.");

discordEmbed.setUrl("https://example.com/status");

discordEmbed.setColor(new Color(87, 242, 135));

discordEmbed.setTimestamp(null);
```

`addField(field)` appends one field, and `addFields(fields)` appends a list of fields in order. `getFields()` exposes the mutable list for removing or clearing entries. `setFields(fields)` replaces the list directly without copying it.

## Authors, footers, and fields

These three types have no-argument constructors, getters, and setters, as well as the convenience constructors below.

| Type | Constructor arguments | Properties |
| --- | --- | --- |
| `DiscordAuthor` | `name` or `name, url, iconUrl` | `name`, `url`, `iconUrl` |
| `DiscordFooter` | `text` or `text, iconUrl` | `text`, `iconUrl` |
| `DiscordField` | `name, value` or `name, value, inline` | `name`, `value`, `inline` |

The two-argument field constructor defaults `inline` to `false`. Author and footer icon URLs serialize as `icon_url`.

```java
final DiscordAuthor discordAuthor = new DiscordAuthor(
        "Deployment service",
        "https://example.com",
        "https://example.com/icon.png"
);

final DiscordFooter discordFooter = new DiscordFooter("Production", "https://example.com/icon.png");

final DiscordEmbed discordEmbed = new DiscordEmbed("Release status");

discordEmbed.setAuthor(discordAuthor);

discordEmbed.setFooter(discordFooter);

discordEmbed.addField(new DiscordField("Result", "Successful", true));
```

## Dispatch and callbacks

Both dispatch modes POST `application/json` to the supplied URL and discard the response body. Every method returns `void`.

| Method | Behavior |
| --- | --- |
| `dispatchSynchronous()` | Waits for the HTTP request, with no callbacks |
| `dispatchSynchronous(successConsumer, errorConsumer)` | Waits for the request and invokes the supplied callback on the calling thread |
| `dispatchAsynchronous()` | Starts the HTTP request without waiting for completion, with no callbacks |
| `dispatchAsynchronous(successConsumer, errorConsumer)` | Starts the request and invokes the supplied callback on completion, without scheduling it onto an application thread |

The response callback receives `HttpResponse<Void>`. Despite the parameter name `successConsumer`, it receives every completed HTTP response, including non-success status codes. Check `statusCode()` or the `UtilHttp` status helpers to distinguish accepted and rejected requests.

```java
final DiscordWebhook discordWebhook = new DiscordWebhook(webhookUrl, "Deployment complete.");

discordWebhook.dispatchAsynchronous(
        httpResponse -> System.out.println("Webhook HTTP status: %s".formatted(httpResponse.statusCode())),
        Throwable::printStackTrace
);
```

The error callback receives request failures such as connection or timeout errors. Without an error callback, those failures are consumed by `UtilHttp`. Payload construction happens before the HTTP helper is called, so serialization failures can still propagate to the caller in either mode. The library does not retry requests or queue messages for rate-limit handling.

The shared HTTP helper defaults to a ten-second request timeout. `UtilHttp.setDefaultRequestTimeout(duration)` and `UtilHttp.setHttpClient(httpClient)` configure the shared HTTP behavior for all callers, including other Foundation modules.

## JSON serialization

`toJsonMap()` returns a `LinkedHashMap` in property insertion order. Optional values can be null in the map; `UtilJson.toJson(...)` omits map entries whose values are null. Empty embed and field lists become null map values and are therefore omitted from the final JSON.

```java
final DiscordEmbed discordEmbed = new DiscordEmbed("Deployment complete");

discordEmbed.setTimestamp(null);

final DiscordWebhook discordWebhook = new DiscordWebhook(webhookUrl, discordEmbed);

final String json = UtilJson.toJson(discordWebhook.toJsonMap());
```

Produces:

```json
{
  "embeds": [
    {
      "title": "Deployment complete"
    }
  ]
}
```

The webhook URL is used only as the HTTP destination and is not included in the JSON payload. The library does not validate message lengths, embed counts, or required combinations of fields before dispatch.

## Full example

Build every payload type with all of its properties populated, then serialize the webhook's nested JSON map. The fixed timestamp makes the output reproducible. As above, `webhookUrl` is supplied by your application.

```java
final DiscordAuthor discordAuthor = new DiscordAuthor(
        "Deployment service",
        "https://example.com",
        "https://example.com/author.png"
);

final DiscordFooter discordFooter = new DiscordFooter(
        "Production deployment",
        "https://example.com/footer.png"
);

final List<DiscordField> discordFieldList = List.of(
        new DiscordField("Environment", "Production", true),
        new DiscordField("Version", "1.0.0", true),
        new DiscordField("Result", "All services are available.", false)
);

final DiscordEmbed discordEmbed = new DiscordEmbed(
        "Deployment complete",
        "The application is ready to accept requests.",
        "https://example.com/deployments/1.0.0",
        new Color(87, 242, 135),
        Instant.parse("2026-11-09T08:00:00Z"),
        discordFieldList,
        discordAuthor,
        discordFooter
);

final DiscordWebhook discordWebhook = new DiscordWebhook(
        webhookUrl,
        "Release 1.0.0 is live.",
        List.of(discordEmbed)
);

final Map<String, Object> webhookJsonMap = discordWebhook.toJsonMap();
final String json = UtilJson.toJson(webhookJsonMap);
```

`List` and `Map` are from `java.util`, `Color` is from `java.awt`, and `Instant` is from `java.time`. `UtilJson` is from `me.trae.foundation.utilities`. The webhook map includes the embed map, which includes the author, footer, and field maps produced by their respective `toJsonMap()` methods.

Produces the following JSON, expanded for readability:

```json
{
  "content": "Release 1.0.0 is live.",
  "embeds": [
    {
      "title": "Deployment complete",
      "description": "The application is ready to accept requests.",
      "url": "https://example.com/deployments/1.0.0",
      "color": 5763719,
      "timestamp": "2026-11-09T08:00:00Z",
      "author": {
        "name": "Deployment service",
        "url": "https://example.com",
        "icon_url": "https://example.com/author.png"
      },
      "footer": {
        "text": "Production deployment",
        "icon_url": "https://example.com/footer.png"
      },
      "fields": [
        {
          "name": "Environment",
          "value": "Production",
          "inline": true
        },
        {
          "name": "Version",
          "value": "1.0.0",
          "inline": true
        },
        {
          "name": "Result",
          "value": "All services are available.",
          "inline": false
        }
      ]
    }
  ]
}
```

To send this payload, call `discordWebhook.dispatchAsynchronous()` or `discordWebhook.dispatchSynchronous()` after constructing it.

## Installation and build

Requires JDK 25 and Maven. From the repository root:

```shell
mvn -pl Discord/Discord-Webhook -am install
```

Add the artifact to your application:

```xml
<dependency>
    <groupId>me.trae.foundation.discord</groupId>
    <artifactId>Discord-Webhook</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Utilities is included transitively. To compile without installing:

```shell
mvn -pl Discord/Discord-Webhook -am compile
```

Return to the [Discord README](../README.md).
