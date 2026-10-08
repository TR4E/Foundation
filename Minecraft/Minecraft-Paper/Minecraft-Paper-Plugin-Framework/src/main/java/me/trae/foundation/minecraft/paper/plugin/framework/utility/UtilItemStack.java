package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import lombok.experimental.UtilityClass;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.Optional;

@UtilityClass
public class UtilItemStack {

    public static final NamespacedKey IDENTIFIER_KEY = new NamespacedKey("custom", "item_identifier");

    public static <P, C> void setPersistentDataType(final ItemMeta itemMeta, final NamespacedKey namespacedKey, final PersistentDataType<P, C> persistentDataType, final C value) {
        itemMeta.getPersistentDataContainer().set(namespacedKey, persistentDataType, value);
    }

    public static void removePersistentDataType(final ItemMeta itemMeta, final NamespacedKey namespacedKey) {
        itemMeta.getPersistentDataContainer().remove(namespacedKey);
    }

    public static <P, C> Optional<C> getPersistentData(final ItemStack itemStack, final NamespacedKey namespacedKey, final PersistentDataType<P, C> persistentDataType) {
        return Optional.ofNullable(itemStack).map(ItemStack::getItemMeta).map(itemMeta -> itemMeta.getPersistentDataContainer().get(namespacedKey, persistentDataType));
    }

    public static void insert(final Player player, final ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    public static boolean contains(final Player player, final ItemStack itemStack, final int amount) {
        int found = 0;

        for (final ItemStack content : player.getInventory().getContents()) {
            if (content == null || !isSimilar(itemStack, content)) {
                continue;
            }

            found += content.getAmount();

            if (found >= amount) {
                return true;
            }
        }

        return false;
    }

    public static boolean remove(final Player player, final ItemStack itemStack, final int amount) {
        if (!contains(player, itemStack, amount)) {
            return false;
        }

        int remaining = amount;

        final PlayerInventory playerInventory = player.getInventory();

        final ItemStack[] contents = playerInventory.getContents();

        for (int index = 0; index < contents.length && remaining > 0; index++) {
            final ItemStack content = contents[index];
            if (content == null || !isSimilar(itemStack, content)) {
                continue;
            }

            final int taken = Math.min(remaining, content.getAmount());

            remaining -= taken;

            if (taken == content.getAmount()) {
                playerInventory.setItem(index, null);
            } else {
                content.setAmount(content.getAmount() - taken);
            }
        }

        return true;
    }

    public static boolean isSimilar(final ItemStack itemStack, final ItemStack content) {
        if (itemStack == null || content == null) {
            return false;
        }

        if (itemStack.isEmpty() && content.isEmpty()) {
            return true;
        }

        if (itemStack.isEmpty() || content.isEmpty()) {
            return false;
        }

        final String itemStackIdentifier = getPersistentData(itemStack, IDENTIFIER_KEY, PersistentDataType.STRING).orElse(null);
        final String contentIdentifier = getPersistentData(content, IDENTIFIER_KEY, PersistentDataType.STRING).orElse(null);
        if (itemStackIdentifier != null && contentIdentifier != null) {
            return itemStackIdentifier.equals(contentIdentifier);
        }

        if (itemStack.getType() != content.getType()) {
            return false;
        }

        final ItemMeta itemStackMeta = itemStack.getItemMeta();
        final ItemMeta contentMeta = content.getItemMeta();

        if (itemStackMeta == null || contentMeta == null) {
            return itemStackMeta == contentMeta;
        }

        if (!Objects.equals(itemStackMeta.displayName(), contentMeta.displayName())) {
            return false;
        }

        if (!Objects.equals(itemStackMeta.lore(), contentMeta.lore())) {
            return false;
        }

        if (!itemStackMeta.getEnchants().equals(contentMeta.getEnchants())) {
            return false;
        }

        return true;
    }
}