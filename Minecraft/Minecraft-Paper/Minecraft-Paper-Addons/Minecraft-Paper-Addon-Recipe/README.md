<h1 align="center">Minecraft-Paper-Addon-Recipe</h1>

<p align="center">Custom shaped and shapeless recipes with crafting checks, player and crafter events, and optional custom item integration.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper" src="https://img.shields.io/badge/Paper-API-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The Recipe addon registers custom crafting recipes, validates ingredient occurrences, and applies global, player, or block-specific crafting rules. It provides pre-craft events for cancellation and result customization, post-craft notifications, and support for displaying disabled recipe placeholders. Player crafting and automated crafter blocks use the same recipe definitions. The optional [Item addon](../Minecraft-Paper-Addon-Item/README.md) integration allows custom items in recipes that explicitly list them as ingredients.

## Contents

<details>
<summary>On this page</summary>

- [Recipe types](#recipe-types)
- [Shaped recipe](#shaped-recipe)
- [Shapeless recipe](#shapeless-recipe)
- [AbstractCustomRecipe](#abstractcustomrecipe)
- [Crafting checks](#crafting-checks)
- [Events](#events)
- [Disabled recipe placeholders](#disabled-recipe-placeholders)
- [Recipe service](#recipe-service)
- [Conflict resolution](#conflict-resolution)
- [Item addon integration](#item-addon-integration)
- [Installation](#installation)

</details>

## Recipe types

| Class | Pattern | Example |
| --- | --- | --- |
| `CustomShapedRecipe` | Material pattern in a grid up to 3x3 | Sword, pickaxe, armor |
| `CustomShapelessRecipe` | Any arrangement of ingredients | Combining dyes, nuggets to ingots |

Both extend `AbstractCustomRecipe`. The constructor takes a recipe key, which becomes `custom:<key>`. Use lowercase keys such as `diamond_hammer`. Override methods to define the result, ingredients, and, for shaped recipes, the shape. Examples omit imports and use Injector's `@Singleton` for plugin components.

## Shaped recipe

Define a shaped recipe by overriding `getResult()`, `getIngredients()`, and `getShape()`:

```java
@Singleton
public final class DiamondHammerRecipe extends CustomShapedRecipe {

    public DiamondHammerRecipe() {
        super("diamond_hammer");
    }

    @Override
    public ItemStack getResult() {
        return new ItemStack(Material.DIAMOND_PICKAXE);
    }

    @Override
    public String[] getShape() {
        return new String[]{"DDD", " S ", " S "};
    }

    @Override
    public Map<Character, ItemStack> getIngredients() {
        return Map.of(
                'D', new ItemStack(Material.DIAMOND),
                'S', new ItemStack(Material.STICK)
        );
    }
}
```

`getShape()` returns a `String[]` representing the recipe's rows. Each character maps to an `ItemStack` in `getIngredients()`. Spaces represent empty slots. Repeating a character requires that ingredient in each corresponding slot; the ingredient stack's amount does not add slots to a shaped recipe.

## Shapeless recipe

Shapeless recipes override `getResult()` and `getIngredients()`. The ingredient map uses character keys as identifiers, and each item stack's amount determines how many ingredient slots are required:

```java
@Singleton
public final class CompactGoldRecipe extends CustomShapelessRecipe {

    public CompactGoldRecipe() {
        super("compact_gold");
    }

    @Override
    public ItemStack getResult() {
        return new ItemStack(Material.GOLD_BLOCK);
    }

    @Override
    public Map<Character, ItemStack> getIngredients() {
        return Map.of(
                'G', new ItemStack(Material.GOLD_NUGGET, 9)
        );
    }
}
```

The item stack amount is expanded into individual slots via `Collections.nCopies`. In this example, 9 gold nuggets fill the crafting grid. A stack of 9 nuggets in one slot does not replace the nine required slots.

The result stack size is capped at the material's maximum stack size.

## AbstractCustomRecipe

Both recipe types extend `AbstractCustomRecipe`, which provides the shared API:

| Method | Returns | Purpose |
| --- | --- | --- |
| `getKey()` | `String` | The recipe key supplied to the constructor |
| `getNamespacedKey()` | `NamespacedKey` | Bukkit recipe identifier, built as `custom:<key>` |
| `getResult()` | `ItemStack` | The crafting result (abstract) |
| `getIngredients()` | `Map<Character, ItemStack>` | Ingredient mapping (abstract) |
| `removeConflictingRecipes()` | `boolean` | Whether to remove vanilla recipes that conflict (default `true`) |
| `canCraft()` | `boolean` | Global crafting eligibility (default `true`) |
| `canCraft(player)` | `boolean` | Player-specific crafting eligibility (default `true`) |
| `canCraft(block)` | `boolean` | Automated crafter eligibility (default `true`) |
| `isIngredientByItemStack(itemStack)` | `boolean` | Checks if an item stack matches any ingredient |
| `matches(matrix)` | `boolean` | Matches occupied input slots against required ingredient occurrences |
| `build()` | `Recipe` | Builds the Bukkit recipe for registration |

`matches(matrix)` creates a list of required ingredient occurrences from `toMatrix()`. Each occupied input slot consumes one similar ingredient from that list. An unmatched input or a required ingredient left over makes the match fail. Comparison uses the framework's `UtilItemStack.isSimilar`, including custom item identifiers when both items have them.

The registered shaped recipe handles the material pattern. The shared `matches(matrix)` method compares ingredient occurrences independently of position; it does not enforce where distinct custom items of the same material appear. Recipes can override `matches(matrix)` for stricter validation.

## Crafting checks

`RecipePreCraftListener` handles both player previews and automated crafter attempts at `HIGHEST` priority:

| Source | Bukkit event | Required checks |
| --- | --- | --- |
| Player crafting grid | `PrepareItemCraftEvent` | `matches(matrix)`, `canCraft()`, and `canCraft(player)` |
| Crafter block | `CrafterCraftEvent` | `matches(contents)`, `canCraft()`, and `canCraft(block)` |

After ingredient matching succeeds, the listener dispatches `CustomRecipePreCraftEvent` before evaluating the eligibility methods. A cancelled pre-craft event also rejects the attempt.

For a player, rejection normally clears the result slot. A tagged disabled placeholder can remain visible instead. For a crafter block, rejection cancels `CrafterCraftEvent`.

Override the eligibility methods for changing availability rules. Player and block checks are independent; each path also requires the global check to pass. Keep the recipe registered so the rules are evaluated when preparing or attempting a craft.

## Events

All four recipe events are synchronous. Registered custom recipes use the custom events. Recipes outside the service use the vanilla events only when their key has the `minecraft` namespace; other namespaces are left alone.

| Event | Cancellable | Data |
| --- | --- | --- |
| `CustomRecipePreCraftEvent` | Yes | Recipe, player or block, original result, mutable result |
| `VanillaRecipePreCraftEvent` | Yes | Player or block, original result, mutable result |
| `CustomRecipePostCraftEvent` | No | Recipe, player or block, result |
| `VanillaRecipePostCraftEvent` | No | Player or block, result |

The source determines which context field is populated:

| Source | `getPlayer()` | `getBlock()` |
| --- | --- | --- |
| Player crafting | Player | `null` |
| Automated crafter | `null` | Crafter block |

### Pre-craft events

Each pre-craft event keeps the supplied original result and initializes its editable result with a clone. Use `getResult()` to edit that copy, `setResult(...)` to replace it, and `setCancelled(true)` to reject the attempt. Treat `getOriginalResult()` as input.

For player crafting, the listener applies the event's result to the preview. For vanilla crafter recipes, it applies the replacement to `CrafterCraftEvent`. The current custom crafter handler reads cancellation but does not apply the custom pre-craft event's replacement result.

Cancel the event to suppress automated output; provide a non-null item stack when replacing a crafter result. Player preview events can fire repeatedly as the grid changes and are not notifications that ingredients were consumed.

### Post-craft events

`RecipePostCraftListener` observes Paper's `ItemCraftedEvent` and `CrafterCraftEvent` at `MONITOR` priority and dispatches the corresponding post-craft notification. The player path skips an empty result; the crafter path skips cancelled events.

Player notifications carry the crafted stack reported by Paper for each pickup, including repeated pickups during shift-crafting. Add the result amounts across notifications to count total output. Crafter notifications carry the result of the uncancelled `CrafterCraftEvent`. These notifications run during the underlying server events, rather than after the entire inventory transaction finishes.

## Disabled recipe placeholders

A custom player recipe can show an unavailable-result item instead of clearing the result slot. Supply that item through `CustomRecipePreCraftEvent.setResult(...)`, mark it with `RecipeService.DISABLED_RECIPE_KEY`, and cancel the event or let an eligibility check reject the recipe.

```java
@Singleton
public final class RecipeAvailabilityListener implements Listener {

    @EventHandler
    public void onCustomRecipePreCraft(final CustomRecipePreCraftEvent event) {
        final Player player = event.getPlayer();
        if (player == null || player.hasPermission("recipes.custom")) {
            return;
        }

        final ItemStack placeholderItemStack = new ItemStack(Material.BARRIER);
        placeholderItemStack.editMeta((final ItemMeta itemMeta) -> {
            itemMeta.displayName(Component.text("Recipe unavailable", NamedTextColor.RED));
            UtilItemStack.setPersistentDataType(
                    itemMeta,
                    RecipeService.DISABLED_RECIPE_KEY,
                    PersistentDataType.BOOLEAN,
                    true
            );
        });

        event.setResult(placeholderItemStack);
        event.setCancelled(true);
    }
}
```

The marker is a `PersistentDataType.BOOLEAN` value at `custom:disabled_recipe`. Both placeholder retention and click prevention check whether the value is present. Use `true` when setting it.

`RecipeDisabledPlaceholderListener` cancels inventory clicks on marked items at `LOW` priority, preventing players from taking the placeholder. This display behavior applies to custom player recipe previews; rejected automated crafts are cancelled instead.

## Recipe service

Inject `RecipeService` into plugin components to inspect registered recipes:

| Method or constant | Purpose |
| --- | --- |
| `getRecipeList()` | The injected live list of recipe definitions |
| `getRegisteredNamespacedKeySet()` | Keys of custom recipes successfully registered during the latest rebuild |
| `getRecipeByKey(namespacedKey)` | Resolve a custom recipe as an `Optional<AbstractCustomRecipe>` |
| `isIngredient(namespacedKey, itemStack)` | Check whether the item is an ingredient of that registered recipe |
| `DISABLED_RECIPE_KEY` | Persistent-data key for disabled preview items |

## Conflict resolution

`RecipeService` registers recipes during `onLiveDependencyUpdate`. Before registering, it checks each recipe for conflicting vanilla recipes by testing the recipe's matrix against Bukkit's crafting system. If a conflict is found and `removeConflictingRecipes()` returns `true`, the vanilla recipe is removed and a log message is emitted. All registered recipes are tracked and cleaned up on shutdown.

## Item addon integration

When the [Item addon](../Minecraft-Paper-Addon-Item/README.md) is present, `RecipeIngredientListener` listens for `ItemIngredientEvent`. The Item addon fires this event when it encounters a custom item in a crafting context and would normally block it. The Recipe addon checks whether the custom item is a valid ingredient for the recipe being crafted and sets the event to allowed if it is.

This means custom items are blocked from all crafting contexts by default (by the Item addon), but allowed in recipes that explicitly list them as ingredients (by the Recipe addon).

The Item addon dependency is optional in the Maven POM. Add it explicitly to your consuming plugin when using this integration, and declare both addons through the Plugin Framework.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Recipe</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Declare `RecipeAddon` on your `PaperPlugin` with `@Addons({RecipeAddon.class})`. When using Item addon integration, include `ItemAddon.class` in the same annotation and add the Item addon dependency as well. Recipe definitions belong under the plugin's package and use `@Singleton`; the framework registers the addon's internal services and listeners.

Does not require Paper NMS. Return to the [Addons README](../README.md).
