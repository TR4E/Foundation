package me.trae.foundation.minecraft.paper.plugin.framework.utility;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.Optional;

@UtilityClass
public class UtilItemStack {

    public static final NamespacedKey UNIQUE_ID_KEY = new NamespacedKey("custom", "item_unique_id");

    public static <Primitive, Complex> Optional<Complex> getPersistentData(final ItemStack itemStack, final NamespacedKey namespacedKey, final PersistentDataType<Primitive, Complex> persistentDataType) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return Optional.empty();
        }

        return UtilPdc.getPersistentData(itemStack.getItemMeta().getPersistentDataContainer(), namespacedKey, persistentDataType);
    }

    public static Optional<String> getUniqueIdByItemStack(final ItemStack itemStack) {
        if (!itemStack.hasItemMeta()) {
            return Optional.empty();
        }

        return UtilPdc.getPersistentData(itemStack.getItemMeta().getPersistentDataContainer(), UNIQUE_ID_KEY, PersistentDataType.STRING);
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

        final String itemStackUniqueId = getPersistentData(itemStack, UNIQUE_ID_KEY, PersistentDataType.STRING).orElse(null);
        final String contentUniqueId = getPersistentData(content, UNIQUE_ID_KEY, PersistentDataType.STRING).orElse(null);
        if (itemStackUniqueId != null && contentUniqueId != null) {
            return itemStackUniqueId.equals(contentUniqueId);
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

    public static Optional<ItemStack> getItemInMainHand(final Entity entity) {
        return Optional.ofNullable(entity)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .map(LivingEntity::getEquipment)
                .map(EntityEquipment::getItemInMainHand)
                .filter(itemStack -> !itemStack.isEmpty());
    }

    public static ItemStack[] getArmourContents(final Entity entity) {
        return Optional.ofNullable(entity)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .map(LivingEntity::getEquipment)
                .map(EntityEquipment::getArmorContents)
                .orElse(null);
    }

    public static Component getDisplayName(final ItemStack itemStack, final boolean hoverable) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        final Component effectiveName = itemStack.effectiveName();

        return hoverable ? effectiveName.hoverEvent(itemStack.asHoverEvent()) : effectiveName;
    }
}