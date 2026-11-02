<h1 align="center">Minecraft-Paper-Addon-Item</h1>

<p align="center">Custom items with activation mechanics, cooldown hooks, versioned updates, optional instance tracking, and crafting prevention.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper" src="https://img.shields.io/badge/Paper-API-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The Item addon provides a framework for defining custom items that are identified by persistent data, automatically styled, version-tracked, and protected from unintended crafting. Items can react to player interactions through an activation system with cooldown hooks and channeling support.

## Contents

<details>
<summary>On this page</summary>

- [Item hierarchy](#item-hierarchy)
- [Base item](#base-item)
- [Default item](#default-item)
- [Custom item](#custom-item)
- [Activatable custom item](#activatable-custom-item)
- [Single activatable custom item](#single-activatable-custom-item)
- [Channel custom item](#channel-custom-item)
- [Styling](#styling)
- [Persistent data stamping](#persistent-data-stamping)
- [Item tracking](#item-tracking)
- [Dynamic lore](#dynamic-lore)
- [Crafting prevention](#crafting-prevention)
- [Inventory synchronization](#inventory-synchronization)
- [Item service](#item-service)
- [Item search](#item-search)
- [Events](#events)
- [Installation](#installation)

</details>

## Item hierarchy

Items form an inheritance chain from simple definitions to fully interactive tools:

| Class | Adds |
| --- | --- |
| `Item` | Base: material, name, lore, color, style, display name formatting |
| `CustomItem` | Persistent unique ID, typable identifier, version tracking, optional instance tracking, update/refresh lifecycle |
| `ActivatableCustomItem` | Multiple activation types (left click, right click, drop, swap hand) |
| `SingleActivatableCustomItem` | Single activation type with simplified callbacks and cooldown hooks |
| `ChannelCustomItem` | Sustained right-click activation with start/channel/stop lifecycle |
| `DefaultItem` | Material-based fallback for vanilla items, with no custom unique ID or identifier |

`ItemService` discovers registered `CustomItem` components through an injected live list and manages the apply/update/refresh lifecycle. Lookup maps are rebuilt when live dependencies change. Fallback `DefaultItem` definitions are created lazily through `ItemAddonSettings.createDefaultItem(material)` and cached per material.

## Base item

`Item` is the abstract base for all items. It defines the material, display name, lore, and visual properties.

```java
public class InfoBook extends Item {

    public InfoBook() {
        super(Material.BOOK);
    }

    @Override
    public String getName() {
        return "Info Book";
    }

    @Override
    public List<String> getLore() {
        return List.of(
                "Contains server information",
                "and helpful tips."
        );
    }

    @Override
    public java.awt.Color getColor() {
        return new java.awt.Color(100, 200, 255);
    }
}
```

`getName()` and lore lines are deserialized through `UtilMessage`, so they can contain supported text markup. The display name uses `getColor()` and `getDecorations()` as fallback styling; explicit formatting in the name takes precedence. Without a style, the name defaults to white. Names are non-italic unless explicitly styled otherwise, while lore lines default to white and always have italics disabled. Returning `null` from `getName()` clears the custom display name.

### Overridable methods

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `getName()` | `String` | _abstract_ | Display name with supported text markup; `null` clears it |
| `getLore()` | `List<String>` | _abstract_ | Lore lines (default color is white) |
| `getColor()` | `java.awt.Color` | Style color, otherwise `ChatColor.WHITE.getColor()` | Fallback display name color |
| `getDecorations()` | `List<TextDecoration>` | Style decorations, otherwise `null` | Fallback display name decorations (bold, italic, etc.) |
| `getStyle()` | `ItemStyle` | `null` | Mass style override (color, decorations, tooltip, tag) |
| `getModel()` | `NamespacedKey` | `null` | Custom model for resource packs |
| `getTooltipStyle()` | `NamespacedKey` | Style tooltip, otherwise `null` | Resource pack tooltip style |
| `hideAttributes()` | `boolean` | `false` | Hide all item flags |
| `getMaxStackSize()` | `int` | Material default | Custom max stack size |
| `addGlow()` | `Boolean` | `null` | Enchantment glint override (`true` = always, `false` = never, `null` = default) |
| `stamp(ItemMeta)` | `void` | no-op | Write custom persistent data during creation and updates; skipped by `createView()`; final in `CustomItem` |
| `editMeta(ItemMeta)` | `void` | no-op | Final item meta adjustments after all other properties are applied |

## Default item

`DefaultItem` extends `Item` with a material-only constructor. Its `getName()` and `getLore()` methods both return `null`; it supplies no custom name or lore of its own. Override `ItemAddonSettings.createDefaultItem(material)` to supply a specialized default definition for each material. The service caches the returned definition on first use.

For ordinary stacks without a custom unique ID or version marker, `ItemService.apply(...)` calls `DefaultItem.refresh(...)`. This dispatches `ItemStackUpdateEvent` without reapplying definition metadata, preserving existing names, lore, enchantments, and other metadata unless an event listener changes them. Overriding a default definition's styling methods alone does not apply that styling during refresh; use the update event for changes to ordinary stacks.

An unresolved custom stack that reaches the fallback and still carries a unique ID or version marker instead uses `DefaultItem.create(itemStack)`. This builds a new stack from the default definition, carrying over only the amount and damage rather than copying the old metadata. Naturally obtainable conversion and deletion checks run before this fallback.

## Custom item

`CustomItem` takes `(material, uniqueId, identifier)`. Its persistent identity, typable name, and display name have separate roles:

| Value | Example | Purpose |
| --- | --- | --- |
| `getUniqueId()` | `d4f7a8b2-1c3e-4f5a-9b6d-2e8f0a1c3d5e` | Stable definition identity stored on item stacks for lookup and comparison |
| `getIdentifier()` | `HEALING_WAND` | Static, typable name used for in-game search and suggestions |
| `getName()` | `Healing Wand` | Raw display name used when rendering the item |

Supply a stable `uniqueId` in the constructor; all stacks of the same definition share it. The examples use UUID strings, but the addon does not generate this value or require a UUID format. Keep it unchanged when renaming the identifier or display name so existing stacks still resolve to the same definition.

```java
@Singleton
public class HealingWand extends CustomItem {

    public HealingWand() {
        super(Material.STICK, "d4f7a8b2-1c3e-4f5a-9b6d-2e8f0a1c3d5e", "HEALING_WAND");
    }

    @Override
    public String getName() {
        return "Healing Wand";
    }

    @Override
    public List<String> getLore() {
        return List.of(
                "A magical wand that",
                "restores health."
        );
    }

    @Override
    public java.awt.Color getColor() {
        return java.awt.Color.GREEN;
    }

    @Override
    public Boolean addGlow() {
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
```

### Additional overridable methods

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `hideAttributes()` | `boolean` | `true` | Custom items hide attributes by default |
| `isInvincible()` | `boolean` | `false` | Cancel damage and combustion events for the dropped item |
| `canDespawn()` | `boolean` | `true` | Allow the dropped item to despawn naturally; return `false` to cancel its despawn event |
| `isTrackable()` | `boolean` | `false` | Enable an instance tracking ID when `getMaxStackSize()` is `1` |
| `removeVanillaUseCooldown()` | `boolean` | `true` | Remove the vanilla use cooldown animation |
| `naturallyObtainable()` | `boolean` | `false` | Whether the item can appear in natural loot |
| `deleteIfRemoved()` | `boolean` | `false` | Delete the item on next inventory sync if the definition is removed |
| `generateVersionEntries()` | `List<String>` | Visual configuration, tracking, and deletion settings | Protected hook supplying entries for the definition's version hash |

Invincibility and despawning are independent: override both `isInvincible()` to return `true` and `canDespawn()` to return `false` to protect dropped items from damage, combustion, and natural despawning.

## Activatable custom item

`ActivatableCustomItem` extends `CustomItem` with support for multiple activation types. Override `getSupportedActivateTypes()` to define which interactions trigger the item.

```java
@Singleton
public class CombatKnife extends ActivatableCustomItem {

    public CombatKnife() {
        super(Material.IRON_SWORD, "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "COMBAT_KNIFE");
    }

    @Override
    public String getName() {
        return "Combat Knife";
    }

    @Override
    public List<String> getLore() {
        return List.of(
                "Left-click to slash",
                "Right-click to throw"
        );
    }

    @Override
    public Set<ActivateType> getSupportedActivateTypes() {
        return Set.of(ActivateType.LEFT_CLICK, ActivateType.RIGHT_CLICK);
    }

    @Override
    public void onActivate(final Player player, final ItemStack itemStack, final Block clickedBlock, final ActivateType activateType) {
        if (activateType == ActivateType.LEFT_CLICK) {
            // Slash logic
        } else {
            // Throw logic
        }
    }

    @Override
    public long getCooldownDuration(final ActivateType activateType) {
        return activateType == ActivateType.LEFT_CLICK ? 500L : 2000L;
    }
}
```

### Activation types

| Type | Trigger |
| --- | --- |
| `LEFT_CLICK` | Left-click air or block with the main-hand item |
| `RIGHT_CLICK` | Right-click air or block with the main-hand item |
| `DROP_ITEM` | Player attempts to drop the item; the drop is cancelled when handled as an activation |
| `SWAP_HAND` | Player attempts to swap the main-hand item into the off hand; the swap is cancelled when handled as an activation |

### Additional overridable methods

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `getSupportedActivateTypes()` | `Set<ActivateType>` | _abstract_ | Which interactions trigger activation |
| `onActivate(player, itemStack, block, type)` | `void` | _abstract_ | Called when the item is activated |
| `canActivate(player, itemStack, block, type)` | `boolean` | `true` | Pre-activation check |
| `useItemInHand(player, itemStack, type)` | `Event.Result` | `DENY` for block items or right-click usable items, else `DEFAULT` | Control vanilla item-in-hand behavior |
| `useInteractedBlock(player, itemStack, block, type)` | `Event.Result` | `DEFAULT` | Control vanilla block interaction |
| `activateOnItemUse(player, itemStack, type)` | `boolean` | `true` | Allow activation when right-clicking with a usable material |
| `activateOnBlockUse(player, itemStack, block, type)` | `boolean` | `false` | Allow activation when right-clicking a block that responds to the interaction |
| `getCooldownName(type)` | `String` | `getName()` | Cooldown name hook; override to make it independent of the display name |
| `getCooldownDuration(type)` | `long` | `0L` | Cooldown duration hook in milliseconds |

### Interaction flow

For supported main-hand clicks, the listener checks the activation gates before setting vanilla interaction results. Returning `false` from `activateOnItemUse(...)` skips activation on right-click with a usable material. With `activateOnBlockUse(...)` returning `false`, right-clicking an interactable block (including a block usable with the held item) skips activation unless the player is sneaking. These early returns leave the interaction results untouched.

If the gates pass, `useItemInHand(...)` and `useInteractedBlock(...)` control the vanilla actions separately from custom activation. Allowing activation on a block does not itself deny the block's vanilla action; return `Event.Result.DENY` from `useInteractedBlock(...)` when that is needed.

When the resulting item-in-hand action is `Event.Result.DENY`, `ItemActivateListener` calls `player.updateInventory()` through `UtilTask.executeSynchronously(...)` to resynchronize the client inventory and address ghost block placement or item-count desynchronization after denied use. This happens before the custom activation sequence, even if `ItemPreActivateEvent` or `canActivate(...)` subsequently prevents activation. Interactions skipped by the activation gates do not reach this synchronization step.

The activation sequence is `ItemPreActivateEvent`, then `canActivate(...)`, then `onActivate(...)`, followed by `ItemPostActivateEvent`. Cancelling the pre-event or returning `false` from `canActivate(...)` stops custom activation. The clicked block is `null` for air clicks, drops, and hand swaps. Drops and hand swaps use this same sequence without the click-specific activation gates or vanilla interaction hooks.

`getCooldownName(...)` and `getCooldownDuration(...)` expose configuration for consuming code. The addon's activation listener does not check or start cooldowns automatically; overriding the duration alone does not delay subsequent activations.

## Single activatable custom item

`SingleActivatableCustomItem` simplifies `ActivatableCustomItem` for items that respond to a single interaction type. Pass the activation type in the constructor and override the simplified methods below. Final adapters route the parent callbacks to these methods, and `getSupportedActivateTypes()` returns only the configured type. The typed `canActivate(...)` adapter also rejects other activation types.

```java
@Singleton
public class TeleportPearl extends SingleActivatableCustomItem {

    public TeleportPearl() {
        super(Material.ENDER_PEARL, "b7c8d9e0-f1a2-3456-7890-abcdef123456", "TELEPORT_PEARL", ActivateType.RIGHT_CLICK);
    }

    @Override
    public String getName() {
        return "Teleport Pearl";
    }

    @Override
    public List<String> getLore() {
        return List.of(
                "Right-click to teleport",
                "to your target location."
        );
    }

    @Override
    public boolean removeVanillaUseCooldown() {
        return true;
    }

    @Override
    public boolean canActivate(final Player player, final ItemStack itemStack, final Block clickedBlock) {
        return player.getHealth() > 2.0;
    }

    @Override
    public void onActivate(final Player player, final ItemStack itemStack, final Block clickedBlock) {
        // Teleport logic
    }

    @Override
    public long getCooldownDuration() {
        return 5000L;
    }
}
```

### Simplified overridable methods

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `onActivate(player, itemStack, block)` | `void` | _abstract_ | Called when the item is activated |
| `canActivate(player, itemStack, block)` | `boolean` | `true` | Pre-activation check |
| `useItemInHand(player, itemStack)` | `Event.Result` | Parent behavior for the configured type: `DENY` for block items or right-click usable items, else `DEFAULT` | Control vanilla item-in-hand behavior |
| `useInteractedBlock(player, itemStack, block)` | `Event.Result` | `DEFAULT` | Control vanilla block interaction |
| `getCooldownName()` | `String` | `getName()` | Cooldown name hook |
| `getCooldownDuration()` | `long` | `0L` | Cooldown duration hook in milliseconds |

`activateOnItemUse(player, itemStack, type)` and `activateOnBlockUse(player, itemStack, block, type)` remain inherited with their `ActivateType` parameter and defaults of `true` and `false`. There are no simplified overloads for these gates. The interaction flow and cooldown behavior described above also apply to single-activation items.

## Channel custom item

`ChannelCustomItem` extends `SingleActivatableCustomItem` with sustained right-click activation. When the player begins the interaction, `onStart` fires. On each subsequent tick while the player holds the interaction, `onChannel` is called. When the player releases or the item is unequipped, `onStop` fires.

```java
@Singleton
public class ChargeBeam extends ChannelCustomItem {

    public ChargeBeam() {
        super(Material.BLAZE_ROD, "c9d0e1f2-a3b4-5678-9012-cdef34567890", "CHARGE_BEAM");
    }

    @Override
    public String getName() {
        return "Charge Beam";
    }

    @Override
    public List<String> getLore() {
        return List.of(
                "Hold right-click to charge",
                "Release to fire."
        );
    }

    @Override
    public java.awt.Color getColor() {
        return java.awt.Color.ORANGE;
    }

    @Override
    public boolean canChannel(final Player player, final ItemStack itemStack) {
        return player.getFoodLevel() > 0;
    }

    @Override
    public void onStart(final Player player, final ItemStack itemStack) {
        // Begin charging
    }

    @Override
    public void onChannel(final Player player, final ItemStack itemStack) {
        // Tick while held
    }

    @Override
    public void onStop(final Player player, final ItemStack itemStack) {
        // Fire the beam
    }
}
```

The constructor takes material, unique ID, and identifier. The activation type is always `RIGHT_CLICK`.

### Additional overridable methods

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `canChannel(player, itemStack)` | `boolean` | `true` | Whether channeling can continue |
| `onStart(player, itemStack)` | `void` | no-op | Called when channeling begins |
| `onChannel(player, itemStack)` | `void` | _abstract_ | Called each tick while channeling |
| `onStop(player, itemStack)` | `void` | no-op | Called when channeling ends |

Utility methods: `isChanneling(player)` checks if a player is currently channeling, and `getChannelingPlayers()` returns all players currently channeling this item.

## Styling

Items define their appearance through color, decorations, and an optional `ItemStyle` object for mass configuration:

```java
@Singleton
public class LegendaryBlade extends SingleActivatableCustomItem {

    private static final ItemStyle LEGENDARY_STYLE = ItemStyle.of(
            "Legendary",
            new java.awt.Color(255, 215, 0),
            List.of(TextDecoration.BOLD),
            new NamespacedKey("myplugin", "legendary_tooltip"),
            "\uE001"
    );

    public LegendaryBlade() {
        super(Material.NETHERITE_SWORD, "e1f2a3b4-c5d6-7890-1234-567890abcdef", "LEGENDARY_BLADE", ActivateType.LEFT_CLICK);
    }

    @Override
    public String getName() {
        return "Blade of Legends";
    }

    @Override
    public List<String> getLore() {
        return List.of(
                "Forged in ancient flames",
                "",
                "Damage: +12"
        );
    }

    @Override
    public ItemStyle getStyle() {
        return LEGENDARY_STYLE;
    }

    @Override
    public void onActivate(final Player player, final ItemStack itemStack, final Block clickedBlock) {
        // Attack logic
    }
}
```

When `getStyle()` is set, it provides `getColor()`, `getDecorations()`, and `getTooltipStyle()` through the style object. The style's `tag` field renders a resource pack font tag below the lore. You can also use `ItemStyle.of(name, color, tooltipStyle, tag)` without decorations.

The base `getColor()`, `getDecorations()`, and `getTooltipStyle()` methods read from the style when present. Override an individual method to provide a different value.

## Persistent data stamping

Custom items are identified through Bukkit's `PersistentDataContainer` on the item meta:

| Key | Type | Purpose |
| --- | --- | --- |
| `custom:item_unique_id` | `String` | Shared definition `uniqueId` supplied to `CustomItem` |
| `custom:item_version` | `String` | SHA-256 hash of the item's current configuration for update detection |
| `custom:item_deletable` | `Boolean` | Allows removal during synchronization when the custom definition no longer resolves |
| `custom:item_tracking_id` | `String` | UUID for an individually tracked item; enabled only for trackable definitions with a stack limit of `1` |
| `custom:item_dynamic_lore` | Container | Nested container for dynamic lore key-value pairs |

During synchronization, registered custom items are checked against their definition's version. Outdated items are updated, preserving durability. If a definition no longer resolves and no naturally obtainable replacement applies, a deletable item is removed; otherwise, a stack retaining a custom unique ID or version marker is recreated through its material's cached default definition. Ordinary stacks are refreshed without rebuilding metadata.

`generateVersionEntries()` includes material, attribute visibility, stack limit, glint, style name and tag, model, tooltip style, color, decorations, name, lore templates, tracking, and deletion settings. Entries are joined with a null separator and hashed with SHA-256. The version is computed lazily and cached per definition instance. Override this protected hook to include additional configuration, retaining the superclass entries when those should still trigger updates. Per-stack dynamic lore values, `isInvincible()`, and `canDespawn()` are not included in the default hash.

### Creation and updates

| Method | Behaviour |
| --- | --- |
| `create()` | Build a new, stamped item with an amount of `1` |
| `create(amount)` / `create(amount, durability)` | Build a new, stamped stack with the supplied amount and damage |
| `create(itemStack)` | Build a new stack using the supplied stack's amount and damage; does not copy its other metadata |
| `createView()` | Build a display stack without calling `stamp`, so custom unique IDs, versions, and tracking IDs are not added by that hook |
| `update(itemStack)` | Apply the current definition and stamp metadata, changing material when needed; use the returned stack |
| `refresh(itemStack)` | Dispatch `ItemStackUpdateEvent` and return the existing stack without restamping its metadata |

## Item tracking

Enable tracking on a custom item by overriding both methods:

```java
@Override
public boolean isTrackable() {
    return true;
}

@Override
public int getMaxStackSize() {
    return 1;
}
```

When stamped, the item receives a random UUID at `CustomItem.TRACKING_ID_KEY` if it does not already have one. Stamped updates preserve an existing ID. When tracking is disabled or the definition's stack limit is greater than `1`, stamping removes the tracking key. The tracking setting is included in the definition's version hash.

The definition unique ID and tracking ID serve different purposes:

| Value | Shared by |
| --- | --- |
| `custom:item_unique_id` | Every stack belonging to the same `CustomItem` definition |
| `custom:item_tracking_id` | One individually tracked item |

Ordinary stackable items do not receive tracking IDs from `CustomItem.stamp`. For tracked items, call `create()` separately for each item you issue. `create(amount)` does not enforce the stack limit. Cloning an existing stack or reusing a serialized recipe result also copies its tracking ID, so those operations do not issue a new identity. Use `createView()` for display-only stacks; it skips definition stamping as well as tracking.

Inject `ItemTrackingService` to inspect existing stacks:

| Method | Returns | Behaviour |
| --- | --- | --- |
| `getTrackingIdByItemStack(itemStack)` | `Optional<String>` | Read the stored ID when the stack resolves to a currently registered, trackable definition |
| `findDuplicateTrackingIdSet(contents)` | `Set<String>` | Return an unmodifiable set of IDs repeated across slots or carried by a stack whose amount exceeds `1` |

The scan skips null, empty, and untracked stacks. It only compares the supplied array: scanning two inventories separately does not detect an ID shared between them. It returns findings for the consuming plugin to handle; it does not automatically scan inventories, maintain a persistent registry, or remove duplicates.

## Dynamic lore

Lore lines can contain `<dynamic:key>` placeholders that are resolved from the item's persistent data container at render time:

```java
@Override
public List<String> getLore() {
    return List.of(
            "Kills: <dynamic:kills>",
            "Level: <dynamic:level>"
    );
}
```

Set dynamic values with `setDynamicLoreValue(itemStack, "kills", "42")` or the `ItemMeta` overload. Both store the value and immediately reapply the lore. Read a stored value with `getDynamicLoreValue(itemStack, "kills")`, which returns `Optional<String>`.

Placeholder keys use lowercase letters, digits, `.`, `_`, `/`, and `-`. Setter and getter keys are normalized to lowercase; setting a key absent from the definition's lore throws `IllegalArgumentException`. Missing values render as empty strings. Each lore application removes stored keys no longer referenced by the lore and removes the nested container when empty. Substituted values are also processed as text markup when the lore is rendered.

## Crafting prevention

The Item addon prevents custom items from being used in unintended contexts. Listeners block custom items in:

| Context | Prevention |
| --- | --- |
| Crafting table | Blocks preparation if the matrix contains a custom item |
| Anvil | Blocks repair and combination |
| Enchanting table | Blocks enchanting |
| Furnace, smoker, campfire | Blocks cooking |
| Smithing table | Blocks smithing |
| Brewing stand | Blocks brewing |

`ItemIngredientEvent` is fired when a custom item is encountered in a crafting context. The [Recipe addon](../Minecraft-Paper-Addon-Recipe/README.md) listens to this event and allows custom items that are valid ingredients for a registered custom recipe.

## Inventory synchronization

Item definitions are applied to player inventories at these points:

| Trigger | Scope |
| --- | --- |
| Player join | Player inventory and ender chest |
| Chest open | Block inventory contents |
| Item pickup | Single item |
| Crafting result | Result slot |
| Smelting result | Result slot |

The `apply` method on `ItemService` processes nonempty stacks in this order:

1. Resolve the stored unique ID to a registered custom definition: update outdated stacks or refresh current stacks.
2. Look for a naturally obtainable custom definition for the material: create a new custom stack using the existing amount and damage.
3. Remove an unresolved stack marked deletable by returning `null`.
4. Resolve the cached `DefaultItem` for the material: recreate stacks carrying a custom unique ID or version marker, or refresh ordinary stacks without rebuilding metadata.

Null and empty stacks are returned unchanged. Always use the returned value, since applying a definition can replace or remove the stack. Recreation keeps amount and damage but does not copy other metadata; refresh dispatches `ItemStackUpdateEvent` on the existing stack.

Crafting preview formatting runs at `NORMAL` priority. The Recipe addon's checks run later at `HIGHEST`, so a disabled placeholder supplied by those checks is not passed through Item formatting afterward. Inventory synchronization also handles stored items in block-state containers and bundles.

Updates keep existing damage when the material stays the same. When switching between damageable materials with positive maximum durability, an upgrade preserves the percentage of remaining uses (rounded), while a downgrade preserves the remaining use count up to the new maximum. The result retains at least one use.

## Item service

Inject `ItemService` to access registered definitions:

| Method | Returns | Behaviour |
| --- | --- | --- |
| `getItemList()` | `List<CustomItem>` | Injected read-only live list of custom definitions |
| `getItemByUniqueId(uniqueId)` | `Optional<CustomItem>` | Resolve a definition by its exact persistent unique ID; empty for null or unknown input |
| `getObtainableItemByMaterial(material)` | `Optional<CustomItem>` | Resolve a naturally obtainable definition; empty for null or unknown input |
| `getItemByItemStack(itemStack)` | `Optional<CustomItem>` | Resolve the definition from the stack's unique ID; empty for null or an unknown unique ID |
| `searchCustomItem(commandSender, input, inform)` | `Optional<CustomItem>` | Search typable identifiers with exact and partial matching and optional feedback |
| `searchCustomItem(commandSender, input, inform, predicate)` | `Optional<CustomItem>` | The same search restricted by the predicate |
| `apply(itemStack)` | `ItemStack` | Apply the item lifecycle; may return a replacement stack or null |

`getItemList()` follows registrations as dependencies initialize and shut down. Callers cannot add, remove, or replace entries through the injected list. The item definitions themselves retain their own behaviour and state. `onLiveDependencyUpdate()` rebuilds the unique ID and naturally obtainable material lookup maps from this list; cached default definitions are retained. If multiple definitions share a unique ID or naturally obtainable material, the last entry encountered in the list wins that lookup.

`Item.isSimilarByMaterial(itemStack)` compares the stack's material with the definition's material and returns `false` for `null`. `CustomItem.isSimilarByUniqueId(itemStack)` compares a stack's stored unique ID with the definition's `uniqueId`. `isHolding(livingEntity)` uses the same unique ID comparison for the main-hand item.

## Item search

[CustomItemSearchEngine](src/main/java/me/trae/foundation/minecraft/paper/addon/item/search/types/CustomItemSearchEngine.java) extends the framework's [Paper search engine](../../Minecraft-Paper-Plugin-Framework/README.md#search-engines). `ItemService` keeps a reusable instance and exposes it through `searchCustomItem(...)`; each search reads the service's current item list.

| Behaviour | Value |
| --- | --- |
| Exact match | Case-insensitive equality against `CustomItem.getIdentifier()` |
| Partial match | Case-insensitive containment in the identifier |
| Displayed match | `CustomItem.getIdentifier()`, colored with `CustomItem.getColor()` |
| Feedback prefix | `Custom Item Search` |

The identifier is the searchable value. The persistent unique ID resolves saved stacks, while `getName()` supplies the raw item display name. Neither is checked by this engine.

With `itemService`, `commandSender`, and `input` available in a command or service:

```java
itemService.searchCustomItem(commandSender, input, true)
        .ifPresent(customItem -> UtilMessage.message(commandSender, "Item", customItem.getIdentifier()));
```

Use the four-argument overload to restrict eligible definitions with a predicate. The three-argument overload includes every definition. With `inform` set to `true`, missing or ambiguous matches produce colored feedback; a unique result is returned without an automatic success message.

For direct engine access, use `itemService.getCustomItemSearchEngine()` or construct `new CustomItemSearchEngine(itemService)`. The engine class is also an example of supplying a service's live collection and overriding `getTypeColor` for per-result colors.

`ItemSuggestions.items(predicate, arg)` uses the same typable identifiers for command suggestions. Pass `_ -> true` as the predicate to include every definition; suggestions retain each identifier's original spelling.

## Events

| Event | Type | Purpose |
| --- | --- | --- |
| `ItemMetaUpdateEvent` | Informational | Fired during creation, view creation, and definition updates |
| `ItemStackUpdateEvent` | Informational | Fired when an item stack is updated |
| `ItemPreActivateEvent` | Cancellable | Fired before item activation |
| `ItemPostActivateEvent` | Informational | Fired after item activation |
| `ItemChannelEvent` | Cancellable | Fired on each tick of a channeled activation |
| `ItemIngredientEvent` | Settable | Fired when a custom item is found in a crafting context; set `allowed` to permit it |

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Item</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Does not require Paper NMS. Return to the [Addons README](../README.md).
