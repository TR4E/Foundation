package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class UtilItemStackTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aCustomItemCannotReplaceAnIdlessItem(final boolean strictItemMeta) {
        final ItemStack plainItemStack = this.itemStack(Material.GOLD_INGOT, null, 1);
        final ItemStack customItemStack = this.itemStack(Material.GOLD_INGOT, "reinforced_gold", 1);

        assertFalse(UtilItemStack.isSimilar(plainItemStack, customItemStack, strictItemMeta));
        assertFalse(UtilItemStack.isSimilar(customItemStack, plainItemStack, strictItemMeta));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void customIdentityBypassesMetadataButRequiresMaterialAndId(final boolean strictItemMeta) {
        final ItemStack firstItemStack = this.itemStack(Material.GOLD_INGOT, "reinforced_gold", 1);
        final ItemStack secondItemStack = this.itemStack(Material.GOLD_INGOT, "reinforced_gold", 5);
        when(secondItemStack.getItemMeta().hasDisplayName()).thenReturn(true);
        when(secondItemStack.getItemMeta().displayName()).thenReturn(Component.text("Renamed"));

        assertTrue(UtilItemStack.isSimilar(firstItemStack, secondItemStack, strictItemMeta));
        assertFalse(UtilItemStack.isSimilar(
                firstItemStack,
                this.itemStack(Material.GOLD_INGOT, "other_gold", 1),
                strictItemMeta
        ));
        assertFalse(UtilItemStack.isSimilar(
                firstItemStack,
                this.itemStack(Material.IRON_INGOT, "reinforced_gold", 1),
                strictItemMeta
        ));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void namePresenceIsRequiredOnlyInStrictMode(final boolean strictItemMeta) {
        final ItemStack plainItemStack = this.itemStack(Material.GOLD_INGOT, null, 1);
        final ItemStack namedItemStack = this.itemStack(Material.GOLD_INGOT, null, 1);
        when(namedItemStack.getItemMeta().hasDisplayName()).thenReturn(true);
        when(namedItemStack.getItemMeta().displayName()).thenReturn(Component.text("My Gold"));

        assertEquals(!strictItemMeta, UtilItemStack.isSimilar(plainItemStack, namedItemStack, strictItemMeta));
        assertEquals(!strictItemMeta, UtilItemStack.isSimilar(namedItemStack, plainItemStack, strictItemMeta));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void differentPresentNamesFailInBothModes(final boolean strictItemMeta) {
        final ItemStack firstItemStack = this.itemStack(Material.GOLD_INGOT, null, 1);
        final ItemStack secondItemStack = this.itemStack(Material.GOLD_INGOT, null, 1);
        when(firstItemStack.getItemMeta().hasDisplayName()).thenReturn(true);
        when(secondItemStack.getItemMeta().hasDisplayName()).thenReturn(true);
        when(firstItemStack.getItemMeta().displayName()).thenReturn(Component.text("First"));
        when(secondItemStack.getItemMeta().displayName()).thenReturn(Component.text("Second"));

        assertFalse(UtilItemStack.isSimilar(firstItemStack, secondItemStack, strictItemMeta));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lorePresenceAndValuesFollowTheSelectedMode(final boolean strictItemMeta) {
        final ItemStack firstItemStack = this.itemStack(Material.GOLD_INGOT, null, 1);
        final ItemStack secondItemStack = this.itemStack(Material.GOLD_INGOT, null, 1);
        when(secondItemStack.getItemMeta().hasLore()).thenReturn(true);
        when(secondItemStack.getItemMeta().lore()).thenReturn(List.of(Component.text("Second")));

        assertEquals(!strictItemMeta, UtilItemStack.isSimilar(firstItemStack, secondItemStack, strictItemMeta));

        when(firstItemStack.getItemMeta().hasLore()).thenReturn(true);
        when(firstItemStack.getItemMeta().lore()).thenReturn(List.of(Component.text("First")));

        assertFalse(UtilItemStack.isSimilar(firstItemStack, secondItemStack, strictItemMeta));

        when(firstItemStack.getItemMeta().lore()).thenReturn(List.of(Component.text("Second")));

        assertTrue(UtilItemStack.isSimilar(firstItemStack, secondItemStack, strictItemMeta));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void enchantmentPresenceFollowsTheSelectedMode(final boolean strictItemMeta) {
        final ItemStack firstItemStack = this.itemStack(Material.GOLD_INGOT, null, 1);
        final ItemStack secondItemStack = this.itemStack(Material.GOLD_INGOT, null, 1);
        when(secondItemStack.getItemMeta().hasEnchants()).thenReturn(true);

        assertEquals(!strictItemMeta, UtilItemStack.isSimilar(firstItemStack, secondItemStack, strictItemMeta));
        assertEquals(!strictItemMeta, UtilItemStack.isSimilar(secondItemStack, firstItemStack, strictItemMeta));
    }

    @Test
    void containsDefaultsToOptionalMetadataAndSupportsStrictCounting() {
        final Player player = mock(Player.class);
        final PlayerInventory playerInventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(playerInventory);

        final ItemStack requestedItemStack = this.itemStack(Material.GOLD_INGOT, null, 1);
        final ItemStack plainItemStack = this.itemStack(Material.GOLD_INGOT, null, 2);
        final ItemStack namedItemStack = this.itemStack(Material.GOLD_INGOT, null, 3);
        when(namedItemStack.getItemMeta().hasDisplayName()).thenReturn(true);
        final ItemStack customItemStack = this.itemStack(Material.GOLD_INGOT, "reinforced_gold", 20);

        when(playerInventory.getContents()).thenReturn(new ItemStack[]{
                null,
                plainItemStack,
                namedItemStack,
                customItemStack
        });

        assertTrue(UtilItemStack.contains(player, requestedItemStack, 5));
        assertTrue(UtilItemStack.contains(player, requestedItemStack, 2, true));
        assertFalse(UtilItemStack.contains(player, requestedItemStack, 3, true));
        assertFalse(UtilItemStack.contains(player, requestedItemStack, 6));
    }

    @Test
    void strictRemoveDoesNotPartiallyRemoveAnInsufficientMatch() {
        final Player player = mock(Player.class);
        final PlayerInventory playerInventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(playerInventory);

        final ItemStack requestedItemStack = this.itemStack(Material.GOLD_INGOT, null, 1);
        final ItemStack plainItemStack = this.itemStack(Material.GOLD_INGOT, null, 2);
        final ItemStack namedItemStack = this.itemStack(Material.GOLD_INGOT, null, 3);
        when(namedItemStack.getItemMeta().hasDisplayName()).thenReturn(true);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{plainItemStack, namedItemStack});

        assertFalse(UtilItemStack.remove(player, requestedItemStack, 3, true));

        verify(playerInventory, never()).setItem(anyInt(), isNull());
        verify(plainItemStack, never()).setAmount(anyInt());
        verify(namedItemStack, never()).setAmount(anyInt());
    }

    @Test
    void defaultRemoveConsumesOptionalMatchesAcrossSlotsButNotCustomItems() {
        final Player player = mock(Player.class);
        final PlayerInventory playerInventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(playerInventory);

        final ItemStack requestedItemStack = this.itemStack(Material.GOLD_INGOT, null, 1);
        final ItemStack plainItemStack = this.itemStack(Material.GOLD_INGOT, null, 2);
        final ItemStack namedItemStack = this.itemStack(Material.GOLD_INGOT, null, 3);
        final ItemStack customItemStack = this.itemStack(Material.GOLD_INGOT, "reinforced_gold", 20);
        when(namedItemStack.getItemMeta().hasDisplayName()).thenReturn(true);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{customItemStack, plainItemStack, namedItemStack});

        assertTrue(UtilItemStack.remove(player, requestedItemStack, 4));

        verify(playerInventory).setItem(1, null);
        verify(namedItemStack).setAmount(1);
        verify(customItemStack, never()).setAmount(anyInt());
        verify(playerInventory, never()).setItem(0, null);
    }

    private ItemStack itemStack(final Material material, final String uniqueId, final int amount) {
        final ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.getType()).thenReturn(material);
        when(itemStack.getAmount()).thenReturn(amount);
        when(itemStack.hasItemMeta()).thenReturn(true);

        final ItemMeta itemMeta = mock(ItemMeta.class);
        when(itemStack.getItemMeta()).thenReturn(itemMeta);

        final PersistentDataContainer persistentDataContainer = mock(PersistentDataContainer.class);
        when(itemMeta.getPersistentDataContainer()).thenReturn(persistentDataContainer);
        when(persistentDataContainer.get(UtilItemStack.UNIQUE_ID_KEY, PersistentDataType.STRING)).thenReturn(uniqueId);

        return itemStack;
    }
}