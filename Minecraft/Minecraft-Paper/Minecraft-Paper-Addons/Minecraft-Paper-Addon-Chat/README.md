<h1 align="center">Minecraft-Paper-Addon-Chat</h1>

<p align="center">Chat channels with ordered component placeholders, per-recipient formatting, and priority-based default selection.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper" src="https://img.shields.io/badge/Paper-API-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The Chat addon routes player messages through channels that define their recipients and ordered placeholders. Static placeholders are evaluated once per message, while dynamic placeholders are evaluated separately for each recipient. Placeholders return Adventure components, preserving colors, hover events, and click events. Channels can reuse placeholder components while choosing their own recipients and layout.

## Contents

<details>
<summary>On this page</summary>

- [How it works](#how-it-works)
- [Placeholders](#placeholders)
- [Creating a channel](#creating-a-channel)
- [Per-recipient placeholders](#per-recipient-placeholders)
- [Default channel priority](#default-channel-priority)
- [Channel selection](#channel-selection)
- [Events](#events)
- [Chat service](#chat-service)
- [Installation](#installation)

</details>

## How it works

`ChatService` discovers registered `ChatChannel` components and selects the default channel. `ChatListener` handles Paper's `AsyncChatEvent` and the addon's send and receive events at `HIGHEST` priority:

1. Ignore an already cancelled Paper chat event; otherwise, cancel normal delivery.
2. Dispatch `ChatSelectChannelEvent` with the sender and the current default channel.
3. If a channel is selected, dispatch `ChatSendEvent`. If none is selected, skip the message and log the player's name.
4. Evaluate static placeholders into their positions in a component array.
5. Dispatch `ChatReceiveEvent` for each player returned by the channel, giving each event its own copy of the array.
6. Fill dynamic placeholder positions for that recipient, join the components with spaces, and send the result.

Cancelling `ChatSendEvent` prevents delivery to all recipients. Cancelling `ChatReceiveEvent` prevents delivery to that recipient only.

## Placeholders

`ChatPlaceholder` is the shared marker interface. Implement one of its two subinterfaces to define when a component is evaluated:

| Interface | Method | Evaluated |
| --- | --- | --- |
| `StaticChatPlaceholder` | `apply(sender, message)` | Once per sent message, before recipient iteration |
| `DynamicChatPlaceholder` | `apply(sender, recipient, message)` | Once per recipient |

Static means independent of the recipient. A static placeholder can still return a different result for each message. Dynamic placeholders can use the recipient to choose colors or content.

The following examples define reusable username and message placeholders in a consuming plugin. Examples omit imports; place public types in separate files under the plugin's package.

```java
@Singleton
public final class UsernameChatPlaceholder implements StaticChatPlaceholder {

    @Override
    public Component apply(final Player sender, final Component message) {
        return Component.text(sender.getName(), NamedTextColor.YELLOW);
    }
}
```

```java
@Singleton
public final class MessageChatPlaceholder implements StaticChatPlaceholder {

    @Override
    public Component apply(final Player sender, final Component message) {
        return message;
    }
}
```

Return `Component.empty()` to omit an optional part. The join helper skips `null` results and `Component.empty()`, adding spaces between the remaining parts. Include punctuation within a component or provide a separate placeholder for it.

Placeholders can receive plugin services through constructor injection. Each channel chooses the implementations it needs.

## Creating a channel

Implement `ChatChannel` for a selectable channel, or `DefaultChatChannel` to also participate in default selection. This global channel uses the two static placeholders defined above:

```java
@AllArgsConstructor
@Singleton
public final class GlobalChatChannel implements DefaultChatChannel {

    private final UsernameChatPlaceholder usernameChatPlaceholder;

    private final MessageChatPlaceholder messageChatPlaceholder;

    @Override
    public String getIdentifier() {
        return "global";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public List<Player> getRecipients(final Player sender) {
        return UtilServer.getOnlinePlayers();
    }

    @Override
    public List<ChatPlaceholder> getPlaceholders() {
        return List.of(this.usernameChatPlaceholder, this.messageChatPlaceholder);
    }
}
```

Plugin components use Injector's `@Singleton`. The addon's internal listener and service use `@AddonSingleton` and are registered by the Plugin Framework.

| Method | Returns | Purpose |
| --- | --- | --- |
| `getIdentifier()` | `String` | Unique channel identifier used for lookup |
| `getRecipients(sender)` | `List<Player>` | Players who should receive the message, including the sender if desired |
| `getPlaceholders()` | `List<ChatPlaceholder>` | Message parts in display order |
| `getPriority()` | `int` | Default selection priority; required by `DefaultChatChannel` |

The placeholder list controls the format. Different channels can compose shared and plugin-specific placeholders:

```text
Global:   USERNAME MESSAGE
Prefixed: PREFIX USERNAME MESSAGE
```

The listener takes an immutable snapshot of the placeholder list once per message. Static and dynamic results use that same ordering for every recipient. Implement either `StaticChatPlaceholder` or `DynamicChatPlaceholder`; the base `ChatPlaceholder` interface is sealed to those two subtypes.

## Per-recipient placeholders

Implement `DynamicChatPlaceholder` when the result depends on who receives the message. This example highlights the sender's own username differently:

```java
@Singleton
public final class RecipientUsernameChatPlaceholder implements DynamicChatPlaceholder {

    @Override
    public Component apply(final Player sender, final Player recipient, final Component message) {
        return Component.text(
                sender.getName(),
                sender.equals(recipient) ? NamedTextColor.GOLD : NamedTextColor.YELLOW
        );
    }
}
```

A channel can inject this implementation in place of `UsernameChatPlaceholder`. Viewer-specific colors, hover text, and visibility rules can use the same recipient-aware method.

Placeholder instances can be reused across messages. Keep message and recipient data in method locals, since separate chat messages can be processed concurrently. The selection, send, and receive events are asynchronous; use dependencies and APIs that support access from that execution thread.

## Default channel priority

`ChatService` chooses the registered `DefaultChatChannel` with the lowest numerical priority. A channel at priority `0` takes precedence over one at `100`. Register a higher numerical priority for a general fallback and a lower value for a plugin-specific default.

Channels implementing only `ChatChannel` remain available for lookup and explicit selection, but do not participate in default selection. Default priority is global; per-player routing belongs in `ChatSelectChannelEvent`.

When no default exists, selection begins with a `null` channel. A selection listener can still supply one. If the channel remains `null`, the message is skipped.

## Channel selection

`ChatService.getSelectedChannel(player)` dispatches `ChatSelectChannelEvent` each time it is called. Listeners can replace its channel with `setChannel(...)`, or set it to `null` to suppress routing.

This example routes players with `chat.staff` permission to a registered `staff` channel when one is available:

```java
@AllArgsConstructor
@Singleton
public final class StaffChatSelectionListener implements Listener {

    private final ChatService chatService;

    @EventHandler
    public void onChatSelectChannel(final ChatSelectChannelEvent event) {
        if (event.getSender().hasPermission("chat.staff")) {
            this.chatService.getChannelByIdentifier("staff").ifPresent(event::setChannel);
        }
    }
}
```

Your plugin can also store a player's selected channel and apply it through this event. The addon resolves selection for each message; it does not store per-player selections or provide a channel-switching command.

## Events

| Event | Execution | Data | Customization |
| --- | --- | --- | --- |
| `ChatSelectChannelEvent` | Asynchronous | Sender, selected channel | Replace or clear the channel |
| `ChatSendEvent` | Asynchronous | Sender, channel, message | Cancel delivery to all recipients |
| `ChatReceiveEvent` | Asynchronous | Sender, recipient, channel, message, placeholder snapshot, component array | Cancel delivery to one recipient or edit that recipient's component array |
| `ChatSwitchChannelEvent` | Synchronous | Player, previous channel, new channel | Notification type for plugin-managed channel changes |

Run send and receive cancellation or component-editing handlers before the addon's `HIGHEST` handlers, such as at the default `NORMAL` priority.

`ChatReceiveEvent.getComponents()` exposes the recipient's own array. Static positions are already populated when the event is dispatched; dynamic positions are filled by `ChatListener`. Earlier listeners can change static entries for that recipient. Values assigned to dynamic positions before rendering are replaced by the corresponding dynamic placeholder.

The sender, recipient, message, and channel on send and receive events are read-only references. `ChatSelectChannelEvent` provides the channel setter.

`ChatSwitchChannelEvent` is available for a consuming plugin to dispatch when it changes its own selection state. The addon does not dispatch it automatically.

## Chat service

Inject `ChatService` into plugin components to resolve channels:

| Method | Returns | Purpose |
| --- | --- | --- |
| `getChannelByIdentifier(identifier)` | `Optional<ChatChannel>` | Case-insensitive lookup; empty for `null` or an unknown identifier |
| `getSelectedChannel(player)` | `Optional<ChatChannel>` | Dispatch selection with the current default and return the chosen channel |

Use unique identifiers across registered channels. Lookup normalizes identifiers with `Locale.ROOT`.

The registry follows the injected live channel collections. On dependency updates, the service publishes a complete, unmodifiable lookup map and recalculates the default channel. Lookup snapshots and the default channel are published through volatile fields for visibility to chat threads.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Chat</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Declare the addon on your plugin class:

```java
@Addons({ChatAddon.class})
public final class MyPlugin extends PaperPlugin {

}
```

Provide your channel and placeholder components under the plugin's package. The [Plugin Framework](../../Minecraft-Paper-Plugin-Framework/README.md) manages addon hosting and listener registration.

Does not require direct Paper NMS access. Return to the [Addons README](../README.md).
