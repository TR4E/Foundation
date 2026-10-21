<h1 align="center">Minecraft-Paper-Addon-Recipe</h1>

<p align="center">Custom shaped and shapeless crafting recipes with optional custom item integration.</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/25/"><img alt="Java 25" src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white"></a>
  <a href="https://maven.apache.org/"><img alt="Maven" src="https://img.shields.io/badge/Maven-Library-C71A36?logo=apachemaven&logoColor=white"></a>
  <a href="https://papermc.io/"><img alt="Paper" src="https://img.shields.io/badge/Paper-API-44cc11?logo=minecraft&logoColor=white"></a>
</p>

The Recipe addon registers custom crafting recipes and validates them at craft time. It can optionally integrate with the [Item addon](../Minecraft-Paper-Addon-Item/README.md) to restrict custom items to their own recipes, preventing custom items from being used as ingredients in vanilla or unrelated recipes.

## Contents

<details>
<summary>On this page</summary>

- [Recipe types](#recipe-types)
- [Shaped recipe](#shaped-recipe)
- [Shapeless recipe](#shapeless-recipe)
- [AbstractCustomRecipe](#abstractcustomrecipe)
- [Conflict resolution](#conflict-resolution)
- [Item addon integration](#item-addon-integration)
- [Installation](#installation)

</details>

## Recipe types

| Class | Pattern | Example |
| --- | --- | --- |
| `CustomShapedRecipe` | Fixed 3x3 grid pattern | Sword, pickaxe, armor |
| `CustomShapelessRecipe` | Any arrangement of ingredients | Combining dyes, nuggets to ingots |

Both extend `AbstractCustomRecipe`. The constructor takes only a `namespace` string. The result, ingredients, and shape are defined by overriding abstract methods.

## Shaped recipe

Define a shaped recipe by overriding `getResult()`, `getIngredients()`, and `getShape()`:

```java
@Singleton
public class DiamondHammerRecipe extends CustomShapedRecipe {

    public DiamondHammerRecipe() {
        super("DIAMOND_HAMMER");
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

`getShape()` returns a `String[]` representing the 3x3 crafting grid. Each character maps to an `ItemStack` in `getIngredients()`. Spaces represent empty slots.

## Shapeless recipe

Shapeless recipes override the same methods. The `getIngredients()` map uses character keys as identifiers, and the item stack amount determines how many of that ingredient are needed:

```java
@Singleton
public class CompactGoldRecipe extends CustomShapelessRecipe {

    public CompactGoldRecipe() {
        super("COMPACT_GOLD");
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

The item stack amount is expanded into individual slots via `Collections.nCopies`. In this example, 9 gold nuggets fill the crafting grid.

The result stack size is capped at the material's maximum stack size.

## AbstractCustomRecipe

Both recipe types extend `AbstractCustomRecipe`, which provides the shared API:

| Method | Returns | Purpose |
| --- | --- | --- |
| `getKey()` | `NamespacedKey` | Built from the namespace as `custom:<namespace>` |
| `getResult()` | `ItemStack` | The crafting result (abstract) |
| `getIngredients()` | `Map<Character, ItemStack>` | Ingredient mapping (abstract) |
| `removeConflictingRecipes()` | `boolean` | Whether to remove vanilla recipes that conflict (default `true`) |
| `isIngredientByItemStack(itemStack)` | `boolean` | Checks if an item stack matches any ingredient |
| `matches(matrix)` | `boolean` | Validates a crafting matrix against the recipe's ingredients |

## Conflict resolution

`RecipeService` registers recipes during `onLiveDependencyUpdate`. Before registering, it checks each recipe for conflicting vanilla recipes by testing the recipe's matrix against Bukkit's crafting system. If a conflict is found and `removeConflictingRecipes()` returns `true`, the vanilla recipe is removed and a log message is emitted. All registered recipes are tracked and cleaned up on shutdown.

`RecipeCraftListener` validates crafting attempts at `HIGHEST` priority. When a `PrepareItemCraftEvent` fires for a registered custom recipe, the listener checks whether the crafting matrix ingredients actually match the recipe. If not, the result is cleared to prevent crafting with incorrect ingredients.

## Item addon integration

When the [Item addon](../Minecraft-Paper-Addon-Item/README.md) is present, `RecipeIngredientListener` listens for `ItemIngredientEvent`. The Item addon fires this event when it encounters a custom item in a crafting context and would normally block it. The Recipe addon checks whether the custom item is a valid ingredient for the recipe being crafted and sets the event to allowed if it is.

This means custom items are blocked from all crafting contexts by default (by the Item addon), but allowed in recipes that explicitly list them as ingredients (by the Recipe addon).

The Item addon dependency is optional in the Maven POM. The Recipe addon works without it; the integration listener is only active when both addons are present.

## Installation

```xml
<dependency>
    <groupId>me.trae.foundation.minecraft.paper.addons</groupId>
    <artifactId>Minecraft-Paper-Addon-Recipe</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Does not require Paper NMS. Return to the [Addons README](../README.md).
