<h1 align="center">Minecraft-Paper-Addon-Item</h1>

<p align="center">Custom items with activation mechanics, cooldowns, versioned updates, and crafting prevention.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper" src="https://img.shields.io/badge/Paper-API-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The Item addon provides a framework for defining custom items that are identified by persistent data, automatically styled, version-tracked, and protected from unintended crafting. Items can react to player interactions through an activation system with cooldowns and channeling support.

## Contents

<details>
<summary>On this page</summary>

- [Item hierarchy](#item-hierarchy)
- [Base item](#base-item)
- [Custom item](#custom-item)
- [Activatable custom item](#activatable-custom-item)
- [Single activatable custom item](#single-activatable-custom-item)
- [Channel custom item](#channel-custom-item)
- [Styling](#styling)
- [Persistent data stamping](#persistent-data-stamping)
- [Dynamic lore](#dynamic-lore)
- [Crafting prevention](#crafting-prevention)
- [Inventory synchronization](#inventory-synchronization)
- [Events](#events)
- [Installation](#installation)

</details>

## Item hierarchy

Items form an inheritance chain from simple definitions to fully interactive tools:

| Class | Adds |
| --- | --- |
| `Item` | Base: material, name, lore, color, style, display name formatting |
| `CustomItem` | Persistent data identifier, namespace, version tracking, update/refresh lifecycle |
| `ActivatableCustomItem` | Multiple activation types (left click, right click, drop, swap hand) |
| `SingleActivatableCustomItem` | Single activation type with simplified API and cooldown |
| `ChannelCustomItem` | Sustained right-click activation with start/channel/stop lifecycle |
| `DefaultItem` | Wraps vanilla items with styling only, no identifier |

`ItemService` is the central registry and Injector application. It discovers all `Item` components and manages the apply/update/refresh lifecycle.

## Base item

`Item` is the abstract base for all items. It defines the material, display name, lore, and visual properties.

```java
@Singleton
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

`getName()` returns plain text with no color codes. The display name color is controlled by `getColor()`, which defaults to white. Lore lines default to white and are not italicized.

### Overridable methods

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `getName()` | `String` | _abstract_ | Plain-text display name (no colors) |
| `getLore()` | `List<String>` | _abstract_ | Lore lines (default color is white) |
| `getColor()` | `java.awt.Color` | `ChatColor.WHITE` | Display name color |
| `getDecorations()` | `List<TextDecoration>` | `null` | Text decorations (bold, italic, etc.) |
| `getStyle()` | `ItemStyle` | `null` | Mass style override (color, decorations, tooltip, tag) |
| `getModel()` | `NamespacedKey` | `null` | Custom model for resource packs |
| `getTooltipStyle()` | `NamespacedKey` | `null` | Tooltip style for 1.20+ |
| `hideAttributes()` | `boolean` | `false` | Hide all item flags |
| `getMaxStackSize()` | `int` | Material default | Custom max stack size |
| `addGlow()` | `Boolean` | `null` | Enchantment glint override (`true` = always, `false` = never, `null` = default) |
| `stamp(ItemMeta)` | `void` | no-op | Write custom persistent data during creation |
| `editMeta(ItemMeta)` | `void` | no-op | Final item meta adjustments after all other properties are applied |

## Custom item

`CustomItem` extends `Item` with a persistent data identifier for tracking and version management. The identifier is a random UUIDv4 stored in the item's `PersistentDataContainer`. The namespace is an uppercase key used for configuration and commands.

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
| `isInvincible()` | `boolean` | `false` | Prevent the item from being destroyed |
| `removeVanillaUseCooldown()` | `boolean` | `true` | Remove the vanilla use cooldown animation |
| `naturallyObtainable()` | `boolean` | `false` | Whether the item can appear in natural loot |
| `deleteIfRemoved()` | `boolean` | `false` | Delete the item on next inventory sync if the definition is removed |

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
| `LEFT_CLICK` | Left-click air or block |
| `RIGHT_CLICK` | Right-click air or block |
| `DROP_ITEM` | Player drops the item |
| `SWAP_HAND` | Player swaps main and off hand |

### Additional overridable methods

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `getSupportedActivateTypes()` | `Set<ActivateType>` | _abstract_ | Which interactions trigger activation |
| `onActivate(player, itemStack, block, type)` | `void` | _abstract_ | Called when the item is activated |
| `canActivate(player, itemStack, block, type)` | `boolean` | `true` | Pre-activation check |
| `useItemInHand(player, itemStack, type)` | `Event.Result` | `DENY` for right-click usable items, else `DEFAULT` | Control vanilla item-in-hand behavior |
| `useInteractedBlock(player, itemStack, block, type)` | `Event.Result` | `DEFAULT` | Control vanilla block interaction |
| `activateOnItemUse(player, itemStack, type)` | `boolean` | `true` | Whether to activate on item use |
| `activateOnBlockUse(player, itemStack, block, type)` | `boolean` | `false` | Whether to activate on block use |
| `getCooldownName(type)` | `String` | `getName()` | Cooldown identifier |
| `getCooldownDuration(type)` | `long` | `0L` | Cooldown in milliseconds |

## Single activatable custom item

`SingleActivatableCustomItem` simplifies `ActivatableCustomItem` for items that respond to a single interaction type. The activation type is passed in the constructor, and the API methods drop the `ActivateType` parameter.

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
| `useItemInHand(player, itemStack)` | `Event.Result` | `DEFAULT` | Control vanilla item-in-hand behavior |
| `useInteractedBlock(player, itemStack, block)` | `Event.Result` | `DEFAULT` | Control vanilla block interaction |
| `getCooldownName()` | `String` | `getName()` | Cooldown identifier |
| `getCooldownDuration()` | `long` | `0L` | Cooldown in milliseconds |

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

The constructor takes material, identifier, and namespace. The activation type is always `RIGHT_CLICK`.

### Additional overridable methods

| Method | Returns | Default | Purpose |
| --- | --- | --- | --- |
| `canChannel(player, itemStack)` | `boolean` | `true` | Whether channeling can continue |
| `onStart(player, itemStack)` | `void` | no-op | Called when channeling begins |
| `onChannel(player, itemStack)` | `void` | _abstract_ | Called each tick while channeling |
| `onStop(player, itemStack)` | `void` | no-op | Called when channeling ends |

Utility methods: `isChanneling(player)` checks if a player is currently channeling, and `getChannelingPlayers()` returns all players currently channeling this item.

## Styling

Items define their appearance through color, decorations, and an optional `ItemStyle` record for mass configuration:

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

Individual overrides of `getColor()` or `getDecorations()` on the item class take precedence over the style when `getStyle()` returns `null`.

## Persistent data stamping

Custom items are identified through Bukkit's `PersistentDataContainer` on the item meta:

| Key | Type | Purpose |
| --- | --- | --- |
| `custom:item_identifier` | `String` | The UUIDv4 identifier matching the `CustomItem` |
| `custom:item_version` | `String` | SHA-256 hash of the item's current configuration for update detection |
| `custom:item_deletable` | `Boolean` | When `true`, the item is removed on next inventory sync |
| `custom:item_dynamic_lore` | Container | Nested container for dynamic lore key-value pairs |

When a player's inventory is synchronized, each item is checked: if it carries an identifier, its version is compared against the current definition. Outdated items are updated in place, preserving durability. Items marked deletable are removed.

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

Set dynamic values with `setDynamicLoreValue(itemStack, "kills", "42")`. The values are stored in the item's persistent data and resolved each time the lore is applied.

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
| Player join | Full inventory |
| Chest open | Block inventory contents |
| Item pickup | Single item |
| Crafting result | Result slot |
| Smelting result | Result slot |

The `apply` method on `ItemService` identifies each item by its persistent data, updates outdated items, refreshes styling on current items, wraps vanilla items in `DefaultItem` for consistent styling, and removes items marked as deletable.

Durability is preserved during updates. When the material changes between versions, durability is scaled proportionally (upgrade) or capped (downgrade).

## Events

| Event | Type | Purpose |
| --- | --- | --- |
| `ItemMetaUpdateEvent` | Informational | Fired when item meta is updated during create or refresh |
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
