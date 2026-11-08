<h1 align="center">Minecraft-Paper-Addon-ResourcePack</h1>

<p align="center">Per-player resource packs with eligibility checks, required-pack enforcement, and loading callbacks.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper" src="https://img.shields.io/badge/Paper-API-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The ResourcePack addon manages registered resource packs through Paper's Adventure resource pack API. Packs define their download URL, hash, eligibility, and whether acceptance is required. The addon sends eligible packs, removes packs that become ineligible, and tracks each player's pending and successfully loaded packs by UUID and hash so updated packs can be sent again.

## Contents

<details>
<summary>On this page</summary>

- [How it works](#how-it-works)
- [Creating a resource pack](#creating-a-resource-pack)
- [Eligibility and synchronization](#eligibility-and-synchronization)
- [Loading status](#loading-status)
- [Events and callbacks](#events-and-callbacks)
- [Settings](#settings)
- [Resource pack service](#resource-pack-service)
- [Pack metadata](#pack-metadata)
- [Installation](#installation)

</details>

## How it works

`ResourcePackService` discovers registered `ResourcePack` components. `ResourcePackListener` synchronizes a player's packs when they join or change worlds:

1. Refresh each pack's cached metadata if its URL or hash has changed, then evaluate its global and per-player eligibility.
2. Remove tracked packs that are no longer eligible from both the player and the addon's loaded/pending state.
3. Collect eligible packs that are neither loaded nor pending with the current hash, clearing outdated tracking and separating required and optional packs.
4. Call `ResourcePackAddonSettings.onApplying(player)` if any new packs need sending.
5. Send the required batch followed by the optional batch, using `replace(false)` to preserve other packs.

Before each batch is sent, every pack in that batch is marked pending, its `ResourcePackSendEvent` is dispatched, and its `onLoading(player)` callback runs. Client status events then update the tracked state. Player state is cleared on quit.

## Creating a resource pack

Extend `ResourcePack` and register the implementation with Injector's `@Singleton` under your plugin's package. Examples omit imports.

```java
@Singleton
public final class ServerResourcePack extends ResourcePack {

    public ServerResourcePack() {
        super("d54bc3bb-0bb1-4b0c-9177-46c20bd711ae", "SERVER");
    }

    @Override
    public String getUrl() {
        return "https://example.com/server-resource-pack.zip";
    }

    @Override
    public String getHash() {
        return "0000000000000000000000000000000000000000";
    }

    @Override
    public boolean isRequired() {
        return true;
    }
}
```

Replace the example URL with your hosted pack ZIP and the placeholder hash with that ZIP's SHA-1 hash. Give each pack a unique, stable UUID. The constructor parses the UUID string; the identifier is a descriptive name exposed by `getIdentifier()`. Lookup and player tracking use the UUID.

`isRequired()` defaults to `false`. Override it to make the pack required.

## Eligibility and synchronization

Both `canLoad()` and `canLoad(player)` must return `true` for a pack to be eligible. Both default to `true`. Override the global method for a server-wide toggle and the player method for permissions, worlds, or other player-specific conditions:

```java
@Override
public boolean canLoad(final Player player) {
    return player.getWorld().getName().equals("lobby");
}
```

The listener synchronizes automatically on join and world changes. Resource pack subclasses only need to define their pack metadata, eligibility, and optional callbacks; they do not need to obtain the service or call synchronization themselves.

For changes that should take effect before the next join or world change, inject `ResourcePackService` into a separate plugin listener or service and call `this.resourcePackService.sync(player)`. This reevaluates all registered packs for that player.

Eligible packs that are already loaded or pending with the current hash are skipped. If a tracked pack's hash changes, synchronization clears its old loaded/pending state and queues it for sending again under the same UUID. Ineligible tracked packs are removed with `Player.removeResourcePacks(...)`. The addon does not periodically poll eligibility or automatically synchronize players when registered components change.

## Loading status

The listener handles a status only when the player has tracked data, the pack UUID resolves to a registered pack, and that pack is pending for the player.

| Client status | Addon behavior |
| --- | --- |
| `SUCCESSFULLY_LOADED` | Remove pending state, mark loaded, dispatch `ResourcePackLoadedEvent`, and call the pack's `onLoaded(player)` |
| `DECLINED`, `FAILED_DOWNLOAD`, `INVALID_URL`, `FAILED_RELOAD` | Remove pending state; for a required pack, clear all addon tracking for the player and kick them with the configured message |
| `DISCARDED` | Remove pending state without kicking the player |
| Other statuses | Leave the pack pending |

After a successful load and its event/callback, the listener calls `ResourcePackAddonSettings.onApplied(player)` if no packs remain pending. This check covers both required and optional packs. It checks pending state rather than verifying that every eligible pack loaded successfully: an optional failure can already have removed another pack from pending state. Failure and discard statuses do not themselves invoke `onApplied`.

A failed optional pack can be sent again on a later synchronization because it is neither loaded nor pending.

## Events and callbacks

| Hook | When it runs | Data |
| --- | --- | --- |
| `ResourcePackSendEvent` | After marking a pack pending, before its loading callback and batch request | Resource pack and player |
| `ResourcePack.onLoading(player)` | After the send event, before the batch request | Player |
| `ResourcePackLoadedEvent` | After a successful status updates the player's tracking | Resource pack and player |
| `ResourcePack.onLoaded(player)` | After the loaded event | Player |

Both events extend the framework's synchronous `CustomEvent`, expose `getResourcePack()` and `getPlayer()`, and are not cancellable. The callbacks default to empty implementations. A send event reports that a pack is about to be requested; successful application is reported by the loaded event.

## Settings

Supply an Injector `@Singleton` component implementing `ResourcePackAddonSettings` under your plugin's package to override the defaults. See the framework's [addon settings guide](../../Minecraft-Paper-Plugin-Framework/README.md#addon-settings) for how overrides are selected.

| Method | Default behavior |
| --- | --- |
| `onApplying(player)` | Show a gold "Applying resource packs" subtitle |
| `onApplied(player)` | Show a green "Applied resource packs" subtitle |
| `getKickMessage(resourcePack)` | Explain that the server pack must be accepted and ask the player to enable Server Resource Packs in their server list entry, then reconnect |

Both default titles use an empty main title with a 250 ms fade-in, 2,000 ms stay, and 250 ms fade-out. `onApplying` runs once per synchronization that sends new packs, before either batch is marked pending. `onApplied` runs under the successful-load conditions described above.

## Resource pack service

Inject `ResourcePackService` into separate plugin components, such as listeners or services, when you need explicit synchronization or access to player tracking. Keep this outside your `ResourcePack` subclasses; the addon handles their normal synchronization automatically.

| Method | Returns | Purpose |
| --- | --- | --- |
| `sync(player)` | `void` | Refresh metadata, reevaluate eligibility, remove ineligible tracked packs, and send new or hash-changed eligible packs |
| `getResourcePackByUniqueId(uniqueId)` | `Optional<ResourcePack>` | Find a registered pack by UUID |
| `getPlayerResourcePackDataByPlayer(player)` | `Optional<PlayerResourcePackData>` | Read the player's current tracking object, if present |
| `cleanup(player)` | `void` | Remove the player's addon tracking without sending a client removal request |

`PlayerResourcePackData` stores loaded and pending hashes by pack UUID. It provides `isLoaded(resourcePack)`, `isPending(resourcePack)`, `isAnyLoaded()`, and `isAnyPending()` to inspect presence. `isCurrent(resourcePack)` compares the pack's current hash with its tracked loaded hash, or its pending hash when no loaded entry exists. State is held in memory for the current session; it is not persisted across reconnects.

The UUID lookup map is rebuilt when live dependencies update. Use unique UUIDs across all registered packs.

## Pack metadata

`ResourcePack` implements Adventure's `ResourcePackInfoLike`. `asResourcePackInfo()` lazily builds and caches metadata from the UUID, `getUrl()`, and `getHash()`.

- `revalidate()` rebuilds the cached metadata from the current URL and hash.
- `isValidated()` returns `true` only when cached metadata exists and its URL and hash still match the current values.

On every synchronization, the service checks `isValidated()` and calls `revalidate()` when needed, so pack subclasses do not need to refresh metadata themselves. Calling `revalidate()` directly only refreshes the cache; it does not send a request or clear player tracking.

Changing a pack's hash causes it to be sent again on the next synchronization, even when its UUID is already loaded or pending. Changing only the URL refreshes the metadata but does not resend a tracked pack whose hash is unchanged. Update the hash whenever the ZIP contents change.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-ResourcePack</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Declare the addon on your plugin class:

```java
@Addons({ResourcePackAddon.class})
public final class MyPlugin extends PaperPlugin {

    @Override
    public String getPluginName() {
        return "MyPlugin";
    }
}
```

Provide your pack components under the plugin's package. The [Plugin Framework](../../Minecraft-Paper-Plugin-Framework/README.md) manages addon hosting and listener registration.

Does not require direct Paper NMS access. Return to the [Addons README](../README.md).
