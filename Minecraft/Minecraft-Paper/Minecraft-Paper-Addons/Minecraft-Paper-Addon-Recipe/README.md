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
- [Recipe search](#recipe-search)
- [Conflict resolution](#conflict-resolution)
- [Item addon integration](#item-addon-integration)
- [Installation](#installation)

</details>

## Recipe types

| Class | Pattern | Example |
| --- | --- | --- |
| `CustomShapedRecipe` | Ingredient pattern in a grid up to 3x3 | Sword, pickaxe, armor |
| `CustomShapelessRecipe` | Any arrangement of ingredients | Combining dyes, nuggets to ingots |

Both extend `AbstractCustomRecipe`. The constructor takes an identifier exposed by `getIdentifier()`. Its Bukkit key is `custom:<identifier>` with the identifier lowercased using `Locale.ROOT`. For example, `DIAMOND_HAMMER` registers as `custom:diamond_hammer`; keep identifiers valid as namespaced-key paths after lowercasing. Override methods to define the result, ingredients, and, for shaped recipes, the shape. Examples omit imports and use Injector's `@Singleton` for plugin components.

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

The registered result amount is capped at the result item's maximum stack size, including a custom stack-size component when present.

## AbstractCustomRecipe

Both recipe types extend `AbstractCustomRecipe`, which provides the shared API:

| Method | Returns | Purpose |
| --- | --- | --- |
| `getIdentifier()` | `String` | The identifier supplied to the constructor, retaining its original spelling |
| `getNamespacedKey()` | `NamespacedKey` | Bukkit recipe identifier, built as `custom:<lowercase-identifier>` |
| `getResult()` | `ItemStack` | The crafting result (abstract) |
| `getIngredients()` | `Map<Character, ItemStack>` | Ingredient mapping (abstract) |
| `canCraft()` | `boolean` | Global crafting eligibility (default `true`) |
| `canCraft(player)` | `boolean` | Player-specific crafting eligibility (default `true`) |
| `canCraft(block)` | `boolean` | Automated crafter eligibility (default `true`) |
| `isIngredientByItemStack(itemStack)` | `boolean` | Checks if an item stack matches any ingredient |
| `matches(matrix)` | `boolean` | Matches occupied input slots against required ingredient occurrences |
| `build()` | `Recipe` | Builds the Bukkit recipe for registration |
| `getConflictingKeys()` | `Set<NamespacedKey>` | Find vanilla recipe conflicts, subject to the conditions in [Conflict resolution](#conflict-resolution) |

`matches(matrix)` creates a list of required ingredient occurrences from `toMatrix()`. Each occupied input slot consumes one similar ingredient from that list. An unmatched input or a required ingredient left over makes the match fail. Both matrix matching and `isIngredientByItemStack` call `UtilItemStack.isSimilar(ingredient, input, false)` for optional metadata matching.

That comparison always requires matching material. If either stack has a custom ID, both must have the same ID; matching IDs return immediately without comparing metadata. For ID-less stacks, display names, lore, and enchantments are checked only when both stacks contain each property. A property present on only one ID-less stack does not reject the match. See [UtilItemStack](../../Minecraft-Paper-Plugin-Framework/README.md#utilitemstack) for the full rules.

Registration uses a separate Bukkit recipe-choice check: `toChoice(itemStack)` selects `RecipeChoice.ExactChoice` when the ingredient has a custom definition ID and `RecipeChoice.MaterialChoice` otherwise. Both shaped and shapeless recipes use it. Bukkit's choice must select the recipe before the addon's `matches` and eligibility checks run; the shared similarity rules do not replace Bukkit's exact-choice validation.

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

Player notifications carry the crafted stack reported by Paper for each pickup, including repeated pickups during shift-crafting. For complete pickups, sum the result amounts across notifications to count output. Crafter notifications carry the result of the uncancelled `CrafterCraftEvent`. These notifications run during the underlying server events, rather than after the entire inventory transaction finishes.

On Paper 1.21.11, the player pickup hooks fire before the crafting matrix is decremented. The listener resolves the recipe from the player's open crafting inventory while the ingredients are still present, including the final ingredient set. A shift-click can produce multiple notifications, each carrying a recipe execution's output stack; it is not a single notification with the whole batch amount. For example, with enough inventory space, 64 oak logs crafted into planks produce 64 pickups of 4 planks each. The result is Paper's reported crafted stack, rather than an independently measured inventory increase.

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
        placeholderItemStack.editMeta(itemMeta -> {
            itemMeta.displayName(Component.text("Recipe unavailable", NamedTextColor.RED));
            UtilPdc.setPersistentDataType(
                    itemMeta.getPersistentDataContainer(),
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

`RecipeDisabledPlaceholderListener` cancels inventory clicks involving marked slot, cursor, hotbar-swap, or offhand-swap items at `LOW` priority. It also blocks marked items in drag operations, inventory transfers, and crafter output. This display behavior applies to custom player recipe previews; rejected automated crafts are cancelled instead.

## Recipe service

Inject `RecipeService` into plugin components to inspect registered recipes:

| Method or constant | Purpose |
| --- | --- |
| `getRecipeList()` | The injected read-only live list of recipe definitions |
| `getRegisteredNamespacedKeySet()` | Keys of custom recipes successfully registered during the latest rebuild |
| `getRecipeByKey(namespacedKey)` | Resolve a custom recipe through the key map; empty for null or an unknown key |
| `searchCustomRecipe(commandSender, input, inform)` | Search recipe identifiers with exact and partial matching and optional feedback |
| `searchCustomRecipe(commandSender, input, inform, predicate)` | The same search restricted by the predicate |
| `isIngredient(namespacedKey, itemStack)` | Check whether the item is an ingredient of that registered recipe |
| `DISABLED_RECIPE_KEY` | Persistent-data key for disabled preview items |

On live dependency updates, the service rebuilds its recipe lookup map and publishes an unmodifiable snapshot through a volatile field. Key lookup and ingredient checks use that map instead of scanning the recipe list. Shutdown removes registered recipes and clears the lookup snapshot. Use unique recipe keys; when definitions share a key, lookup retains the first definition in the live list.

## Recipe search

[CustomRecipeSearchEngine](src/main/java/me/trae/foundation/minecraft/paper/addon/recipe/search/types/CustomRecipeSearchEngine.java) extends the framework's [Paper search engine](../../Minecraft-Paper-Plugin-Framework/README.md#search-engines). `RecipeService` keeps a reusable instance and exposes it through `searchCustomRecipe(...)`; each search reads the service's current recipe list. Both service overloads return `Optional<AbstractCustomRecipe>`.

| Behaviour | Value |
| --- | --- |
| Exact match | Case-insensitive equality against `AbstractCustomRecipe.getIdentifier()` |
| Partial match | Case-insensitive containment in the identifier |
| Displayed match | Identifier formatted with `UtilString.clean`, such as `Diamond Hammer` for `diamond_hammer` |
| Feedback prefix | `Custom Recipe Search` |

Search with the identifier, such as `DIAMOND_HAMMER` or `diamond_hammer`. The namespaced Bukkit key `custom:diamond_hammer` and formatted display name are separate values.

With `recipeService`, `commandSender`, and `input` available in a command or service:

```java
recipeService.searchCustomRecipe(commandSender, input, true)
        .ifPresent(abstractCustomRecipe -> UtilMessage.message(commandSender, "Recipe", abstractCustomRecipe.getIdentifier()));
```

Use the four-argument overload to restrict eligible definitions with a predicate. The three-argument overload includes every definition. With `inform` set to `true`, missing or ambiguous matches produce colored feedback; a unique result is returned without an automatic success message. For direct engine access, use `recipeService.getCustomRecipeSearchEngine()` or construct `new CustomRecipeSearchEngine(recipeService)`.

The engine searches the service's recipe definitions. Use `RecipeService.getRegisteredNamespacedKeySet()` when you also need to distinguish which keys were successfully registered during the latest rebuild.

## Conflict resolution

`RecipeService` registers recipes during `onLiveDependencyUpdate`. Before registering, it collects each definition's `getConflictingKeys()` and removes those vanilla recipes, logging successful removals. Conflict lookup returns no keys when any declared ingredient has a custom item unique ID or no worlds are loaded. Otherwise, it tests the recipe's matrix against Bukkit's crafting system in the first loaded world and returns at most one `minecraft` key. The result's custom ID does not affect conflict lookup, and the former `removeConflictingRecipes()` hook is no longer available. Each rebuild first removes the previously registered custom recipes; successful registrations are tracked and cleaned up on shutdown.

| Ingredients | Result | Vanilla conflict handling |
| --- | --- | --- |
| All ID-less | Vanilla item | Remove the detected vanilla conflict, if any |
| All ID-less | Custom item | Remove the detected vanilla conflict, if any |
| At least one custom-ID ingredient | Vanilla item | Preserve vanilla recipes |
| At least one custom-ID ingredient | Custom item | Preserve vanilla recipes |

Ingredient validation is independent of the output. For example, an ID-less gold ingot renamed by a player can satisfy a plain-gold ingredient in a custom recipe producing Reinforced Gold. A Reinforced Gold input cannot replace that plain-gold ingredient. Conversely, a recipe requiring Reinforced Gold accepts the matching custom ID at the base comparison stage whether its result is vanilla or custom; Bukkit's registered exact choice must also pass. `getResult()` supplies the output, and `buildResult()` caps its amount at its maximum stack size.

## Item addon integration

When the [Item addon](../Minecraft-Paper-Addon-Item/README.md) is present, `RecipeIngredientListener` listens for `ItemIngredientEvent`. The Item addon fires this event when it encounters a custom item in a crafting context and would normally block it. The Recipe addon checks whether the custom item is a valid ingredient for the recipe being crafted and sets the event to allowed if it is.

This means custom items are blocked from all crafting contexts by default (by the Item addon), but allowed in recipes that explicitly list them as ingredients (by the Recipe addon).

For player previews, `ItemApplyListener` formats the result at `NORMAL` priority before recipe checks run at `HIGHEST`. A disabled placeholder supplied by the recipe checks remains intact because Item formatting has already completed.

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
