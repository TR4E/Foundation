package me.trae.foundation.minecraft.paper.addon.recipe;

import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilItemStack;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilWorld;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

final class CustomRecipeMatchingTest {

    @Test
    void renamedVanillaIngredientsAreAcceptedButCustomSubstitutesAreRejected() {
        final ItemStack ingredientItemStack = this.itemStack(null);
        final CustomShapelessRecipe customShapelessRecipe = this.recipe(ingredientItemStack, this.itemStack("custom_result"));
        final ItemStack renamedItemStack = this.itemStack(null);
        when(renamedItemStack.getItemMeta().hasDisplayName()).thenReturn(true);
        when(renamedItemStack.getItemMeta().displayName()).thenReturn(Component.text("My Gold"));

        assertTrue(customShapelessRecipe.isIngredientByItemStack(renamedItemStack));
        assertTrue(customShapelessRecipe.matches(new ItemStack[]{renamedItemStack}));

        final ItemStack customItemStack = this.itemStack("reinforced_gold");

        assertFalse(customShapelessRecipe.isIngredientByItemStack(customItemStack));
        assertFalse(customShapelessRecipe.matches(new ItemStack[]{customItemStack}));
    }

    @Test
    void customIngredientsRequireTheirIdAndIgnoreTheirDisplayName() {
        final CustomShapelessRecipe customShapelessRecipe = this.recipe(this.itemStack("reinforced_gold"), this.itemStack(null));
        final ItemStack renamedItemStack = this.itemStack("reinforced_gold");
        when(renamedItemStack.getItemMeta().hasDisplayName()).thenReturn(true);
        when(renamedItemStack.getItemMeta().displayName()).thenReturn(Component.text("Different appearance"));

        assertTrue(customShapelessRecipe.isIngredientByItemStack(renamedItemStack));
        assertTrue(customShapelessRecipe.matches(new ItemStack[]{renamedItemStack}));
        assertFalse(customShapelessRecipe.matches(new ItemStack[]{this.itemStack(null)}));
        assertFalse(customShapelessRecipe.matches(new ItemStack[]{this.itemStack("other_gold")}));
        assertFalse(customShapelessRecipe.matches(new ItemStack[0]));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void conflictRemovalDependsOnIngredientsRatherThanTheResult(final boolean customIngredient, final boolean customResult) {
        final CustomShapelessRecipe customShapelessRecipe = this.recipe(
                this.itemStack(customIngredient ? "reinforced_gold" : null),
                this.itemStack(customResult ? "custom_result" : null)
        );
        final World world = mock(World.class);
        final NamespacedKey namespacedKey = NamespacedKey.minecraft("gold_recipe");
        final CraftingRecipe craftingRecipe = mock(CraftingRecipe.class);
        when(craftingRecipe.getKey()).thenReturn(namespacedKey);

        try (final MockedStatic<UtilWorld> utilWorldMockedStatic = mockStatic(UtilWorld.class);
             final MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class)) {
            utilWorldMockedStatic.when(UtilWorld::getWorlds).thenReturn(List.of(world));
            bukkitMockedStatic.when(() -> Bukkit.getCraftingRecipe(any(ItemStack[].class), eq(world)))
                    .thenReturn(craftingRecipe);

            assertEquals(customIngredient ? Set.of() : Set.of(namespacedKey), customShapelessRecipe.getConflictingKeys());
        }
    }

    @Test
    void conflictLookupReturnsNothingWithoutALoadedWorld() {
        final CustomShapelessRecipe customShapelessRecipe = this.recipe(this.itemStack(null), this.itemStack(null));

        try (final MockedStatic<UtilWorld> utilWorldMockedStatic = mockStatic(UtilWorld.class)) {
            utilWorldMockedStatic.when(UtilWorld::getWorlds).thenReturn(List.of());

            assertTrue(customShapelessRecipe.getConflictingKeys().isEmpty());
        }
    }

    private CustomShapelessRecipe recipe(final ItemStack ingredientItemStack, final ItemStack resultItemStack) {
        final CustomShapelessRecipe customShapelessRecipe = mock(CustomShapelessRecipe.class, CALLS_REAL_METHODS);
        doReturn(Map.of('G', ingredientItemStack)).when(customShapelessRecipe).getIngredients();
        doReturn(resultItemStack).when(customShapelessRecipe).getResult();

        return customShapelessRecipe;
    }

    private ItemStack itemStack(final String uniqueId) {
        final ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.getType()).thenReturn(Material.GOLD_INGOT);
        when(itemStack.getAmount()).thenReturn(1);
        when(itemStack.asOne()).thenReturn(itemStack);
        when(itemStack.hasItemMeta()).thenReturn(true);

        final ItemMeta itemMeta = mock(ItemMeta.class);
        when(itemStack.getItemMeta()).thenReturn(itemMeta);

        final PersistentDataContainer persistentDataContainer = mock(PersistentDataContainer.class);
        when(itemMeta.getPersistentDataContainer()).thenReturn(persistentDataContainer);
        when(persistentDataContainer.get(UtilItemStack.UNIQUE_ID_KEY, PersistentDataType.STRING)).thenReturn(uniqueId);

        return itemStack;
    }
}